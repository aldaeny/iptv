package main

import (
	"bufio"
	"crypto/aes"
	"crypto/cipher"
	"crypto/ecdsa"
	"crypto/ecdh"
	"crypto/hmac"
	cryptorand "crypto/rand"
	"crypto/sha1"
	"crypto/sha256"
	"crypto/x509"
	"embed"
	"encoding/asn1"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"log"
	"math"
	"math/big"
	"math/rand"
	"net"
	"net/http"
	"os"
	"os/exec"
	"regexp"
	"runtime"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

//go:embed web/*
var embeddedWeb embed.FS

const (
	controlPort       = 47810
	mediaUDPPort      = 47811
	webUIPort         = 47812
	beaconUDPPort     = 47813
	magicBytes        = uint16(0x4259)
	protocolVersion   = byte(0x01)
	headerSize        = 16
	gcmNonceSize      = 12
	gcmTagSize        = 16
	encOverhead       = gcmNonceSize + gcmTagSize
	maxPacketBytes    = 65536
	kdfInfo           = "BYD-MIRROR-SESSION-KEY-V1"

	// Security: Key rotation interval (1 hour)
	keyRotationInterval = 3600 * time.Second

	opHelloSyn          byte = 0x01
	opHelloAck          byte = 0x02
	opKeyExchangeInit   byte = 0x03
	opKeyExchangeResp   byte = 0x04
	opCapabilitiesReq   byte = 0x05
	opCapabilitiesResp  byte = 0x06
	opSessionStart      byte = 0x07
	opSessionStop       byte = 0x08
	opOrientationChange byte = 0x09
	opDeviceIdentity    byte = 0x0A
	opVideoNalu         byte = 0x10
	opAudioAAC          byte = 0x11
	opKeyframeRequest   byte = 0x12
	opVideoNaluFrag     byte = 0x13
	opTouchEvent        byte = 0x14
	opHeartbeatPing     byte = 0x20
	opHeartbeatPong     byte = 0x21
	opQosTelemetry      byte = 0x22
)

// DEVICE_IDENTITY (0x0A) verification constants
const (
	identityVersion     = 1
	identityPrefix      = "BYD-WMP-IDENTITY-V1|"
	p256SpkiPrefixHex   = "3059301306072a8648ce3d020106082a8648ce3d030107034200"
)

var p256SpkiPrefix []byte

func init() {
	var err error
	p256SpkiPrefix, err = decodeHex(p256SpkiPrefixHex)
	if err != nil {
		panic("invalid P256_SPKI_PREFIX: " + err.Error())
	}
}

func decodeHex(s string) ([]byte, error) {
	if len(s)%2 != 0 {
		return nil, errors.New("odd length hex string")
	}
	b := make([]byte, len(s)/2)
	for i := 0; i < len(s); i += 2 {
		var val byte
		fmt.Sscanf(s[i:i+2], "%02x", &val)
		b[i/2] = val
	}
	return b, nil
}

// VerifiedSenderIdentity holds the verified sender's SPKI and fingerprint
type VerifiedSenderIdentity struct {
	PublicKeySpki []byte
	Fingerprint   string
}

// canonicalSpki converts raw X9.63 (65B) to SPKI; passes valid SPKI through
func canonicalSpki(pubBytes []byte) []byte {
	if len(pubBytes) == 65 && pubBytes[0] == 0x04 {
		return append(p256SpkiPrefix, pubBytes...)
	}
	if len(pubBytes) > len(p256SpkiPrefix) {
		if equalBytes(pubBytes[:len(p256SpkiPrefix)], p256SpkiPrefix) {
			return pubBytes
		}
	}
	return nil
}

func equalBytes(a, b []byte) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

func fingerprint(spki []byte) string {
	sum := sha256.Sum256(spki)
	const hex = "0123456789ABCDEF"
	var sb strings.Builder
	sb.Grow(len(sum) * 2)
	for _, b := range sum {
		sb.WriteByte(hex[b>>4])
		sb.WriteByte(hex[b&0x0F])
	}
	return sb.String()
}

func signingPayload(sessionFingerprint string) []byte {
	return []byte(identityPrefix + sessionFingerprint)
}

// verifyDeviceIdentity verifies a DEVICE_IDENTITY proof payload
func verifyDeviceIdentity(proof []byte, sessionFingerprint string) *VerifiedSenderIdentity {
	if len(proof) < 5 {
		return nil
	}
	buf := proof
	if buf[0] != identityVersion {
		return nil
	}
	buf = buf[1:]
	if len(buf) < 2 {
		return nil
	}
	pubLen := int(binary.BigEndian.Uint16(buf[:2]))
	buf = buf[2:]
	if pubLen <= 0 || pubLen > len(buf) {
		return nil
	}
	pubBytes := buf[:pubLen]
	buf = buf[pubLen:]
	if len(buf) < 2 {
		return nil
	}
	sigLen := int(binary.BigEndian.Uint16(buf[:2]))
	buf = buf[2:]
	if sigLen <= 0 || sigLen != len(buf) {
		return nil
	}
	sigBytes := buf[:sigLen]

	spki := canonicalSpki(pubBytes)
	if spki == nil {
		return nil
	}

	// Parse SPKI to get ECDSA public key
	pubKey, err := x509.ParsePKIXPublicKey(spki)
	if err != nil {
		return nil
	}
	ecdsaPub, ok := pubKey.(*ecdsa.PublicKey)
	if !ok {
		return nil
	}

	// Verify signature
	payload := signingPayload(sessionFingerprint)
	hash := sha256.Sum256(payload)
	var sigStruct struct {
		R, S *big.Int
	}
	if _, err := asn1.Unmarshal(sigBytes, &sigStruct); err != nil {
		return nil
	}
	if !ecdsa.Verify(ecdsaPub, hash[:], sigStruct.R, sigStruct.S) {
		return nil
	}

	return &VerifiedSenderIdentity{
		PublicKeySpki: spki,
		Fingerprint:   fingerprint(spki),
	}
}

type CryptoSession struct {
	mu          sync.RWMutex
	active      bool
	aead        cipher.AEAD
	fingerprint string
	pin         string
}

func (c *CryptoSession) Reset() {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.active = false
	c.aead = nil
	c.fingerprint = "----"
	c.pin = "000000"
}

func (c *CryptoSession) IsActive() bool {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.active
}

func (c *CryptoSession) Info() (string, string) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.pin, c.fingerprint
}

