package main

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"crypto/ecdh"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"encoding/binary"
	"fmt"
	"testing"
)

func TestHandshakeRawX963AndSpki(t *testing.T) {
	curve := ecdh.P256()

	// 1. iOS-style raw 65-byte X9.63 peer key
	iosPriv, err := curve.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	var sim CryptoSession
	resp, err := sim.Handshake(iosPriv.PublicKey().Bytes())
	if err != nil {
		t.Fatalf("X9.63 handshake failed: %v", err)
	}
	if sim.IsActive() {
		t.Fatal("crypto must stay inactive until KEY_EXCHANGE_RESP is sent in plaintext")
	}
	sim.Activate()
	if len(resp) != 16+65 {
		t.Fatalf("expected 81 bytes for X9.63 response, got %d", len(resp))
	}
	salt := resp[:16]
	serverPub, err := curve.NewPublicKey(resp[16:])
	if err != nil {
		t.Fatal(err)
	}
	shared, err := iosPriv.ECDH(serverPub)
	if err != nil {
		t.Fatal(err)
	}
	derived := hkdfSHA256(shared, salt, []byte(kdfInfo), 32)
	sum := sha256.Sum256(derived)
	expectedPin := fmt.Sprintf("%06d", ((int(sum[0]&0x7F)<<16)|(int(sum[1])<<8)|int(sum[2]))%1000000)
	gotPin, _ := sim.Info()
	if gotPin != expectedPin {
		t.Fatalf("PIN mismatch: got %s want %s", gotPin, expectedPin)
	}

	aad := []byte("0123456789ABCDEF")
	plain := []byte("HELLO-BYD-SIM")
	enc, err := sim.Encrypt(plain, aad)
	if err != nil {
		t.Fatal(err)
	}
	blk, _ := aes.NewCipher(derived)
	gcm, _ := cipher.NewGCM(blk)
	dec, err := gcm.Open(nil, enc[:gcmNonceSize], enc[gcmNonceSize:], aad)
	if err != nil || !bytes.Equal(dec, plain) {
		t.Fatalf("AES-GCM mismatch: err=%v dec=%q", err, dec)
	}

	// Empty 0-byte control payload roundtrip (28B wire ciphertext)
	emptyEnc, err := sim.Encrypt(nil, aad)
	if err != nil || len(emptyEnc) != encOverhead {
		t.Fatalf("empty encrypt failed: len=%d err=%v", len(emptyEnc), err)
	}
	emptyDec, err := sim.Decrypt(emptyEnc, aad)
	if err != nil || len(emptyDec) != 0 {
		t.Fatalf("empty decrypt failed: len=%d err=%v", len(emptyDec), err)
	}

	// 2. Android-style X.509 SPKI peer key
	andPriv, err := curve.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	spkiBytes, err := x509.MarshalPKIXPublicKey(andPriv.PublicKey())
	if err != nil {
		t.Fatal(err)
	}
	sim.Reset()
	resp2, err := sim.Handshake(spkiBytes)
	if err != nil {
		t.Fatalf("SPKI handshake failed: %v", err)
	}
	if len(resp2) <= 16+65 {
		t.Fatalf("expected SPKI response > 81 bytes, got %d", len(resp2))
	}
}

func TestFrameReassemblerOutOfOrder(t *testing.T) {
	r := NewFrameReassembler()
	makeFrag := func(id uint32, idx, total uint16, body []byte) []byte {
		b := make([]byte, 8+len(body))
		binary.BigEndian.PutUint32(b[0:4], id)
		binary.BigEndian.PutUint16(b[4:6], idx)
		binary.BigEndian.PutUint16(b[6:8], total)
		copy(b[8:], body)
		return b
	}

	if out := r.Push(makeFrag(7, 2, 3, []byte("CCC"))); out != nil {
		t.Fatal("expected nil before all fragments arrive")
	}
	if out := r.Push(makeFrag(7, 0, 3, []byte("AAA"))); out != nil {
		t.Fatal("expected nil before all fragments arrive")
	}
	full := r.Push(makeFrag(7, 1, 3, []byte("BBB")))
	if string(full) != "AAABBBCCC" {
		t.Fatalf("unexpected reassembled frame: %q", full)
	}
}

func TestAntiReplayWindowRejectsDuplicatesAndOldPackets(t *testing.T) {
	var w AntiReplayWindow
	if w.Accept(0) {
		t.Fatal("seq 0 must be rejected")
	}
	if !w.Accept(1) || !w.Accept(3) || !w.Accept(2) {
		t.Fatal("in-window out-of-order packets should be accepted")
	}
	if w.Accept(2) || w.Accept(3) {
		t.Fatal("duplicate packets inside window must be rejected")
	}
	if !w.Accept(100) {
		t.Fatal("advancing window must succeed")
	}
	if w.Accept(10) {
		t.Fatal("packet older than 64-packet window must be rejected")
	}
}
