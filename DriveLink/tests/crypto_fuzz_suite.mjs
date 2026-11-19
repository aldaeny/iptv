#!/usr/bin/env node
import crypto from "node:crypto";
import dgram from "node:dgram";
import fs from "node:fs";
import net from "node:net";
import path from "node:path";
import assert from "node:assert/strict";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..");
const MAGIC = 0x4259;
const VERSION = 0x01;
const KDF_INFO = Buffer.from("BYD-MIRROR-SESSION-KEY-V1", "utf8");
const P256_SPKI_PREFIX = Buffer.from(
  "3059301306072a8648ce3d020106082a8648ce3d030107034200",
  "hex"
);

function buildHeader(opcode, seq, tsUs, payloadLen) {
  const b = Buffer.allocUnsafe(16);
  b.writeUInt16BE(MAGIC, 0);
  b.writeUInt8(VERSION, 2);
  b.writeUInt8(opcode, 3);
  b.writeUInt32BE(seq >>> 0, 4);
  b.writeUInt32BE(tsUs >>> 0, 8);
  b.writeUInt32BE(payloadLen >>> 0, 12);
  return b;
}

function computePin(derivedKey) {
  const h = crypto.createHash("sha256").update(derivedKey).digest();
  const pinInt = ((h[0] & 0x7f) << 16) | (h[1] << 8) | h[2];
  return String(pinInt % 1_000_000).padStart(6, "0");
}

function encryptGcm(key, plain, aad) {
  const nonce = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", key, nonce);
  cipher.setAAD(aad);
  const enc = Buffer.concat([cipher.update(plain), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([nonce, enc, tag]);
}

function decryptGcm(key, wire, aad) {
  assert(wire.length >= 28, "wire < 28 bytes");
  const nonce = wire.subarray(0, 12);
  const tag = wire.subarray(wire.length - 16);
  const ct = wire.subarray(12, wire.length - 16);
  const decipher = crypto.createDecipheriv("aes-256-gcm", key, nonce);
  decipher.setAAD(aad);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ct), decipher.final()]);
}

// 1. Android SPKI (91B) <-> Receiver SPKI ECDH + HKDF + AES-256-GCM
function testAndroidSpkiAndIosX963Interop() {
  const android = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const androidSpki = android.publicKey.export({ type: "spki", format: "der" });
  assert.equal(androidSpki.length, 91, "Android P-256 SPKI must be 91 bytes");

  const rx1 = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const rx1Spki = rx1.publicKey.export({ type: "spki", format: "der" });
  const salt1 = crypto.randomBytes(16);

  const sAndroid = crypto.diffieHellman({
    privateKey: android.privateKey,
    publicKey: crypto.createPublicKey({ key: rx1Spki, format: "der", type: "spki" }),
  });
  const sRx1 = crypto.diffieHellman({
    privateKey: rx1.privateKey,
    publicKey: crypto.createPublicKey({ key: androidSpki, format: "der", type: "spki" }),
  });
  assert(sAndroid.equals(sRx1), "Android SPKI ECDH shared secret mismatch");

  const kAndroid = Buffer.from(crypto.hkdfSync("sha256", sAndroid, salt1, KDF_INFO, 32));
  const kRx1 = Buffer.from(crypto.hkdfSync("sha256", sRx1, salt1, KDF_INFO, 32));
  assert.equal(computePin(kAndroid), computePin(kRx1));

  // iOS raw 65B X9.63 <-> Receiver P256_SPKI_PREFIX wrapping (exact ReceiverCryptoManager.kt logic)
  const ios = crypto.createECDH("prime256v1");
  ios.generateKeys();
  const iosRaw65 = ios.getPublicKey(null, "uncompressed");
  assert.equal(iosRaw65.length, 65);
  assert.equal(iosRaw65[0], 0x04);

  const wrappedSpki = Buffer.concat([P256_SPKI_PREFIX, iosRaw65]);
  const iosPubKeyObj = crypto.createPublicKey({ key: wrappedSpki, format: "der", type: "spki" });

  const rx2 = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const rx2Spki = rx2.publicKey.export({ type: "spki", format: "der" });
  assert(rx2Spki.subarray(0, 26).equals(P256_SPKI_PREFIX), "P256 SPKI prefix mismatch");
  const rx2Raw65 = rx2Spki.subarray(26);
  assert.equal(rx2Raw65.length, 65);

  const sIos = ios.computeSecret(rx2Raw65);
  const sRx2 = crypto.diffieHellman({ privateKey: rx2.privateKey, publicKey: iosPubKeyObj });
  assert(sIos.equals(sRx2), "iOS X9.63 <-> Receiver SPKI-prefix ECDH mismatch");

  const salt2 = crypto.randomBytes(16);
  const kIos = Buffer.from(crypto.hkdfSync("sha256", sIos, salt2, KDF_INFO, 32));
  const kRx2 = Buffer.from(crypto.hkdfSync("sha256", sRx2, salt2, KDF_INFO, 32));
  assert.equal(computePin(kIos), computePin(kRx2));

  // Test 0-byte empty encrypted control payload (28B)
  const hdrEmpty = buildHeader(0x12, 1, 1000, 28);
  const wireEmpty = encryptGcm(kRx2, Buffer.alloc(0), hdrEmpty);
  assert.equal(wireEmpty.length, 28);
  assert.equal(decryptGcm(kIos, wireEmpty, hdrEmpty).length, 0);

  // Tampered AAD must fail
  const tamperedHdr = Buffer.from(hdrEmpty);
  tamperedHdr[3] = 0x08;
  assert.throws(() => decryptGcm(kIos, wireEmpty, tamperedHdr));

  // Fuzz 200 random encrypted frames + nonce uniqueness check
  const seenNonces = new Set();
  for (let i = 1; i <= 200; i++) {
    const plain = crypto.randomBytes((i * 137) % 16384);
    const hdr = buildHeader(0x10, i, i * 16667, plain.length + 28);
    const wire = encryptGcm(kAndroid, plain, hdr);
    const nonceHex = wire.subarray(0, 12).toString("hex");
    assert(!seenNonces.has(nonceHex), "Nonce collision!");
    seenNonces.add(nonceHex);
    const dec = decryptGcm(kRx1, wire, hdr);
    assert(dec.equals(plain), "Fuzz decrypt payload mismatch");
  }
}

