# Remote Control

Remote Control lets an Android device run a configured set of commands on a
computer. The server publishes command metadata, the Android app builds its UI
from that metadata, and every protected request is signed with an Ed25519 key
stored on the Android device.

Commands can be simple buttons, commands with arguments, or live synchronized
controls such as volume sliders, mute toggles, selections, and text values.

## Project structure

```text
remote-control/
|-- android/               Android client
|-- server/                Java 21 and Javalin server
|   |-- config/
|   |   |-- app.json       Active server configuration
|   |   `-- app.json.example
|   `-- src/
`-- README.md
```

## Requirements

### Server

- JDK 21 or newer
- Maven 3.8 or newer
- The executables used by configured commands
- TCP port `7000` reachable from the Android device

### Android

- JDK 17 or newer
- Android SDK with API 36 installed
- A device running Android 12/API 31 or newer, or an Android emulator
- `adb` when installing from the command line

The Gradle wrapper is included, so a separate Gradle installation is not
required.

## Quick setup

### 1. Build and install the Android app

From the repository root:

```bash
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open the app, select the settings icon, and use **Copy public key**. The app
generates its Ed25519 key pair automatically on first launch. Its private key
stays encrypted on the device using Android Keystore.

Clearing the application's data or reinstalling it may generate a new key. If
that happens, copy the new public key into the server configuration.

### 2. Configure the server

Edit `server/config/app.json`. If the file does not exist, start from the
example:

```bash
cp server/config/app.json.example server/config/app.json
```

Replace `GENERATED_PUBLIC_KEY` with the public key copied from Android (you can also connect more than one Android device just by adding them to the `publicKeys` array):

```json
{
  "ttlMs": 30000,
  "publicKeys": ["PUBLIC_KEY_COPIED_FROM_ANDROID", "PUBLIC_KEY_COPIED_FROM_ANOTHER_ANDROID"],
  "commands": []
}
```

`ttlMs` is the maximum allowed clock difference and request age in
milliseconds. Keep the computer and phone clocks synchronized.

### 3. Run the server

The default configuration path is relative to the `server` directory, so run
the application from there:

```bash
cd server
mvn package dependency:copy-dependencies
java -cp 'target/remote-1.0-SNAPSHOT.jar:target/dependency/*' \
  io.github.teilabs.remote.App
```

The server listens on port `7000`. Confirm that it is running:

```bash
curl http://127.0.0.1:7000/health
```

Expected response:

```json
{"status":"ok"}
```

To use a configuration at another location:

```bash
java -Dremote.config=/absolute/path/to/app.json \
  -cp 'target/remote-1.0-SNAPSHOT.jar:target/dependency/*' \
  io.github.teilabs.remote.App
```

### 4. Connect Android

Open the settings screen in the Android app and enter the server URL.

- Physical device: `http://COMPUTER_LAN_IP:7000`
- Android emulator: `http://10.0.2.2:7000`

The phone and computer must be on the same reachable network unless a VPN,
reverse proxy, or another routing solution is used.

## Configuring commands

Every command has these common fields:

| Field | Required | Description |
| --- | --- | --- |
| `name` | Yes | Unique command name displayed by the app |
| `type` | Yes | `SIMPLE` or `SYNCABLE` |
| `executable` | Yes | Program to execute |
| `args` | No | Array of fixed arguments and argument placeholders |
| `arguments` | No | UI argument definitions |
| `read` | For `SYNCABLE` | Command used to read the current argument value |
| `needConfirmation` | No | Ask for confirmation before execution |
| `needNotificationOnComplete` | No | Show a result dialog after completion |

Both interaction fields must be JSON booleans. When omitted, they default to
`true` for `SIMPLE` commands and `false` for `SYNCABLE` commands. They control
Android UI behavior; server-side authorization still comes from request
signature validation.

The server uses `ProcessBuilder`. The executable and every item in `args` are
passed as separate process arguments. Shell syntax such as pipes, redirects,
and `&&` is not interpreted unless the executable is explicitly `sh` with
`-c`.

### Simple command

A command without configurable arguments appears with a Run button:

```json
{
  "name": "lock",
  "type": "SIMPLE",
  "needConfirmation": true,
  "needNotificationOnComplete": true,
  "executable": "dms",
  "args": [
    "ipc",
    "call",
    "lock",
    "lock"
  ]
}
```

This executes:

```text
dms ipc call lock lock
```

### Command with arguments

Define controls in `arguments` and reference their values with `${name}`
placeholders in `args`:

```json
{
  "name": "audio-profile",
  "type": "SIMPLE",
  "executable": "audio-control",
  "args": [
    "--volume",
    "${volume}",
    "--profile",
    "${profile}",
    "--muted",
    "${muted}",
    "--device",
    "${device}"
  ],
  "arguments": [
    {
      "name": "volume",
      "label": "Volume",
      "type": "SLIDER",
      "min": 0,
      "max": 100,
      "step": 1,
      "default": 50
    },
    {
      "name": "profile",
      "label": "Profile",
      "type": "SELECT",
      "options": ["music", "movie", "voice"],
      "default": "music"
    },
    {
      "name": "muted",
      "label": "Muted",
      "type": "TOGGLE",
      "default": false
    },
    {
      "name": "device",
      "label": "Device",
      "type": "TEXT",
      "default": "speakers"
    }
  ]
}
```

The Android app renders the controls and sends their values when Run is
confirmed. The server validates values before starting the process.

### Argument types

#### `SLIDER`

```json
{
  "name": "brightness",
  "label": "Brightness",
  "type": "SLIDER",
  "min": 0,
  "max": 100,
  "step": 5,
  "default": 50
}
```

`min`, `max`, and `step` are required. The range must be evenly divisible by
the step, and the default must be inside the range and aligned to the step.

#### `SELECT`

```json
{
  "name": "profile",
  "label": "Profile",
  "type": "SELECT",
  "options": ["quiet", "balanced", "performance"],
  "default": "balanced"
}
```

`options` must contain at least one item. The server accepts only listed
values.

#### `TOGGLE`

```json
{
  "name": "enabled",
  "label": "Enabled",
  "type": "TOGGLE",
  "default": true
}
```

The process receives the string `true` or `false`.

#### `TEXT`

```json
{
  "name": "message",
  "label": "Message",
  "type": "TEXT",
  "default": "Hello"
}
```

Text is passed as one process argument. Be careful when placing text
placeholders inside `sh -c`, because the shell will interpret the resulting
string.

## Synchronized controls

A `SYNCABLE` command represents one live value. It must have exactly one
argument of any supported type and a `read` command.

```json
{
  "name": "set-volume",
  "type": "SYNCABLE",
  "needConfirmation": false,
  "needNotificationOnComplete": false,
  "executable": "wpctl",
  "args": [
    "set-volume",
    "@DEFAULT_AUDIO_SINK@",
    "${volume}%"
  ],
  "arguments": [
    {
      "name": "volume",
      "label": "Volume",
      "type": "SLIDER",
      "min": 0,
      "max": 100,
      "step": 1,
      "default": 50
    }
  ],
  "read": {
    "executable": "sh",
    "args": [
      "-c",
      "wpctl get-volume @DEFAULT_AUDIO_SINK@ | awk '{print $2 * 100}'"
    ]
  }
}
```

The `read` command must:

- Exit successfully.
- Print only the argument value.
- Return a value valid for the configured argument type.

For example, the slider above expects `50`, not `0.50`. The `awk` expression
converts the value returned by `wpctl`.

The Android client reads the value when commands load and then every two
seconds. It commits changes according to the control:

| Argument type | When Android sends the new value |
| --- | --- |
| `SLIDER` | When the slider is released |
| `TOGGLE` | When the switch changes |
| `SELECT` | When a new option is selected |
| `TEXT` | When Done is pressed or the field loses focus |

Incoming values are paused while a control is being edited or updated.

A synchronized toggle uses the same one-value contract:

