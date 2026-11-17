package io.github.teilabs.remote;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import io.github.teilabs.remote.config.AppConfig;
import io.javalin.http.Context;

/** Verifies signed requests and encrypts/decrypts their message bodies. */
public final class RequestSecurity {
    static final String FORM_ATTRIBUTE = "e2e.form";

    private static final String KEY_ATTRIBUTE = "e2e.key";
    private static final String CLIENT_KEY_HEADER = "X-Client-Encryption-Key";

    private final AppConfig config;
    private final KeyPair serverEncryptionKey;

    /** Creates the security middleware and loads or creates the persistent server EC key pair. */
    public RequestSecurity(AppConfig config) {
        this.config = config;
        this.serverEncryptionKey = loadOrCreateEncryptionKey();
    }

    /** Rejects protected requests without a valid, recent Ed25519 device signature. */
    void verifySignature(Context ctx) {
        if (ctx.path().equals("/health")) return;

        String timestamp = ctx.header("X-Timestamp");
        String signature = ctx.header("X-Signature");
        String clientKey = ctx.header(CLIENT_KEY_HEADER);
        if (timestamp == null || signature == null) {
            reject(ctx, "Missing timestamp or signature");
            return;
        }
        if (clientKey == null) {
            reject(ctx, "Missing client encryption key");
            return;
        }

        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            reject(ctx, "Invalid timestamp");
            return;
        }

        long now = System.currentTimeMillis();
        if (requestTime < now - config.ttl() || requestTime > now + config.ttl()) {
            reject(ctx, "Timestamp expired");
            return;
        }

        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(signature);
        } catch (IllegalArgumentException e) {
            reject(ctx, "Invalid signature encoding");
            return;
        }

        byte[] messageBytes = (timestamp + clientKey + ctx.body()).getBytes(StandardCharsets.UTF_8);
        Ed25519Signer signer = new Ed25519Signer();
        for (Ed25519PublicKeyParameters publicKey : config.publicKeys()) {
            signer.init(false, publicKey);
            signer.update(messageBytes, 0, messageBytes.length);
            if (signer.verifySignature(signatureBytes)) return;
        }
        reject(ctx, "Invalid signature");
    }

    /** Decrypts protected request bodies and stores decoded form data on the request context. */
    void decryptRequest(Context ctx) {
        if (ctx.path().equals("/health")) return;
        try {
            PublicKey clientPublicKey = decodePublicKey(ctx.header(CLIENT_KEY_HEADER));
            byte[] key = deriveKey(serverEncryptionKey.getPrivate(), clientPublicKey);
            ctx.attribute(KEY_ATTRIBUTE, key);

            if (!ctx.path().equals("/key-exchange") && !ctx.body().isEmpty()) {
                byte[] plaintext = crypt(
                        Cipher.DECRYPT_MODE, key, Base64.getDecoder().decode(ctx.body()));
                ctx.attribute(FORM_ATTRIBUTE, new String(plaintext, StandardCharsets.UTF_8));
            } else {
                ctx.attribute(FORM_ATTRIBUTE, "");
            }
        } catch (Exception e) {
            reject(ctx, "Invalid encrypted request");
        }
    }

    /** Returns the server encryption public key as the plaintext for the encrypted key exchange. */
    void sendServerKey(Context ctx) {
        ctx.json(Map.of("serverKey", Base64.getEncoder()
                .encodeToString(serverEncryptionKey.getPublic().getEncoded())));
    }

    /** Encrypts a protected response body before it is sent to the Android client. */
    void encryptResponse(Context ctx) {
        byte[] key = ctx.attribute(KEY_ATTRIBUTE);
        if (key == null || ctx.path().equals("/health")) return;

        try {
            if (ctx.path().equals("/key-exchange")) {
                encryptKeyExchangeResponse(ctx);
                return;
            }
            ctx.result(Base64.getEncoder().encodeToString(
                    crypt(Cipher.ENCRYPT_MODE, key,
                            ctx.result().getBytes(StandardCharsets.UTF_8))));
            ctx.contentType("text/plain");
        } catch (Exception e) {
            ctx.status(500).result("Unable to encrypt response");
        }
    }

    private void encryptKeyExchangeResponse(Context ctx) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ephemeral = generator.generateKeyPair();
        PublicKey clientPublicKey = decodePublicKey(ctx.header(CLIENT_KEY_HEADER));
        byte[] handshakeKey = deriveKey(ephemeral.getPrivate(), clientPublicKey);
        String encryptedPayload = Base64.getEncoder().encodeToString(
                crypt(Cipher.ENCRYPT_MODE, handshakeKey,
                        ctx.result().getBytes(StandardCharsets.UTF_8)));

        ctx.json(Map.of(
                "ephemeralKey", Base64.getEncoder().encodeToString(
                        ephemeral.getPublic().getEncoded()),
                "ciphertext", encryptedPayload));
    }

    private static PublicKey decodePublicKey(String encoded) throws Exception {
        return KeyFactory.getInstance("EC").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
    }

    private static byte[] deriveKey(java.security.PrivateKey privateKey, PublicKey publicKey)
            throws Exception {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey);
        agreement.doPhase(publicKey, true);
        return MessageDigest.getInstance("SHA-256").digest(agreement.generateSecret());
    }

    private static byte[] crypt(int mode, byte[] key, byte[] input) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        if (mode == Cipher.ENCRYPT_MODE) {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(input);
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return result;
        }

        byte[] iv = java.util.Arrays.copyOfRange(input, 0, 12);
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return cipher.doFinal(input, 12, input.length - 12);
    }

    private static KeyPair loadOrCreateEncryptionKey() {
        Path privatePath = Path.of(System.getProperty(
                "remote.serverKey", "config/server-encryption-key.pk8"));
        Path publicPath = Path.of(privatePath + ".pub");
        try {
            KeyFactory factory = KeyFactory.getInstance("EC");
            if (Files.exists(privatePath) && Files.exists(publicPath)) {
                return new KeyPair(
                        factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(publicPath))),
                        factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(privatePath))));
            }

            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keyPair = generator.generateKeyPair();
            Files.createDirectories(privatePath.toAbsolutePath().getParent());
            Files.write(privatePath, keyPair.getPrivate().getEncoded());
            Files.write(publicPath, keyPair.getPublic().getEncoded());
            try {
                Files.setPosixFilePermissions(privatePath,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Filesystems without POSIX permissions still get an OS-restricted file.
            }
            return keyPair;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load server encryption key", e);
        }
    }

    private static void reject(Context ctx, String message) {
        ctx.status(401).json(Map.of("error", message)).skipRemainingHandlers();
    }
}