// Rekey performs key rotation with a new ephemeral key pair while keeping the same peer.
func (c *CryptoSession) Rekey(newSalt []byte) error {
	c.mu.Lock()
	defer c.mu.Unlock()

	curve := ecdh.P256()
	localPriv, err := curve.GenerateKey(rand.Reader)
	if err != nil {
		return err
	}

	var peerPub *ecdh.PublicKey
	// We need the peer's public key - in practice this would be stored during initial handshake
	// For now, we'll use the existing shared secret derivation
	// Note: Full rekey requires peer cooperation; this is a local re-derivation
	_ = localPriv

	// In a real implementation, this would require a new key exchange with the peer
	// For forward secrecy, we'd need to exchange new ephemeral keys
	log.Printf("Key rotation initiated (simulated - full rekey requires peer cooperation)")
	return nil
}

func (c *CryptoSession) IsSessionActive() bool {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.active
}

func (c *CryptoSession) GetFingerprint() string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.fingerprint
}

func (c *CryptoSession) GetSessionKeyForAudit() []byte {
	c.mu.RLock()
	defer c.mu.RUnlock()
	if c.aead == nil {
		return nil
	}
	// Return a hash of the session key for audit (never the raw key)
	return []byte(c.fingerprint)
}

func (c *CryptoSession) IsSessionActive() bool {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.active
}

func hkdfSHA256(ikm, salt, info []byte, length int) []byte {
	prkMac := hmac.New(sha256.New, salt)
	prkMac.Write(ikm)
	prk := prkMac.Sum(nil)

	okm := make([]byte, 0, length)
	var prev []byte
	for counter := byte(1); len(okm) < length; counter++ {
		mac := hmac.New(sha256.New, prk)
		mac.Write(prev)
		mac.Write(info)
		mac.Write([]byte{counter})
		prev = mac.Sum(nil)
		need := length - len(okm)
		if len(prev) < need {
			need = len(prev)
		}
		okm = append(okm, prev[:need]...)
	}
	return okm
}

// Handshake auto-detects raw X9.63 (65B from iOS) vs X.509 SPKI (91B from Android).
func (c *CryptoSession) Handshake(peerPubBytes []byte) ([]byte, error) {
	curve := ecdh.P256()
	localPriv, err := curve.GenerateKey(cryptorand.Reader)
	if err != nil {
		return nil, err
	}

	var peerPub *ecdh.PublicKey
	isRawX963 := len(peerPubBytes) == 65 && peerPubBytes[0] == 0x04
	if isRawX963 {
		peerPub, err = curve.NewPublicKey(peerPubBytes)
		if err != nil {
			return nil, err
		}
	} else {
		parsed, err := x509.ParsePKIXPublicKey(peerPubBytes)
		if err != nil {
			return nil, err
		}
		ecdsaPub, ok := parsed.(interface {
			ECDH() (*ecdh.PublicKey, error)
		})
		if !ok {
			return nil, errors.New("unsupported peer public key type")
		}
		peerPub, err = ecdsaPub.ECDH()
		if err != nil {
			return nil, err
		}
	}

	shared, err := localPriv.ECDH(peerPub)
	if err != nil {
		return nil, err
	}

	salt := make([]byte, 16)
	if _, err := cryptorand.Read(salt); err != nil {
		return nil, err
	}
	derived := hkdfSHA256(shared, salt, []byte(kdfInfo), 32)
	block, err := aes.NewCipher(derived)
	if err != nil {
		return nil, err
	}
	aead, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}

	sum := sha256.Sum256(derived)
	fp := fmt.Sprintf("%02X-%02X-%02X-%02X", sum[0], sum[1], sum[2], sum[3])
	pinInt := (int(sum[0]&0x7F) << 16) | (int(sum[1]) << 8) | int(sum[2])
	pin := fmt.Sprintf("%06d", pinInt%1000000)

	var localPubOut []byte
	if isRawX963 {
		localPubOut = localPriv.PublicKey().Bytes()
	} else {
		localPubOut, err = x509.MarshalPKIXPublicKey(localPriv.PublicKey())
		if err != nil {
			return nil, err
		}
	}

	c.mu.Lock()
	c.aead = aead
	c.fingerprint = fp
	c.pin = pin
	c.active = false // Activated right after KEY_EXCHANGE_RESP is sent in plaintext
	c.mu.Unlock()

	return append(salt, localPubOut...), nil
}

func (c *CryptoSession) Activate() {
	c.mu.Lock()
	c.active = true
	c.mu.Unlock()
}

func (c *CryptoSession) Encrypt(plain, aad []byte) ([]byte, error) {
	c.mu.RLock()
	aead := c.aead
	c.mu.RUnlock()
	if aead == nil {
		return nil, errors.New("session key not ready")
	}
	nonce := make([]byte, gcmNonceSize)
	if _, err := cryptorand.Read(nonce); err != nil {
		return nil, err
	}
	ciphertext := aead.Seal(nil, nonce, plain, aad)
	return append(nonce, ciphertext...), nil
}

func (c *CryptoSession) Decrypt(wire, aad []byte) ([]byte, error) {
	c.mu.RLock()
	aead := c.aead
	c.mu.RUnlock()
	if aead == nil {
		return nil, errors.New("session key not ready")
	}
	if len(wire) < encOverhead {
		return nil, errors.New("ciphertext too short")
	}
	nonce := wire[:gcmNonceSize]
	body := wire[gcmNonceSize:]
	return aead.Open(nil, nonce, body, aad)
}

type FragBucket struct {
	total     int
	parts     map[int][]byte
	createdAt time.Time
}

type FrameReassembler struct {
	mu      sync.Mutex
	buckets map[uint32]*FragBucket
}

func NewFrameReassembler() *FrameReassembler {
	return &FrameReassembler{buckets: make(map[uint32]*FragBucket)}
}

func (r *FrameReassembler) Clear() {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.buckets = make(map[uint32]*FragBucket)
}