// 2. Test Web Simulator SPS & Keyframe parsers from web/index.html directly
function testSimulatorWebCodecsParsers() {
  const html = fs.readFileSync(path.join(ROOT, "simulator/web/index.html"), "utf8");
  const fnMatch1 = html.match(/function extractAvcCodecFromSps\(nalu\)\s*\{[\s\S]*?\n    \}/);
  const fnMatch2 = html.match(/function isKeyFrame\(nalu, mime\)\s*\{[\s\S]*?\n    \}/);
  assert(fnMatch1 && fnMatch2, "Missing WebCodecs parser functions in web/index.html");

  const extractAvcCodecFromSps = new Function(`${fnMatch1[0]} return extractAvcCodecFromSps;`)();
  const isKeyFrame = new Function(`${fnMatch2[0]} return isKeyFrame;`)();

  // Baseline level 4.2 (0x42 0x00 0x2a)
  const sps = Uint8Array.from([0, 0, 0, 1, 0x67, 0x42, 0x00, 0x2a, 0x99]);
  assert.equal(extractAvcCodecFromSps(sps), "avc1.42002a");
  assert.equal(isKeyFrame(sps, "video/avc"), false);

  // Combined SPS + PPS + IDR (nalType 5)
  const idr = Uint8Array.from([0, 0, 0, 1, 0x67, 0x64, 0x00, 0x2a, 0, 0, 0, 1, 0x68, 0xee, 0, 0, 0, 1, 0x65, 0x88]);
  assert.equal(extractAvcCodecFromSps(idr), "avc1.64002a");
  assert.equal(isKeyFrame(idr, "video/avc"), true);

  // HEVC IDR_W_RADL (nalType 19 -> (19 << 1) = 0x26)
  const hevcIdr = Uint8Array.from([0, 0, 0, 1, 0x26, 0x01, 0xaf]);
  assert.equal(isKeyFrame(hevcIdr, "video/hevc"), true);
}

// 3. Fuzz AntiReplayWindow logic & Fragment bounds
function testAntiReplayFuzz() {
  let highest = 0n;
  let bitmap = 0n;
  function accept(seq) {
    if (seq <= 0) return false;
    const u = BigInt(seq);
    if (highest === 0n) {
      highest = u;
      bitmap = 1n;
      return true;
    }
    if (u > highest) {
      const shift = u - highest;
      bitmap = shift >= 64n ? 1n : ((bitmap << shift) | 1n) & ((1n << 64n) - 1n);
      highest = u;
      return true;
    }
    const delta = highest - u;
    if (delta >= 64n) return false;
    const mask = 1n << delta;
    if ((bitmap & mask) !== 0n) return false;
    bitmap |= mask;
    return true;
  }

  assert.equal(accept(0), false);
  for (let s = 1; s <= 500; s++) {
    assert.equal(accept(s), true);
    assert.equal(accept(s), false); // immediate replay rejected
  }
  assert.equal(accept(436), false); // >64 old rejected
  assert.equal(accept(505), true);
  assert.equal(accept(502), true); // out-of-order inside window
  assert.equal(accept(502), false); // replay of out-of-order rejected
}

