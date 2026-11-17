# Request security protocol

The app and server use two different key pairs. Ed25519 authenticates the
Android device; P-256 ECDH derives an AES key for message bodies. The server
allows devices whose Ed25519 public keys appear in `publicKeys` in
`app.json`.

## Initial key exchange

1. Android generates its encryption key pair and sends its encoded EC public
   key in `X-Client-Encryption-Key`.
2. It signs the timestamp, that public-key header, and the exact wire body with
   its Ed25519 private key.
3. `POST /key-exchange` returns the server's long-term encryption public key
   inside a one-time ECDH/AES-GCM envelope. The response includes the one-time
   ephemeral public key needed by the Android app to derive the envelope key.
4. Android stores the received server key and uses it for subsequent body
   encryption and decryption.

The server private key is stored at
`server/config/server-encryption-key.pk8` by default, with the public key beside
it as `.pub`. Set `-Dremote.serverKey=/path/to/server-key.pk8` to choose another
location. Preserve the key pair across server restarts.

## Protected requests after exchange

The client encrypts each non-empty request body with AES-GCM. For the
long-term channel key, both peers perform ECDH using the Android private key
and server public key, then apply SHA-256 to the shared secret. The wire body
is Base64 of `12-byte IV || ciphertext || 16-byte GCM tag`.

Requests include:

- `X-Timestamp`: Unix time in milliseconds.
- `X-Client-Encryption-Key`: Base64-encoded X.509 EC public key.
- `X-Signature`: Base64 Ed25519 signature over the UTF-8 concatenation of
  `timestamp + clientPublicKeyHeader + exactWireBody`.

The server rejects timestamps outside `ttlMs`, verifies the signature against
the configured device keys, and only then decrypts the body. Protected response
bodies are encrypted with the same derived channel key. `/health` is public.

## Trust and limits

The Android app pins the server public key received on first use. The initial
encrypted exchange prevents passive observers from reading the key, but does
not authenticate the server against an active attacker who substitutes a key
during that first connection. Verify the server key fingerprint over a trusted
channel before relying on the pin. Use HTTPS or a trusted LAN/VPN as additional
transport protection. Encryption protects message bodies, not URLs, headers,
traffic timing, or traffic size.