func (r *FrameReassembler) Push(payload []byte) []byte {
	if len(payload) < 8 {
		return nil
	}
	frameID := binary.BigEndian.Uint32(payload[0:4])
	fragIdx := int(binary.BigEndian.Uint16(payload[4:6]))
	fragTotal := int(binary.BigEndian.Uint16(payload[6:8]))
	if fragTotal <= 0 || fragTotal > 256 || fragIdx < 0 || fragIdx >= fragTotal {
		return nil
	}

	r.mu.Lock()
	defer r.mu.Unlock()
	now := time.Now()
	for id, b := range r.buckets {
		if now.Sub(b.createdAt) > 500*time.Millisecond {
			delete(r.buckets, id)
		}
	}
	bucket, ok := r.buckets[frameID]
	if !ok {
		if len(r.buckets) >= 16 {
			var oldestID uint32
			var oldestTime time.Time
			first := true
			for id, b := range r.buckets {
				if first || b.createdAt.Before(oldestTime) {
					oldestID = id
					oldestTime = b.createdAt
					first = false
				}
			}
			delete(r.buckets, oldestID)
		}
		bucket = &FragBucket{total: fragTotal, parts: make(map[int][]byte, fragTotal), createdAt: now}
		r.buckets[frameID] = bucket
	}
	chunk := make([]byte, len(payload)-8)
	copy(chunk, payload[8:])
	bucket.parts[fragIdx] = chunk
	if len(bucket.parts) < bucket.total {
		return nil
	}
	var out []byte
	for i := 0; i < bucket.total; i++ {
		p, exists := bucket.parts[i]
		if !exists {
			return nil
		}
		out = append(out, p...)
	}
	delete(r.buckets, frameID)
	return out
}

type wsClient struct {
	conn net.Conn
	mu   sync.Mutex
}

func (w *wsClient) sendFrame(opcode byte, data []byte) error {
	w.mu.Lock()
	defer w.mu.Unlock()
	var hdr []byte
	l := len(data)
	switch {
	case l < 126:
		hdr = []byte{0x80 | opcode, byte(l)}
	case l <= 65535:
		hdr = make([]byte, 4)
		hdr[0] = 0x80 | opcode
		hdr[1] = 126
		binary.BigEndian.PutUint16(hdr[2:], uint16(l))
	default:
		hdr = make([]byte, 10)
		hdr[0] = 0x80 | opcode
		hdr[1] = 127
		binary.BigEndian.PutUint64(hdr[2:], uint64(l))
	}
	if _, err := w.conn.Write(hdr); err != nil {
		return err
	}
	_, err := w.conn.Write(data)
	return err
}

type UIState struct {
	State       string   `json:"state"`
	PhoneName   string   `json:"phoneName,omitempty"`
	Pin         string   `json:"pin,omitempty"`
	Fingerprint string   `json:"fingerprint,omitempty"`
	Mime        string   `json:"mime,omitempty"`
	Codec       string   `json:"codec,omitempty"`
	IsLandscape bool     `json:"isLandscape"`
	FPS         int      `json:"fps"`
	IPs         []string `json:"ips"`
}

// AuditLogger provides security event logging for the simulator
type AuditLogger struct {
	file       *os.File
	fileSize   int64
	maxSize    int64
	logDir     string
	mu         sync.Mutex
}

// AuditEvent represents a security audit event
type AuditEvent struct {
	Timestamp          string `json:"timestamp"`
	EventType          string `json:"eventType"`
	SessionFingerprint string `json:"sessionFingerprint,omitempty"`
	PeerAddress        string `json:"peerAddress,omitempty"`
	Details            string `json:"details"`
	Severity           string `json:"severity"`
}

const (
	maxAuditFileSize = 10 * 1024 * 1024 // 10 MB
	maxAuditFiles    = 7
)

func NewAuditLogger() *AuditLogger {
	logDir := "audit_logs"
	os.MkdirAll(logDir, 0755)

	// Clean old audit files
	files, _ := os.ReadDir("audit_logs")
	var auditFiles []string
	for _, f := range files {
		if strings.HasPrefix(f.Name(), "audit_") && strings.HasSuffix(f.Name(), ".log") {
			auditFiles = append(auditFiles, filepath.Join("audit_logs", f.Name()))
		}
	}
	sort.Strings(auditFiles)
	for len(auditFiles) > 7 {
		os.Remove(auditFiles[0])
		auditFiles = auditFiles[1:]
	}

	fileName := fmt.Sprintf("audit_%s.log", time.Now().Format("20060102_150405"))
	filePath := filepath.Join("audit_logs", fileName)
	file, _ := os.Create(filePath)

	return &AuditLogger{
		file:    file,
		maxSize: 10 * 1024 * 1024,
		logDir:  "audit_logs",
	}
}

func (a *AuditLogger) log(eventType, severity, fingerprint, peer, details string) {
	a.mu.Lock()
	defer a.mu.Unlock()

	event := AuditEvent{
		Timestamp:          time.Now().UTC().Format(time.RFC3339Nano),
		EventType:          eventType,
		SessionFingerprint: fingerprint,
		PeerAddress:        peer,
		Details:            details,
		Severity:           severity,
	}

	data, _ := json.Marshal(event)
	data = append(data, '\n')
	
	// Write to file
	if a.file != nil {
		a.file.Write(data)
		a.fileSize += int64(len(data))
		
		// Rotate if needed
		if a.fileSize > 10*1024*1024 {
			a.rotate()
		}
	}
	
	// Also log to stdout
	log.Printf("AUDIT: [%s] [%s] fp=%s peer=%s %s", eventType, severity, fingerprint, peer, details)
}

func (a *AuditLogger) rotate() {
	a.file.Close()
	fileName := fmt.Sprintf("audit_%s.log", time.Now().Format("20060102_150405"))
	filePath := filepath.Join("audit_logs", fileName)
	newFile, _ := os.Create(filePath)
	a.file = newFile
	a.fileSize = 0
	
	// Clean old files
	files, _ := os.ReadDir("audit_logs")
	var auditFiles []string
	for _, f := range files {
		if strings.HasPrefix(f.Name(), "audit_") && strings.HasSuffix(f.Name(), ".log") {
			auditFiles = append(auditFiles, filepath.Join("audit_logs", f.Name()))
		}
	}
	sort.Strings(auditFiles)
	for len(auditFiles) > 7 {
		os.Remove(auditFiles[0])
		auditFiles = auditFiles[1:]
	}
}

func (a *AuditLogger) close() {
	if a.file != nil {
		a.file.Close()
	}
}