testAndroidSpkiAndIosX963Interop();
testSimulatorWebCodecsParsers();
testAntiReplayFuzz();

// 4. Live Loopback TCP+UDP BYD-WMP v1.0 Handshake + Fragmented NALU + Negative Drop Test
async function testLiveLoopbackProtocol() {
  const rxKeys = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const txKeys = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  let rxSessionKey = null;
  let rxEncActive = false;
  let lastTcpSeq = 0;
  let reassembledVideo = null;
  const fragBuckets = new Map();

  const udpServer = dgram.createSocket("udp4");
  await new Promise((res) => udpServer.bind(0, "127.0.0.1", res));
  const udpPort = udpServer.address().port;

  udpServer.on("message", (msg) => {
    if (!rxEncActive || msg.length < 16) return;
    const hdr = msg.subarray(0, 16);
    if (hdr.readUInt16BE(0) !== MAGIC || hdr[2] !== VERSION) return;
    const opcode = hdr[3];
    const len = hdr.readUInt32BE(12);
    if (msg.length < 16 + len) return;
    let plain;
    try {
      plain = decryptGcm(rxSessionKey, msg.subarray(16, 16 + len), hdr);
    } catch {
      return; // reject forged/unauthenticated UDP
    }
    if (opcode === 0x13 && plain.length > 8) {
      const fid = plain.readUInt32BE(0);
      const idx = plain.readUInt16BE(4);
      const total = plain.readUInt16BE(6);
      const body = plain.subarray(8);
      if (total > 256 || idx >= total) return;
      if (!fragBuckets.has(fid)) fragBuckets.set(fid, new Map());
      const bucket = fragBuckets.get(fid);
      bucket.set(idx, body);
      if (bucket.size === total) {
        const ordered = [];
        for (let i = 0; i < total; i++) ordered.push(bucket.get(i));
        reassembledVideo = Buffer.concat(ordered);
        fragBuckets.delete(fid);
      }
    }
  });

  const tcpServer = net.createServer((sock) => {
    let buf = Buffer.alloc(0);
    let outSeq = 0;
    const sendTcp = (opcode, plain, encrypt) => {
      outSeq += 1;
      const wireLen = encrypt ? plain.length + 28 : plain.length;
      const hdr = buildHeader(opcode, outSeq, 1000, wireLen);
      const body = encrypt ? encryptGcm(rxSessionKey, plain, hdr) : plain;
      sock.write(Buffer.concat([hdr, body]));
    };

    sock.on("data", (chunk) => {
      buf = Buffer.concat([buf, chunk]);
      while (buf.length >= 16) {
        const hdr = buf.subarray(0, 16);
        assert.equal(hdr.readUInt16BE(0), MAGIC);
        const opcode = hdr[3];
        const seq = hdr.readUInt32BE(4);
        const len = hdr.readUInt32BE(12);
        if (buf.length < 16 + len) break;
        const wire = buf.subarray(16, 16 + len);
        buf = buf.subarray(16 + len);
        assert(seq > lastTcpSeq, "TCP replay rejected");
        lastTcpSeq = seq;
        const plain = rxEncActive ? decryptGcm(rxSessionKey, wire, hdr) : wire;

        if (opcode === 0x01) {
          sendTcp(0x02, Buffer.from("BYD-DILINK-5.0|15.6"), false);
        } else if (opcode === 0x03) {
          const salt = crypto.randomBytes(16);
          const shared = crypto.diffieHellman({
            privateKey: rxKeys.privateKey,
            publicKey: crypto.createPublicKey({ key: plain, format: "der", type: "spki" }),
          });
          rxSessionKey = Buffer.from(crypto.hkdfSync("sha256", shared, salt, KDF_INFO, 32));
          const rxSpki = rxKeys.publicKey.export({ type: "spki", format: "der" });
          sendTcp(0x04, Buffer.concat([salt, rxSpki]), false);
          rxEncActive = true;
        } else if (opcode === 0x05) {
          sendTcp(0x06, Buffer.from("1920x1080@60|H265,H264"), true);
        } else if (opcode === 0x07) {
          sendTcp(0x12, Buffer.alloc(0), true); // empty 28B KEYFRAME_REQUEST
        } else if (opcode === 0x20) {
          sendTcp(0x21, plain, true);
        } else if (opcode === 0x08) {
          sock.end();
        }
      }
    });
  });

  await new Promise((res) => tcpServer.listen(0, "127.0.0.1", res));
  const tcpPort = tcpServer.address().port;

  let txSessionKey = null;
  let txEnc = false;
  let txTcpSeq = 0;
  let gotPong = false;

  await new Promise((resolve, reject) => {
    const client = net.createConnection({ host: "127.0.0.1", port: tcpPort });
    let rxBuf = Buffer.alloc(0);
    const send = (op, plain, enc) => {
      txTcpSeq += 1;
      const hdr = buildHeader(op, txTcpSeq, 2000, enc ? plain.length + 28 : plain.length);
      const body = enc ? encryptGcm(txSessionKey, plain, hdr) : plain;
      client.write(Buffer.concat([hdr, body]));
    };

    client.on("connect", () => send(0x01, Buffer.from("BYD-TEST-PHONE"), false));
    client.on("error", reject);
    client.on("data", (chunk) => {
      rxBuf = Buffer.concat([rxBuf, chunk]);
      while (rxBuf.length >= 16) {
        const hdr = rxBuf.subarray(0, 16);
        const op = hdr[3];
        const len = hdr.readUInt32BE(12);
        if (rxBuf.length < 16 + len) break;
        const wire = rxBuf.subarray(16, 16 + len);
        rxBuf = rxBuf.subarray(16 + len);
        const plain = txEnc ? decryptGcm(txSessionKey, wire, hdr) : wire;

        if (op === 0x02) {
          send(0x03, txKeys.publicKey.export({ type: "spki", format: "der" }), false);
        } else if (op === 0x04) {
          const salt = plain.subarray(0, 16);
          const peerSpki = plain.subarray(16);
          const shared = crypto.diffieHellman({
            privateKey: txKeys.privateKey,
            publicKey: crypto.createPublicKey({ key: peerSpki, format: "der", type: "spki" }),
          });
          txSessionKey = Buffer.from(crypto.hkdfSync("sha256", shared, salt, KDF_INFO, 32));
          txEnc = true;
          send(0x05, Buffer.from("dilink_15_6_fhd|1920x1080@60"), true);
        } else if (op === 0x06) {
          send(0x07, Buffer.from("video/avc"), true);
        } else if (op === 0x12) {
          // Send forged unencrypted UDP packet (must be ignored) + 3-part fragmented encrypted NALU
          const udpClient = dgram.createSocket("udp4");
          const fakeHdr = buildHeader(0x10, 1, 10, 4);
          udpClient.send(Buffer.concat([fakeHdr, Buffer.from("FAKE")]), udpPort, "127.0.0.1");

          const fullNalu = Buffer.concat([Buffer.from([0, 0, 0, 1, 0x65]), crypto.randomBytes(2500)]);
          const chunks = [fullNalu.subarray(0, 1150), fullNalu.subarray(1150, 2300), fullNalu.subarray(2300)];
          // Send out of order (2, 0, 1) to verify real UDP reassembly
          for (const idx of [2, 0, 1]) {
            const fragHdr = Buffer.alloc(8);
            fragHdr.writeUInt32BE(99, 0);
            fragHdr.writeUInt16BE(idx, 4);
            fragHdr.writeUInt16BE(3, 6);
            const fragPlain = Buffer.concat([fragHdr, chunks[idx]]);
            const hdr = buildHeader(0x13, idx + 1, 3000, fragPlain.length + 28);
            const pkt = Buffer.concat([hdr, encryptGcm(txSessionKey, fragPlain, hdr)]);
            udpClient.send(pkt, udpPort, "127.0.0.1");
          }
          setTimeout(() => {
            assert(reassembledVideo && reassembledVideo.equals(fullNalu), "Live UDP out-of-order reassembly failed");
            udpClient.close();
            send(0x20, Buffer.from("12345678"), true);
          }, 60);
        } else if (op === 0x21) {
          gotPong = true;
          send(0x08, Buffer.alloc(0), true);
        }
      }
    });
    client.on("close", resolve);
  });

  assert(gotPong, "Live TCP loopback heartbeat pong missing");
  udpServer.close();
  tcpServer.close();
}

await testLiveLoopbackProtocol();
console.log("CRYPTO, INTEROP, FUZZ, LIVE LOOPBACK TCP/UDP & WEBCODECS SUITE: ALL PASSED!");