```json
{
  "name": "set-mute",
  "type": "SYNCABLE",
  "needConfirmation": false,
  "needNotificationOnComplete": false,
  "executable": "wpctl",
  "args": [
    "set-mute",
    "@DEFAULT_AUDIO_SINK@",
    "${muted}"
  ],
  "arguments": [
    {
      "name": "muted",
      "label": "Muted",
      "type": "TOGGLE",
      "default": false
    }
  ],
  "read": {
    "executable": "sh",
    "args": [
      "-c",
      "wpctl get-volume @DEFAULT_AUDIO_SINK@ | grep -q MUTED && echo true || echo false"
    ]
  }
}
```

## Building from source

### Server

Run tests and build the JAR:

```bash
cd server
mvn clean test package
```

The resulting JAR is:

```text
server/target/remote-1.0-SNAPSHOT.jar
```

The JAR does not bundle dependencies. To prepare a standalone classpath:

```bash
cd server
mvn clean package dependency:copy-dependencies
java -cp 'target/remote-1.0-SNAPSHOT.jar:target/dependency/*' \
  io.github.teilabs.remote.App
```

### Android

Run unit tests, lint, and build the debug APK:

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The APK is written to:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Install or update it:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The project does not currently define a production release signing
configuration. `assembleDebug` is the intended local build.

## Request authentication

On first launch, Android generates an Ed25519 key pair:

- The private key is encrypted using Android Keystore and never sent to the
  server.
- The public key is copied into `server/config/app.json`.
- Protected requests include `X-Timestamp` and `X-Signature` headers.
- The signature covers the timestamp followed by the exact request body.
- `/health` is public; `/config`, `/value`, and `/execute` require a valid
  signature.

Request signing authenticates the device but does not encrypt network traffic.
Use this project only on a trusted LAN or VPN, or place the server behind an
HTTPS reverse proxy. Do not expose port `7000` directly to the public internet.

The server configuration can execute arbitrary programs. Restrict access to
the configuration file and run the server as an unprivileged user.

## Desktop-session commands

Commands such as `dms`, Quickshell IPC calls, and compositor utilities depend
on the environment of the active graphical session. Start the server from a
terminal opened inside that session.

Important variables can include:

```text
XDG_RUNTIME_DIR
DBUS_SESSION_BUS_ADDRESS
WAYLAND_DISPLAY
DISPLAY
NIRI_SOCKET
XDG_CURRENT_DESKTOP
```

For a systemd user service, import the active environment before restarting
the server:

```bash
systemctl --user import-environment \
  XDG_RUNTIME_DIR \
  DBUS_SESSION_BUS_ADDRESS \
  WAYLAND_DISPLAY \
  DISPLAY \
  NIRI_SOCKET \
  XDG_CURRENT_DESKTOP
```

Use an absolute executable path in `app.json` if the service has a different
`PATH`.

## Troubleshooting

### Android cannot connect

- Check `http://SERVER_IP:7000/health` from another device.
- Verify that the server is listening and port `7000` is allowed by the
  firewall.
- Use `10.0.2.2`, not `localhost`, from the standard Android emulator.
- Confirm that the phone and computer can reach each other.

### Server returns `401`

- Copy the current Android public key into `publicKeys`.
- Restart the server after editing `app.json`.
- Synchronize the phone and computer clocks.
- Increase `ttlMs` only when necessary.

### Server returns `400`

The command name or an argument is missing or invalid. Check slider ranges,
steps, select options, and placeholders in `app.json`.

### Server returns `502`

The configured process could not start, the sync read command failed, or the
desktop-session environment is missing. Run the exact executable and arguments
from the same terminal used to launch the server.

### A synchronized control does not update

- Run its `read` command manually.
- Confirm that it outputs only one value.
- For sliders, check range, units, and step alignment.
- For toggles, output exactly `true` or `false`.
- For selects, output one configured option exactly.

## API summary

| Method | Path | Signed | Purpose |
| --- | --- | --- | --- |
| `GET` | `/health` | No | Server health check |
| `GET` | `/config` | Yes | Load command and UI metadata |
| `POST` | `/value` | Yes | Read a synchronized argument value |
| `POST` | `/execute` | Yes | Execute a configured command |

## License

Licensed under the MIT License. See [LICENSE](LICENSE).