// Audit event types
const (
	AuditHandshakeStart       = "HANDSHAKE_START"
	AuditHandshakeSuccess     = "HANDSHAKE_SUCCESS"
	AuditHandshakeFailure     = "HANDSHAKE_FAILURE"
	AuditKeyExchangeInit      = "KEY_EXCHANGE_INIT"
	AuditKeyExchangeSuccess   = "KEY_EXCHANGE_SUCCESS"
	AuditKeyExchangeFailure   = "KEY_EXCHANGE_FAILURE"
	AuditKeyRotation          = "KEY_ROTATION"
	AuditSessionStart         = "SESSION_START"
	AuditSessionStop          = "SESSION_STOP"
	AuditSessionStopUnexpected = "SESSION_STOP_UNEXPECTED"
	AuditDecryptionFailure    = "DECRYPTION_FAILURE"
	AuditReplayAttack         = "REPLAY_ATTACK_DETECTED"
	AuditInvalidSequence      = "INVALID_SEQUENCE"
	AuditKeyRotationSuccess   = "KEY_ROTATION_SUCCESS"
	AuditPairingVerified      = "PAIRING_VERIFIED"
	AuditPairingRejected      = "PAIRING_REJECTED"
	AuditReconnectAttempt     = "RECONNECT_ATTEMPT"
	AuditReconnectSuccess     = "RECONNECT_SUCCESS"
	AuditReconnectFailure     = "RECONNECT_FAILURE"
	AuditDecryptionFailure    = "DECRYPTION_FAILURE"
	AuditReplayAttack         = "REPLAY_ATTACK"
	AuditInvalidSequence      = "INVALID_SEQUENCE"
	AuditProtocolViolation    = "PROTOCOL_VIOLATION"
)

type AntiReplayWindow struct {
	mu      sync.Mutex
	highest uint64
	bitmap  uint64
}

func (a *AntiReplayWindow) Reset() {
	a.mu.Lock()
	a.highest = 0
	a.bitmap = 0
	a.mu.Unlock()
}

func (a *AntiReplayWindow) Accept(seq int) bool {
	if seq <= 0 {
		return false
	}
	u := uint64(seq)
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.highest == 0 {
		a.highest = u
		a.bitmap = 1
		return true
	}
	if u > a.highest {
		shift := u - a.highest
		if shift >= 64 {
			a.bitmap = 1
		} else {
			a.bitmap = (a.bitmap << shift) | 1
		}
		a.highest = u
		return true
	}
	delta := a.highest - u
	if delta >= 64 {
		return false
	}
	mask := uint64(1) << delta
	if (a.bitmap & mask) != 0 {
		return false
	}
	a.bitmap |= mask
	return true
}

type SimulatorServer struct {
	crypto              CryptoSession
	reasm               *FrameReassembler
	udpReplay           AntiReplayWindow
	clientsMu           sync.Mutex
	clients             map[*wsClient]struct{}
	stateMu             sync.RWMutex
	uiState             UIState
	tcpOutMu            sync.Mutex
	tcpOut              net.Conn
	outSeq              atomic.Int32
	framesWin           atomic.Int32
	droppedWin          atomic.Int32
	lastUdpSeq          atomic.Int32
	failedHandshake     atomic.Int32
	trustStoreMu        sync.RWMutex
	trustedSenders      map[string]bool // fingerprint -> trusted

	// Exponential backoff + IP cooldown for failed handshakes
	failedHandshakesPerIP   sync.Map // map[string]*atomic.Int32
	ipCooldownUntil         sync.Map // map[string]int64 (unix ms)
	maxBackoffMs            int64
	baseBackoffMs           int64
	maxFailedBeforeCooldown int32

	// Security: Audit logging
	auditLog *AuditLogger
	// Security: Key rotation
	keyRotationTimer *time.Ticker
}

func NewSimulatorServer() *SimulatorServer {
	return &SimulatorServer{
		reasm:                   NewFrameReassembler(),
		clients:                 make(map[*wsClient]struct{}),
		trustedSenders:          make(map[string]bool),
		maxBackoffMs:            60_000,
		baseBackoffMs:           1_000,
		maxFailedBeforeCooldown: 3,
		uiState: UIState{
			State:       "waiting",
			IsLandscape: true,
			IPs:         localIPv4s(),
		},
		auditLog: NewAuditLogger(),
	}
}

func isVirtualAdapter(name string) bool {
	low := strings.ToLower(name)
	for _, kw := range []string{"vethernet", "hyper-v", "wsl", "virtualbox", "vmware", "docker", "vbox", "tailscale", "zerotier", "hamachi", "tun", "tap"} {
		if strings.Contains(low, kw) {
			return true
		}
	}
	return false
}

func localIPv4s() []string {
	var preferred, fallback []string
	ifaces, err := net.Interfaces()
	if err != nil {
		return preferred
	}
	for _, iface := range ifaces {
		if (iface.Flags&net.FlagUp) == 0 || (iface.Flags&net.FlagLoopback) != 0 {
			continue
		}
		if isVirtualAdapter(iface.Name) {
			continue
		}
		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, a := range addrs {
			if ipnet, ok := a.(*net.IPNet); ok {
				if v4 := ipnet.IP.To4(); v4 != nil && !v4.IsLinkLocalUnicast() {
					s := v4.String()
					if strings.HasPrefix(s, "192.168.") || strings.HasPrefix(s, "10.") {
						preferred = append(preferred, s)
					} else {
						fallback = append(fallback, s)
					}
				}
			}
		}
	}
	return append(preferred, fallback...)
}

func (s *SimulatorServer) broadcastJSON(st UIState) {
	s.stateMu.Lock()
	st.IPs = localIPv4s()
	s.uiState = st
	s.stateMu.Unlock()

	b, _ := json.Marshal(st)
	s.clientsMu.Lock()
	for c := range s.clients {
		if err := c.sendFrame(0x1, b); err != nil {
			c.conn.Close()
			delete(s.clients, c)
		}
	}
	s.clientsMu.Unlock()
}

func (s *SimulatorServer) broadcastMedia(kind byte, tsUs uint32, payload []byte) {
	pkt := make([]byte, 9+len(payload))
	pkt[0] = kind
	binary.BigEndian.PutUint64(pkt[1:9], uint64(tsUs))
	copy(pkt[9:], payload)

	s.clientsMu.Lock()
	for c := range s.clients {
		if err := c.sendFrame(0x2, pkt); err != nil {
			c.conn.Close()
			delete(s.clients, c)
		}
	}
	s.clientsMu.Unlock()
}

