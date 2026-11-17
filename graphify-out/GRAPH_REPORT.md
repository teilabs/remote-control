# Graph Report - remote-control  (2026-11-17)

## Corpus Check
- 33 files · ~12,755 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 320 nodes · 670 edges · 25 communities (23 shown, 2 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 50 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `7e3757c7`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- .addSyncableControl
- MainActivity
- CommandArgument
- AppConfig
- SigningKeyStore
- Configured Command
- RequestSecurity
- Round Android App Launcher Icon
- Android App Launcher Icon
- Android Launcher Icon
- Round Android App Launcher Icon
- gradlew
- Android App Launcher Icon
- White Android Robot Head Symbol
- Android Launcher Icon
- White Android Robot Head Symbol
- Round Android Launcher Icon
- White Android Robot Head Symbol
- io.github.teilabs:remote
- Operations
- AGENTS.md
- Request security protocol

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 51 edges
2. `AppConfig` - 29 edges
3. `RemoteArgument` - 21 edges
4. `SigningKeyStore` - 20 edges
5. `RemoteCommand` - 17 edges
6. `CommandArgument` - 17 edges
7. `RequestSecurity` - 16 edges
8. `CommandExecutor` - 14 edges
9. `RemoteClient` - 13 edges
10. `RemoteServer` - 13 edges

## Surprising Connections (you probably didn't know these)
- `Ed25519 Request Signing` --semantically_similar_to--> `Ed25519 Request Authentication`  [INFERRED] [semantically similar]
  android/README.md → README.md
- `Device-Bound Credential Protection` --conceptually_related_to--> `Android Keystore`  [INFERRED]
  android/README.md → README.md
- `MainActivity` --references--> `SigningKeyStore`  [EXTRACTED]
  android/app/src/main/java/io/github/teilabs/remote/android/MainActivity.java → android/app/src/main/java/io/github/teilabs/remote/android/SigningKeyStore.java
- `SyncableControl` --references--> `RemoteCommand`  [EXTRACTED]
  android/app/src/main/java/io/github/teilabs/remote/android/MainActivity.java → android/app/src/main/java/io/github/teilabs/remote/android/RemoteCommand.java
- `RemoteCommand` --references--> `RemoteArgument`  [EXTRACTED]
  android/app/src/main/java/io/github/teilabs/remote/android/RemoteCommand.java → android/app/src/main/java/io/github/teilabs/remote/android/RemoteArgument.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Remote Command Metadata Flow** — readme_javalin_server, readme_command_metadata, readme_android_client, readme_configured_command [EXTRACTED 1.00]
- **Signed Protected Request Flow** — readme_android_client, readme_ed25519_request_authentication, readme_android_keystore, readme_protected_http_endpoints [EXTRACTED 1.00]

## Communities (25 total, 2 thin omitted)

### Community 0 - ".addSyncableControl"
Cohesion: 0.13
Nodes (13): ArgumentValue, RemoteValueSetter, SyncableControl, RemoteArgument, android.view.View, android.widget.LinearLayout, EditText, LayoutParams (+5 more)

### Community 1 - "MainActivity"
Cohesion: 0.10
Nodes (15): MainActivity, RemoteCommand, android.os.Bundle, android.os.Handler, android.widget.EditText, android.widget.ImageButton, android.widget.ProgressBar, android.widget.Spinner (+7 more)

### Community 2 - "CommandArgument"
Cohesion: 0.10
Nodes (14): ExampleInstrumentedTest, ExampleUnitTest, androidx.test.ext.junit.runners.AndroidJUnit4, org.junit.rules.TemporaryFolder, org.junit.runner.RunWith, org.junit.Test, ArgumentType, SELECT (+6 more)

### Community 3 - "AppConfig"
Cohesion: 0.13
Nodes (11): com.fasterxml.jackson.databind.JsonNode, com.fasterxml.jackson.databind.ObjectMapper, Ed25519PublicKeyParameters, org.bouncycastle.crypto.params.Ed25519PublicKeyParameters, App, AppConfig, Command, CommandType (+3 more)

### Community 4 - "SigningKeyStore"
Cohesion: 0.10
Nodes (7): ExecutionResult, RemoteClient, SigningKeyStore, android.content.Context, android.content.SharedPreferences, java.security.PrivateKey, javax.crypto.SecretKey

### Community 5 - "Configured Command"
Cohesion: 0.09
Nodes (24): Android Emulator Host Routing, Android Keystore, Connection Settings, Device-Bound Credential Protection, Ed25519 Request Signing, Remote Android, Server Command Metadata, Server app.json Configuration (+16 more)

### Community 6 - "RequestSecurity"
Cohesion: 0.11
Nodes (11): io.javalin.http.Context, java.security.KeyPair, java.security.PublicKey, KeyPair, PrivateKey, CommandExecutor, ExecutionResult, UnknownCommandException (+3 more)

### Community 7 - "Round Android App Launcher Icon"
Cohesion: 0.50
Nodes (4): Android Robot Symbol, Circular Icon Border, Green Grid Background, Round Android App Launcher Icon

### Community 8 - "Android App Launcher Icon"
Cohesion: 0.50
Nodes (4): Android Robot Symbol, Green Grid Background, Android App Launcher Icon, Rounded Square Icon Frame

### Community 9 - "Android Launcher Icon"
Cohesion: 0.50
Nodes (4): Android Launcher Icon, Diagonal Robot Shadow, Green Square Grid Background, White Android Robot Head

### Community 10 - "Round Android App Launcher Icon"
Cohesion: 0.50
Nodes (4): Android Robot Symbol, Circular Icon Border, Green Grid Background, Round Android App Launcher Icon

### Community 11 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 12 - "Android App Launcher Icon"
Cohesion: 0.67
Nodes (3): Android Robot Symbol, Green Grid Background, Android App Launcher Icon

### Community 13 - "White Android Robot Head Symbol"
Cohesion: 1.00
Nodes (3): White Android Robot Head Symbol, Green Globe Grid Background, Round Android Launcher Icon

### Community 14 - "Android Launcher Icon"
Cohesion: 0.67
Nodes (3): Android Launcher Icon, White Android Robot Head, Green Grid Background

### Community 15 - "White Android Robot Head Symbol"
Cohesion: 1.00
Nodes (3): White Android Robot Head Symbol, Green Grid Background, Android Launcher Icon

### Community 16 - "Round Android Launcher Icon"
Cohesion: 0.67
Nodes (3): Green Circular Grid Background, Round Android Launcher Icon, White Android Robot Head

### Community 17 - "White Android Robot Head Symbol"
Cohesion: 1.00
Nodes (3): White Android Robot Head Symbol, Green Globe Grid Background, Round Android Launcher Icon

### Community 22 - "Operations"
Cohesion: 0.22
Nodes (7): Android code map, Architecture, Server code map, Configuration and startup, Desktop session, Keep secrets and commands safe, Operations

### Community 24 - "Request security protocol"
Cohesion: 0.29
Nodes (5): HTTP API, Initial key exchange, Protected requests after exchange, Request security protocol, Trust and limits

## Knowledge Gaps
- **38 isolated node(s):** `io.github.teilabs:remote`, `SLIDER`, `TEXT`, `SELECT`, `TOGGLE` (+33 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **2 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MainActivity` connect `MainActivity` to `.addSyncableControl`, `SigningKeyStore`?**
  _High betweenness centrality (0.245) - this node is a cross-community bridge._
- **Why does `AppConfig` connect `AppConfig` to `CommandArgument`, `RequestSecurity`?**
  _High betweenness centrality (0.217) - this node is a cross-community bridge._
- **Why does `SigningKeyStore` connect `SigningKeyStore` to `MainActivity`?**
  _High betweenness centrality (0.197) - this node is a cross-community bridge._
- **What connects `io.github.teilabs:remote`, `SLIDER`, `TEXT` to the rest of the system?**
  _38 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `.addSyncableControl` be split into smaller, more focused modules?**
  _Cohesion score 0.13048780487804879 - nodes in this community are weakly interconnected._
- **Should `MainActivity` be split into smaller, more focused modules?**
  _Cohesion score 0.09574468085106383 - nodes in this community are weakly interconnected._
- **Should `CommandArgument` be split into smaller, more focused modules?**
  _Cohesion score 0.10476190476190476 - nodes in this community are weakly interconnected._