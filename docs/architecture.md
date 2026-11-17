# Architecture

Remote Control has two runtime components:

- **Android app**: stores device credentials, fetches command metadata, renders
  controls, and sends signed/encrypted requests.
- **Java server**: exposes the HTTP API, checks device signatures, decrypts
  request bodies, validates arguments, and runs configured programs.

## Server code map

| Class | Responsibility |
| --- | --- |
| `App` | Loads configuration and starts the server. |
| `RemoteServer` | Defines HTTP routes and maps requests to server operations. |
| `RequestSecurity` | Verifies request signatures, manages the server encryption key, and handles the ECDH/AES-GCM envelope. |
| `AppConfig` | Loads and validates `app.json`. |
| `CommandExecutor` | Validates command arguments and starts configured processes. |

## Android code map

| Class | Responsibility |
| --- | --- |
| `MainActivity` | Connection settings, command list, and control UI. |
| `RemoteClient` | Builds HTTP requests and parses API responses. |
| `SigningKeyStore` | Creates and stores signing/encryption credentials and derives the body-encryption key. |
| `RemoteCommand`, `RemoteArgument` | Client-side models for server-provided command metadata. |

The server runs configured commands as the same operating-system user that
started it. For desktop controls, that process must inherit the active desktop
session environment; see [operations](operations.md).