func parseHeader(hdr []byte) (byte, int, uint32, int, bool) {
	if len(hdr) < headerSize {
		return 0, 0, 0, 0, false
	}
	if binary.BigEndian.Uint16(hdr[0:2]) != magicBytes || hdr[2] != protocolVersion {
		return 0, 0, 0, 0, false
	}
	opcode := hdr[3]
	seq := int(int32(binary.BigEndian.Uint32(hdr[4:8])))
	ts := binary.BigEndian.Uint32(hdr[8:12])
	length := int(binary.BigEndian.Uint32(hdr[12:16]))
	if seq <= 0 || length < 0 || length > maxPacketBytes {
		return 0, 0, 0, 0, false
	}
	return opcode, seq, ts, length, true
}

func (s *SimulatorServer) sendControl(conn net.Conn, opcode byte, plain []byte) {
	if conn == nil {
		return
	}
	s.tcpOutMu.Lock()
	defer s.tcpOutMu.Unlock()

	seq := s.outSeq.Add(1)
	ts := uint32(time.Now().UnixMicro())
	enc := s.crypto.IsActive()
	wireLen := len(plain)
	if enc {
		wireLen += encOverhead
	}
	hdr := make([]byte, headerSize)
	binary.BigEndian.PutUint16(hdr[0:2], magicBytes)
	hdr[2] = protocolVersion
	hdr[3] = opcode
	binary.BigEndian.PutUint32(hdr[4:8], uint32(seq))
	binary.BigEndian.PutUint32(hdr[8:12], ts)
	binary.BigEndian.PutUint32(hdr[12:16], uint32(wireLen))

	body := plain
	if enc {
		var err error
		body, err = s.crypto.Encrypt(plain, hdr)
		if err != nil {
			return
		}
	}
	_, _ = conn.Write(append(hdr, body...))
}

func (s *SimulatorServer) handleIncomingMedia(opcode byte, ts uint32, payload []byte) {
	switch opcode {
	case opVideoNalu:
		s.framesWin.Add(1)
		s.broadcastMedia(1, ts, payload)
	case opVideoNaluFrag:
		if full := s.reasm.Push(payload); full != nil {
			s.framesWin.Add(1)
			s.broadcastMedia(1, ts, full)
		}
	case opAudioAAC:
		s.broadcastMedia(2, ts, payload)
	}
}

func (s *SimulatorServer) startUDP() {
	pc, err := net.ListenPacket("udp4", fmt.Sprintf(":%d", mediaUDPPort))
	if err != nil {
		log.Printf("UDP bind warning: %v", err)
		return
	}
	go func() {
		defer pc.Close()
		buf := make([]byte, maxPacketBytes)
		for {
			n, _, err := pc.ReadFrom(buf)
			if err != nil {
				return
			}
			if n < headerSize || !s.crypto.IsActive() {
				continue
			}
			hdr := buf[:headerSize]
			opcode, seq, ts, length, ok := parseHeader(hdr)
			if !ok || n < headerSize+length {
				continue
			}
			wire := buf[headerSize : headerSize+length]
			payload, err := s.crypto.Decrypt(wire, hdr)
			if err != nil {
				continue
			}
			if !s.udpReplay.Accept(seq) {
				continue
			}
			prev := int(s.lastUdpSeq.Load())
			if prev != 0 && seq > prev+1 {
				s.droppedWin.Add(int32(seq - prev - 1))
				s.tcpOutMu.Lock()
				tc := s.tcpOut
				s.tcpOutMu.Unlock()
				if tc != nil && s.crypto.IsActive() {
					s.sendControl(tc, opKeyframeRequest, nil)
				}
			}
			if seq > prev {
				s.lastUdpSeq.Store(int32(seq))
			}
			s.handleIncomingMedia(opcode, ts, payload)
		}
	}()
}

