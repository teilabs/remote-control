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
import java.security.spec.PKCS8EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SigningKeyStore {
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String WRAPPING_KEY_ALIAS = "remote_signing_key_wrapper";
    private static final String PREFS = "remote_credentials";
    private static final String PRIVATE_KEY = "private_key";
    private static final String PUBLIC_KEY = "public_key";
    private static final String IV = "private_key_iv";

    private final SharedPreferences preferences;

    SigningKeyStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureKeyPair();
    }

    String publicKeyBase64() {
        return preferences.getString(PUBLIC_KEY, "");
    }

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

    private byte[] decryptPrivateKey() throws Exception {
        byte[] encrypted = Base64.decode(preferences.getString(PRIVATE_KEY, ""), Base64.NO_WRAP);
        byte[] iv = Base64.decode(preferences.getString(IV, ""), Base64.NO_WRAP);
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
