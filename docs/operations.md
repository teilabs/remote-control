# Operations

## Configuration and startup

Copy `server/config/app.json.example` to `server/config/app.json`, then add the
Android app's Ed25519 public key to `publicKeys`. The active file is local and
ignored by Git. The default config path is relative to the server working
directory; start from `server/` or set `-Dremote.config=/absolute/path/app.json`.

Build and run the server from `server/`:

```bash
mvn package dependency:copy-dependencies
java -cp 'target/remote-1.0-SNAPSHOT.jar:target/dependency/*' \
  io.github.teilabs.remote.App
```

The Android project can be built with:

```bash
cd android
./gradlew :app:assembleDebug
```

## Keep secrets and commands safe

- Protect `app.json` and the server encryption private key. Back them up
  together; losing/changing the server key means clients must establish a new
  server pin.
- Configure only commands you intend the Android client to invoke. The server
  executes them with its own user permissions.
- Run the server as an unprivileged user and prefer absolute executable paths
  for service deployments.
- Restrict network access to the server. Use HTTPS or a trusted LAN/VPN; the
  application encryption does not hide request metadata.

## Desktop session

Commands that use D-Bus, Wayland, a compositor, or shell IPC need the active
desktop session's environment. Start the server from that session or import
the required variables into its systemd user service. Common variables include
`XDG_RUNTIME_DIR`, `DBUS_SESSION_BUS_ADDRESS`, `WAYLAND_DISPLAY`, and
`DISPLAY`.