func (s *SimulatorServer) handleTCPClient(conn net.Conn) {
	if tc, ok := conn.(*net.TCPConn); ok {
		_ = tc.SetNoDelay(true)
	}
	if fails := s.failedHandshake.Load(); fails >= 3 {
		backoff := time.Duration(min(int(fails-2), 4)) * time.Second
		time.Sleep(backoff)
	}
	s.tcpOutMu.Lock()
	s.crypto.Reset()
	s.reasm.Clear()
	s.udpReplay.Reset()
	s.lastUdpSeq.Store(0)
	s.outSeq.Store(0)
	s.tcpOut = conn
	s.tcpOutMu.Unlock()
	_ = conn.SetReadDeadline(time.Now().Add(10 * time.Second))
	handshakeCompleted := false

	// Exponential backoff + IP cooldown
	remoteIP := conn.RemoteAddr().(*net.TCPAddr).IP.String()
	nowMs := time.Now().UnixMilli()

	// IP cooldown check
	if cooldownVal, ok := s.ipCooldownUntil.Load(remoteIP); ok {
		cooldownUntil := cooldownVal.(int64)
		if nowMs < cooldownUntil {
			waitMs := cooldownUntil - nowMs
			log.Printf("Connection from %s rejected — IP cooldown active (%dms remaining)", remoteIP, waitMs)
			_ = conn.Close()
			return
		}
	}

	// Exponential backoff with jitter based on per-IP failure count
	ipFailuresVal, _ := s.failedHandshakesPerIP.LoadOrStore(remoteIP, &atomic.Int32{})
	ipFailures := ipFailuresVal.(*atomic.Int32).Load()
	if ipFailures >= s.maxFailedBeforeCooldown {
		backoffExp := int(ipFailures - s.maxFailedBeforeCooldown + 1)
		backoffMs := s.baseBackoffMs * int64(1<<uint(backoffExp-1))
		if backoffMs > s.maxBackoffMs {
			backoffMs = s.maxBackoffMs
		}
		// Add jitter: ±30%
		jitter := int64(float64(backoffMs) * 0.3 * (rand.Float64()*2 - 1))
		totalDelay := backoffMs + jitter
		if totalDelay < 0 {
			totalDelay = 0
		}
		log.Printf("Connection from %s delayed %dms (failures=%d)", remoteIP, totalDelay, ipFailures)
		time.Sleep(time.Duration(totalDelay) * time.Millisecond)
	}

	phoneName := "BYD Phone"
	mime := "video/avc"
	isLandscape := true
	lastSeq := 0
	dimRe := regexp.MustCompile(`\d{3,4}x\d{3,4}`)

	// DEVICE_IDENTITY tracking
	pendingIdentityTrusted := false
	sessionApproved := false

	stopQos := make(chan struct{})
	go func() {
		ticker := time.NewTicker(1 * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-stopQos:
				return
			case <-ticker.C:
				fps := int(s.framesWin.Swap(0))
				dropped := int(s.droppedWin.Swap(0))
				s.stateMu.RLock()
				cur := s.uiState
				s.stateMu.RUnlock()
				if cur.State == "streaming" && s.crypto.IsActive() {
					cur.FPS = fps
					s.broadcastJSON(cur)
					if fps == 0 {
						s.sendControl(conn, opKeyframeRequest, nil)
					}
					tel := make([]byte, 8)
					binary.BigEndian.PutUint32(tel[0:4], uint32(fps))
					binary.BigEndian.PutUint32(tel[4:8], uint32(dropped))
					s.sendControl(conn, opQosTelemetry, tel)
				}
			}
		}
	}()

	hdr := make([]byte, headerSize)
	for {
		if _, err := io.ReadFull(conn, hdr); err != nil {
			break
		}
		opcode, seq, ts, length, ok := parseHeader(hdr)
		if !ok || seq <= lastSeq {
			break
		}
		lastSeq = seq
		wire := make([]byte, length)
		if _, err := io.ReadFull(conn, wire); err != nil {
			break
		}
		payload := wire
		if s.crypto.IsActive() {
			var err error
			payload, err = s.crypto.Decrypt(wire, hdr)
			if err != nil {
				break
			}
		}

		if !s.crypto.IsActive() && opcode != opHelloSyn && opcode != opKeyExchangeInit {
			break
		}
		if s.crypto.IsActive() && (opcode == opHelloSyn || opcode == opKeyExchangeInit) {
			break
		}
		switch opcode {
		case opHelloSyn:
			if len(payload) > 0 {
				phoneName = string(payload)
			}
			s.sendControl(conn, opHelloAck, []byte("BYD-DILINK-5.0|15.6"))
		case opKeyExchangeInit:
			resp, err := s.crypto.Handshake(payload)
			if err != nil {
				log.Printf("Key exchange failed: %v", err)
				goto done
			}
			s.sendControl(conn, opKeyExchangeResp, resp)
			s.crypto.Activate()
			pin, fp := s.crypto.Info()
			s.broadcastJSON(UIState{
				State:       "pairing",
				PhoneName:   phoneName,
				Pin:         pin,
				Fingerprint: fp,
				IsLandscape: isLandscape,
			})
		case opDeviceIdentity:
			if !s.crypto.IsActive() {
				log.Printf("DEVICE_IDENTITY received before encryption active")
				goto done
			}
			verified := verifyDeviceIdentity(payload, s.crypto.fingerprint)
			pin, fp := s.crypto.Info()
			if verified != nil {
				s.trustStoreMu.RLock()
				pendingIdentityTrusted = s.trustedSenders[verified.Fingerprint]
				s.trustStoreMu.RUnlock()
				sessionApproved = pendingIdentityTrusted
				state := "pairing"
				if pendingIdentityTrusted {
					state = "streaming"
				}
				s.broadcastJSON(UIState{
					State:       state,
					PhoneName:   phoneName,
					Pin:         pin,
					Fingerprint: fp,
					Mime:        mime,
					IsLandscape: isLandscape,
					FPS:         60,
				})
				log.Printf("DEVICE_IDENTITY verified: fingerprint=%s trusted=%v", verified.Fingerprint, pendingIdentityTrusted)
			} else {
				pendingIdentityTrusted = false
				sessionApproved = false
				s.broadcastJSON(UIState{
					State:       "pairing",
					PhoneName:   phoneName,
					Pin:         pin,
					Fingerprint: fp,
					IsLandscape: isLandscape,
				})
				log.Printf("DEVICE_IDENTITY verification failed")
			}
		case opCapabilitiesReq:
			_ = dimRe.FindString(string(payload))
			s.sendControl(conn, opCapabilitiesResp, []byte("1920x1080@60|H264"))
			_ = conn.SetReadDeadline(time.Now().Add(60 * time.Second))
		case opSessionStart:
			handshakeCompleted = true
			s.failedHandshake.Store(0)
			_ = conn.SetReadDeadline(time.Time{})
			if len(payload) > 0 {
				mime = string(payload)
			}
			pin, fp := s.crypto.Info()
			if sessionApproved {
				s.broadcastJSON(UIState{
					State:       "streaming",
					PhoneName:   phoneName,
					Fingerprint: fp,
					Mime:        mime,
					IsLandscape: isLandscape,
					FPS:         60,
				})
				s.sendControl(conn, opKeyframeRequest, nil)
			} else {
				s.broadcastJSON(UIState{
					State:       "pairing",
					PhoneName:   phoneName,
					Pin:         pin,
					Fingerprint: fp,
					IsLandscape: isLandscape,
				})
			}
		case opOrientationChange:
			isLandscape = len(payload) > 0 && payload[0] == 0x01
			_, fp := s.crypto.Info()
			s.broadcastJSON(UIState{
				State:       "streaming",
				PhoneName:   phoneName,
				Fingerprint: fp,
				Mime:        mime,
				IsLandscape: isLandscape,
				FPS:         60,
			})
		case opVideoNalu, opVideoNaluFrag, opAudioAAC:
			if !s.crypto.IsActive() {
				goto done
			}
			s.handleIncomingMedia(opcode, ts, payload)
		case opHeartbeatPing:
			s.sendControl(conn, opHeartbeatPong, payload)
		case opSessionStop:
			goto done
		}
	}

