# HTTP API

The server listens on port `7000` by default. All protected routes require the
headers described in [the security protocol](protocol.md). Except for the
initial key exchange, request and response bodies are AES-GCM encrypted on the
wire. Examples below show the plaintext form fields and JSON after decryption.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Public liveness check. Returns `{"status":"ok"}`. |
| `POST` | `/key-exchange` | Protected bootstrap exchange; returns the server EC public key in an encrypted envelope. |
| `GET` | `/config` | Returns configured command metadata. |
| `POST` | `/value` | Runs the read command for one syncable command. |
| `POST` | `/execute` | Executes a configured command. |

`POST /value` accepts a URL-encoded `command` field. A successful response is:

```json
{"value":"42"}
```

`POST /execute` accepts `command` plus optional `arg.<name>` fields:

```text
command=Set%20volume&arg.volume=42
```

Its successful response contains the process exit code and combined output:

```json
{"exitCode":0,"output":""}
```

Errors use an `error` string. Typical statuses are `400` for invalid/missing
input, `401` for authentication/decryption failure, `404` for an unknown
command, and `502` when a configured process cannot run or its read command
fails.

