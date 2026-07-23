# Remote Android

The app loads command metadata from the server and signs every protected HTTP
request with a device-generated Ed25519 key.

## Connect a device

1. Build and install the debug app:

   ```bash
   ./gradlew :app:installDebug
   ```

2. Open **Connection settings** in the app and copy the public key.
3. Set that value as `publicKeyBase64` in `server/config/app.json`.
4. Restart the server.
5. Set the server URL in the app. The Android emulator reaches the host at
   `http://10.0.2.2:7000`; a physical device needs the server machine's LAN URL.

The private signing key never leaves the device. It is encrypted using an AES
key held by Android Keystore, and application backup is disabled so encrypted
credentials cannot be restored without their device-bound wrapping key.