done:
	if !handshakeCompleted {
		s.failedHandshake.Add(1)
		// Record per-IP failure and set cooldown if threshold exceeded
		ipFailsVal, _ := s.failedHandshakesPerIP.LoadOrStore(remoteIP, &atomic.Int32{})
		ipFails := ipFailsVal.(*atomic.Int32)
		newFails := ipFails.Add(1)
		if newFails >= s.maxFailedBeforeCooldown {
			backoffExp := int(newFails - s.maxFailedBeforeCooldown + 1)
			backoffMs := s.baseBackoffMs * int64(1<<uint(backoffExp-1))
			if backoffMs > s.maxBackoffMs*10 {
				backoffMs = s.maxBackoffMs * 10 // up to 10 min cooldown
			}
			cooldownUntil := time.Now().UnixMilli() + backoffMs
			s.ipCooldownUntil.Store(remoteIP, cooldownUntil)
			log.Printf("IP %s placed in cooldown for %dms (failures=%d)", remoteIP, backoffMs, newFails)
		}
	}
	close(stopQos)
	s.tcpOutMu.Lock()
	if s.tcpOut == conn {
		s.tcpOut = nil
		s.crypto.Reset()
		s.broadcastJSON(UIState{State: "waiting", IsLandscape: true})
	}
	s.tcpOutMu.Unlock()
	_ = conn.Close()
}

func (s *SimulatorServer) startTCP() {
	ln, err := net.Listen("tcp4", fmt.Sprintf(":%d", controlPort))
	if err != nil {
		log.Fatalf("TCP listen failed on %d: %v", controlPort, err)
	}
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			s.tcpOutMu.Lock()
			prev := s.tcpOut
			s.tcpOutMu.Unlock()
			if prev != nil {
				_ = prev.Close()
			}
			go s.handleTCPClient(c)
		}
	}()
}

// Lightweight zero-dependency mDNS responder so phones auto-discover the simulator.
func startMDNSResponder() {
	addr, err := net.ResolveUDPAddr("udp4", "224.0.0.251:5353")
	if err != nil {
		return
	}
	conn, err := net.ListenMulticastUDP("udp4", nil, addr)
	if err != nil {
		return
	}
	go func() {
		defer conn.Close()
		buf := make([]byte, 2048)
		for {
			n, _, err := conn.ReadFromUDP(buf)
			if err != nil {
				return
			}
			if n > 12 && strings.Contains(string(buf[:n]), "_byd-mirror") {
				ips := localIPv4s()
				if len(ips) == 0 {
					continue
				}
				ip4 := net.ParseIP(ips[0]).To4()
				if ip4 == nil {
					continue
				}
				pkt := buildMDNSAnnouncement(ip4)
				_, _ = conn.WriteToUDP(pkt, addr)
			}
		}
	}()
}

func encodeDNSName(parts ...string) []byte {
	var out []byte
	for _, p := range parts {
		out = append(out, byte(len(p)))
		out = append(out, []byte(p)...)
	}
	return append(out, 0)
}

func buildMDNSAnnouncement(ip4 net.IP) []byte {
	var pkt []byte
	// Header: ID=0, Flags=0x8400 (Response+Authoritative), QD=0, AN=4, NS=0, AR=0
	pkt = append(pkt, 0, 0, 0x84, 0x00, 0, 0, 0, 4, 0, 0, 0, 0)

	svcName := encodeDNSName("_byd-mirror", "_tcp", "local")
	instName := encodeDNSName("BYD-DiLink-Simulator", "_byd-mirror", "_tcp", "local")
	hostName := encodeDNSName("byd-sim", "local")

	// 1. PTR -> instName
	pkt = append(pkt, svcName...)
	pkt = append(pkt, 0, 12, 0, 1, 0, 0, 0, 120)
	pkt = binary.BigEndian.AppendUint16(pkt, uint16(len(instName)))
	pkt = append(pkt, instName...)

	// 2. SRV -> port 47810 + hostName
	srvData := make([]byte, 6+len(hostName))
	binary.BigEndian.PutUint16(srvData[4:6], controlPort)
	copy(srvData[6:], hostName)
	pkt = append(pkt, instName...)
	pkt = append(pkt, 0, 33, 0x80, 1, 0, 0, 0, 120)
	pkt = binary.BigEndian.AppendUint16(pkt, uint16(len(srvData)))
	pkt = append(pkt, srvData...)

	// 3. TXT -> model=BYD DiLink 5.0
	model := "model=BYD DiLink 5.0"
	txt := append([]byte{byte(len(model))}, model...)
	pkt = append(pkt, instName...)
	pkt = append(pkt, 0, 16, 0x80, 1, 0, 0, 0, 120)
	pkt = binary.BigEndian.AppendUint16(pkt, uint16(len(txt)))
	pkt = append(pkt, txt...)

	// 4. A -> ip4
	pkt = append(pkt, hostName...)
	pkt = append(pkt, 0, 1, 0x80, 1, 0, 0, 0, 120, 0, 4)
	pkt = append(pkt, ip4...)
	return pkt
}

func localBrowser(r *http.Request) bool {
	host := r.Host
	return (host == fmt.Sprintf("127.0.0.1:%d", webUIPort) || host == fmt.Sprintf("localhost:%d", webUIPort)) && r.Header.Get("Origin") == "http://"+host
}

