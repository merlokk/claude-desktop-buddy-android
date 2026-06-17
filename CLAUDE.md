# CLAUDE.md

Guidance for working in this repository.

## What this is

An Android port of [`anthropics/claude-desktop-buddy`](https://github.com/anthropics/claude-desktop-buddy).

The original is firmware for an ESP32 / M5StickCPlus hardware "buddy" that pairs with the
Claude desktop app over Bluetooth Low Energy. It shows what Claude is currently doing and lets
the user approve or deny permission prompts from a small physical device.

This project replaces that hardware with an **Android phone**. The phone plays the same role the
ESP32 plays in the original: it is the **BLE peripheral** (GATT server) that advertises the
Nordic UART Service, and the Claude desktop app is the **central** that connects to it. From the
desktop's point of view the phone is just another Hardware Buddy.

We are not porting the pet/animation/energy features of the original firmware. We implement the
core buddy loop: show Claude's activity, and answer permission prompts.

## Hard rules

- **Everything in the repo is in English** — code, comments, identifiers, commit messages,
  documentation, and test names. No exceptions.
- **TDD.** Write a failing test first, then the code that makes it pass, then refactor. No
  production logic lands without a test that exercises it.
- **Readable code, no cruft.** Clear names, small functions, no dead code, no copy-paste. If a
  reviewer can't follow it on first read, rewrite it.
- **The BLE transport is separated from the application logic** (see Architecture). Domain and UI
  code must not depend on Android Bluetooth APIs directly.

## App screens

Two screens, reachable from the bottom navigation.

1. **Buddy (home)** — mirrors the original device's main screen.
   - An indicator of what Claude is currently busy with (status / current message).
   - When a permission prompt arrives: the question (what is being approved — tool and hint) plus
     a set of choice buttons (approve / deny).

2. **Logs** — the raw exchange with the desktop. One JSON object per line, in order.
   - Logging can be turned **on and off**.
   - It is **off by default**.

## BLE protocol (Nordic UART Service)

The phone advertises the Nordic UART Service and exchanges UTF-8 JSON, **one object per line,
terminated with `\n`**. Each side accumulates bytes until a newline, then parses one message.

| Component | UUID |
|-----------|------|
| Service | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` |
| RX (desktop → phone) | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` |
| TX (phone → desktop) | `6e400003-b5a3-f393-e0a9-e50e24dcca9e` |

### Desktop → phone (incoming)

Heartbeat snapshot (~10s keepalive) — drives the buddy screen:

```json
{
  "total": 3,
  "running": 1,
  "waiting": 1,
  "msg": "approve: Bash",
  "entries": ["10:42 git push"],
  "tokens": 184502,
  "tokens_today": 31200,
  "prompt": { "id": "req_abc123", "tool": "Bash", "hint": "rm -rf /tmp/foo" }
}
```

The optional `prompt` object means a permission decision is pending. Commands (`status`, `name`,
`owner`, `unpair`, …) and turn events (`{"evt":"turn",...}`) also arrive on RX.

### Phone → desktop (outgoing)

Permission decision (echo the prompt `id`):

```json
{ "cmd": "permission", "id": "req_abc123", "decision": "once" }
{ "cmd": "permission", "id": "req_abc123", "decision": "deny" }
```

`once` = approve, `deny` = reject. Status acks and generic command acks (`{"ack":"<cmd>","ok":true}`)
are also sent on TX.

See the original repo's `REFERENCE.md` for the full message catalog.

## Architecture

Keep three layers separate; dependencies point inward (UI → domain → transport interface, never
the reverse). The transport interface lives with the domain; the Android BLE implementation
depends on it, not vice versa.

- **Transport (BLE).** Owns Android Bluetooth: advertising the GATT server, the RX/TX
  characteristics, and the line-buffered framing (split incoming bytes on `\n`, append `\n` on
  send). Exposes a plain interface — a stream of inbound JSON lines in, a sink for outbound JSON
  lines out — and nothing Bluetooth-specific leaks past it.
- **Domain / logic.** Parses and builds protocol messages, models buddy state (what Claude is
  doing, the pending prompt), and decides what to send. Pure Kotlin, no Android dependencies, so
  it is unit-testable on the JVM. This is where most TDD happens.
- **UI (Jetpack Compose).** The two screens and a ViewModel observing domain state. No protocol
  or Bluetooth logic here.

The logging feature observes the raw line traffic at the transport boundary so it can record
exactly what crossed the wire, independent of parsing.

## Tech stack

- Kotlin, Jetpack Compose (Material 3, adaptive navigation suite).
- `minSdk` 24, `targetSdk` / `compileSdk` 36. Version catalog in `gradle/libs.versions.toml`.
- Tests: JUnit4 for JVM unit tests (`app/src/test`), Espresso / Compose UI test for instrumented
  tests (`app/src/androidTest`). Favor JVM unit tests for domain and transport-framing logic.
- Package root: `com.example.claudedesktopbuddy`.

## Build & test

```bash
./gradlew test                 # JVM unit tests — the main TDD loop
./gradlew connectedAndroidTest # instrumented tests (needs a device/emulator)
./gradlew assembleDebug        # build the debug APK
```

## Permissions note

Acting as a BLE peripheral on Android requires Bluetooth advertise/connect permissions
(`BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` on API 31+; legacy Bluetooth + location on older
versions). These are not yet declared in `AndroidManifest.xml` — add them when the transport
layer is implemented.