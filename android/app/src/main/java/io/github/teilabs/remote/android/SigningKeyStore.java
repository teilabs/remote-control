package io.github.teilabs.remote.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.ECGenParameterSpec;
import javax.crypto.KeyAgreement;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Creates, protects, and uses the Android device's signing and encryption keys. */
final class SigningKeyStore {
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String WRAPPING_KEY_ALIAS = "remote_signing_key_wrapper";
    private static final String PREFS = "remote_credentials";
    private static final String PRIVATE_KEY = "private_key";
    private static final String PUBLIC_KEY = "public_key";
    private static final String IV = "private_key_iv";
    private static final String ENC_PRIVATE = "enc_private";
    private static final String ENC_PUBLIC = "enc_public";
    private static final String ENC_IV = "enc_private_iv";
    private static final String SERVER_PUBLIC = "server_enc_public";

    private final SharedPreferences preferences;

    /** Opens the credential store and creates device keys when they do not exist. */
    SigningKeyStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureKeyPair();
        ensureEncryptionKeyPair();
    }

    /** Returns the raw Ed25519 public key in Base64 for server allow-list setup. */
    String publicKeyBase64() {
        return preferences.getString(PUBLIC_KEY, "");
    }

    /** Signs a UTF-8 request message with the device's Ed25519 private key. */
    String sign(String message) {
        try {
            byte[] privateKeyBytes = decryptPrivateKey();
            PrivateKey privateKey = KeyFactory.getInstance("Ed25519")
                    .generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(signature.sign(), Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign request", e);
        }
    }

    /** Returns the encoded EC public key used during the initial key exchange. */
    synchronized String encryptionPublicKeyBase64() { return preferences.getString(ENC_PUBLIC, ""); }

    /** Returns the server EC key pinned during the first successful exchange, if any. */
    synchronized String serverPublicKeyBase64() { return preferences.getString(SERVER_PUBLIC, ""); }

    /** Persists the server public key received during the initial exchange. */
    synchronized void saveServerPublicKey(String key) {
        preferences.edit().putString(SERVER_PUBLIC, key).apply();
    }

    /** Derives the AES-GCM channel key from this device's private key and the server public key. */
    synchronized byte[] encryptionKey(String serverKeyBase64) throws Exception {
        return deriveKey(serverKeyBase64);
    }

    synchronized byte[] encryptionKeyFromServerPublic(String serverKeyBase64) throws Exception {
        return deriveKey(serverKeyBase64);
    }

    private byte[] deriveKey(String serverKeyBase64) throws Exception {
        byte[] privateBytes = decrypt(ENC_PRIVATE, ENC_IV);
        PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
        PublicKey serverKey = KeyFactory.getInstance("EC").generatePublic(
                new X509EncodedKeySpec(Base64.decode(serverKeyBase64, Base64.NO_WRAP)));
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey); agreement.doPhase(serverKey, true);
        return java.security.MessageDigest.getInstance("SHA-256").digest(agreement.generateSecret());
    }

    private void ensureKeyPair() {
        if (preferences.contains(PRIVATE_KEY) && preferences.contains(PUBLIC_KEY)) {
            return;
        }

        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            KeyPair keyPair = generator.generateKeyPair();
            byte[] encodedPublicKey = keyPair.getPublic().getEncoded();
            byte[] rawPublicKey = new byte[32];
            System.arraycopy(encodedPublicKey, encodedPublicKey.length - rawPublicKey.length,
                    rawPublicKey, 0, rawPublicKey.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey());
            byte[] encryptedPrivateKey = cipher.doFinal(keyPair.getPrivate().getEncoded());

            preferences.edit()
                    .putString(PRIVATE_KEY, Base64.encodeToString(encryptedPrivateKey, Base64.NO_WRAP))
                    .putString(PUBLIC_KEY, Base64.encodeToString(rawPublicKey, Base64.NO_WRAP))
                    .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .apply();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create signing key", e);
        }
    }

    private void ensureEncryptionKeyPair() {
        if (preferences.contains(ENC_PRIVATE) && preferences.contains(ENC_PUBLIC)) return;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey());
            preferences.edit()
                    .putString(ENC_PRIVATE, Base64.encodeToString(cipher.doFinal(pair.getPrivate().getEncoded()), Base64.NO_WRAP))
                    .putString(ENC_PUBLIC, Base64.encodeToString(pair.getPublic().getEncoded(), Base64.NO_WRAP))
                    .putString(ENC_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).apply();
        } catch (Exception e) { throw new IllegalStateException("Unable to create encryption key", e); }
    }

    private byte[] decryptPrivateKey() throws Exception {
        return decrypt(PRIVATE_KEY, IV);
    }

    private byte[] decrypt(String encryptedName, String ivName) throws Exception {
        byte[] encrypted = Base64.decode(preferences.getString(encryptedName, ""), Base64.NO_WRAP);
        byte[] iv = Base64.decode(preferences.getString(ivName, ""), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), new GCMParameterSpec(128, iv));
        return cipher.doFinal(encrypted);
    }

    private SecretKey wrappingKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        if (keyStore.containsAlias(WRAPPING_KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(WRAPPING_KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(
                WRAPPING_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