func (s *SimulatorServer) handleWS(w http.ResponseWriter, r *http.Request) {
	if !localBrowser(r) {
		http.Error(w, "same-origin local UI required", http.StatusForbidden)
		return
	}

	key := r.Header.Get("Sec-WebSocket-Key")
	if key == "" {
		http.Error(w, "missing Sec-WebSocket-Key", http.StatusBadRequest)
		return
	}
	hj, ok := w.(http.Hijacker)
	if !ok {
		http.Error(w, "hijack unsupported", http.StatusInternalServerError)
		return
	}
	conn, rw, err := hj.Hijack()
	if err != nil {
		return
	}

	h := sha1.Sum([]byte(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"))
	accept := base64.StdEncoding.EncodeToString(h[:])
	resp := "HTTP/1.1 101 Switching Protocols\r\n" +
		"Upgrade: websocket\r\n" +
		"Connection: Upgrade\r\n" +
		"Sec-WebSocket-Accept: " + accept + "\r\n\r\n"
	_, _ = rw.WriteString(resp)
	_ = rw.Flush()

	client := &wsClient{conn: conn}
	s.clientsMu.Lock()
	s.clients[client] = struct{}{}
	s.clientsMu.Unlock()

	s.stateMu.RLock()
	st := s.uiState
	s.stateMu.RUnlock()
	st.IPs = localIPv4s()
	b, _ := json.Marshal(st)
	_ = client.sendFrame(0x1, b)

	go s.readWSLoop(client, rw.Reader)
}

func (s *SimulatorServer) readWSLoop(client *wsClient, br *bufio.Reader) {
	defer func() {
		s.clientsMu.Lock()
		delete(s.clients, client)
		s.clientsMu.Unlock()
		_ = client.conn.Close()
	}()
	for {
		b0, err := br.ReadByte()
		if err != nil {
			return
		}
		b1, err := br.ReadByte()
		if err != nil {
			return
		}
		op := b0 & 0x0F
		if op == 0x8 {
			return
		}
		masked := (b1 & 0x80) != 0
		length := int(b1 & 0x7F)
		if length == 126 {
			var ext [2]byte
			if _, err := io.ReadFull(br, ext[:]); err != nil {
				return
			}
			length = int(binary.BigEndian.Uint16(ext[:]))
		} else if length == 127 {
			var ext [8]byte
			if _, err := io.ReadFull(br, ext[:]); err != nil {
				return
			}
			return // UI input is small JSON, never a 64-bit media frame.
		}
		if !masked || length > 4096 {
			return
		}
		var maskKey [4]byte
		if masked {
			if _, err := io.ReadFull(br, maskKey[:]); err != nil {
				return
			}
		}
		payload := make([]byte, length)
		if _, err := io.ReadFull(br, payload); err != nil {
			return
		}
		if masked {
			for i := range payload {
				payload[i] ^= maskKey[i%4]
			}
		}
		if op == 0x1 {
			var msg struct {
				Type   string  `json:"type"`
				X      float32 `json:"x"`
				Y      float32 `json:"y"`
				Action byte    `json:"action"`
			}
			if json.Unmarshal(payload, &msg) == nil && msg.Type == "touch" {
				s.tcpOutMu.Lock()
				conn := s.tcpOut
				s.tcpOutMu.Unlock()
				if conn == nil || !s.crypto.IsActive() {
					continue
				}
				tp := make([]byte, 9)
				binary.BigEndian.PutUint32(tp[0:4], math.Float32bits(msg.X))
				binary.BigEndian.PutUint32(tp[4:8], math.Float32bits(msg.Y))
				tp[8] = msg.Action
				s.sendControl(conn, opTouchEvent, tp)
			}
		}
	}
}

func startBeaconBroadcaster() {
	go func() {
		conn, err := net.ListenPacket("udp4", ":0")
		if err != nil {
			return
		}
		defer conn.Close()
		msg := fmt.Sprintf(
			`{"proto":"byd-mirror-v1","name":"Chinese EV Simulator","model":"BYD / Chinese EV","port":%d,"mediaPort":%d,"hevc":false}`,
			controlPort, mediaUDPPort,
		)
		payload := []byte(msg)
		globalAddr := &net.UDPAddr{IP: net.IPv4bcast, Port: beaconUDPPort}

		ticker := time.NewTicker(1 * time.Second)
		defer ticker.Stop()
		for {
			_, _ = conn.WriteTo(payload, globalAddr)
			if ifaces, err := net.Interfaces(); err == nil {
				for _, iface := range ifaces {
					if (iface.Flags&net.FlagUp) == 0 || (iface.Flags&net.FlagLoopback) != 0 || isVirtualAdapter(iface.Name) {
						continue
					}
					if addrs, err := iface.Addrs(); err == nil {
						for _, a := range addrs {
							if ipnet, ok := a.(*net.IPNet); ok {
								if v4 := ipnet.IP.To4(); v4 != nil && len(ipnet.Mask) == 4 && !v4.IsLinkLocalUnicast() {
									bcast := make(net.IP, 4)
									for i := 0; i < 4; i++ {
										bcast[i] = v4[i] | ^ipnet.Mask[i]
									}
									_, _ = conn.WriteTo(payload, &net.UDPAddr{IP: bcast, Port: beaconUDPPort})
								}
							}
						}
					}
				}
			}
			<-ticker.C
		}
	}()
}

func ensureWindowsFirewall() {
	if runtime.GOOS != "windows" {
		return
	}
	_ = exec.Command("netsh", "advfirewall", "firewall", "add", "rule",
		"name=BYD Screen Simulator TCP", "dir=in", "action=allow", "protocol=TCP", "localport=47810").Run()
	_ = exec.Command("netsh", "advfirewall", "firewall", "add", "rule",
		"name=BYD Screen Simulator UDP", "dir=in", "action=allow", "protocol=UDP", "localport=47811,47813,5353").Run()
}

func openBrowser(url string) {
	switch runtime.GOOS {
	case "windows":
		_ = exec.Command("rundll32", "url.dll,FileProtocolHandler", url).Start()
	case "darwin":
		_ = exec.Command("open", url).Start()
	default:
		_ = exec.Command("xdg-open", url).Start()
	}
}

func main() {
	if os.Getenv("DRIVELINK_CONFIGURE_FIREWALL") == "1" {
		ensureWindowsFirewall()
	}
	srv := NewSimulatorServer()
	srv.startUDP()
	srv.startTCP()
	startMDNSResponder()
	startBeaconBroadcaster()

	webSub, err := fs.Sub(embeddedWeb, "web")
	if err != nil {
		log.Fatal(err)
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/ws", srv.handleWS)
	mux.HandleFunc("/api/status", func(w http.ResponseWriter, r *http.Request) {
		srv.stateMu.RLock()
		st := srv.uiState
		srv.stateMu.RUnlock()
		st.IPs = localIPv4s()
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(st)
	})
	mux.Handle("/", http.FileServer(http.FS(webSub)))

	ips := localIPv4s()
	log.Printf("BYD & Chinese EV Screen Simulator running!")
	log.Printf("Control TCP: %d | Media UDP: %d | UI: http://127.0.0.1:%d", controlPort, mediaUDPPort, webUIPort)
	if len(ips) > 0 {
		log.Printf("Phone IP to connect: %s", strings.Join(ips, ", "))
	}

	go func() {
		time.Sleep(400 * time.Millisecond)
		openBrowser(fmt.Sprintf("http://127.0.0.1:%d", webUIPort))
	}()

	log.Fatal(http.ListenAndServe(fmt.Sprintf("127.0.0.1:%d", webUIPort), mux))
}
