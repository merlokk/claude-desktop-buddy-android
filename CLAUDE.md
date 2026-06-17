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

See [`docs/REFERENCE.md`](docs/REFERENCE.md) for the full message catalog (a copy of the upstream
protocol reference, with a link back to its source).

### Implemented

- Heartbeat snapshot → buddy state, and permission decisions (`once` / `deny`).
- `status` command → status ack with the complete `data` set (battery incl. current, uptime, free
  heap, device name, link security, and stats). The desktop's status panel needs the full set, or
  it shows "No response".
- `name` (sets the reported device name), `owner` (shown on the buddy screen), and `unpair` → acks.
- BLE advertising with a `Claude`-prefixed device name (the phone's Bluetooth name is temporarily
  prefixed while advertising) so the desktop's device picker lists the phone.
- Turn events → the latest turn (role + text) shown on the buddy screen.
- Time sync → the desktop's UTC offset is shown as a time-zone line on the buddy screen.
- Stale-connection detection → no inbound line for ~30s flips the buddy screen to "Disconnected".

### Not yet implemented

These parts of the protocol are intentionally not built yet:

- **Folder push / character preview** (`char_begin` / `file` / `chunk` / `file_end` / `char_end`) —
  the desktop's folder-drop streaming, and the device "preview" the desktop renders from the pushed
  character pack. We don't accept it (the pet/character feature), so we never ack `char_begin` and
  the desktop's preview stays blank. Unrelated to the buddy loop.
- **Link encryption / bonding** — the link is unencrypted, so the status ack reports `"sec": false`
  and transcript snippets and tool-call hints travel in the clear. This was attempted (encrypted
  GATT permissions to force pairing) and reverted: as a peripheral, Android can't drive the
  protocol's recommended pairing — no control of the IO capability (only Just Works, no displayed
  passkey), no way to send a *Service Changed* indication, and `removeBond()` is blocked on Android
  16, so bonds desync the moment the desktop "Forget"s and reconnects fail with
  `HCI_ERR_AUTH_FAILURE`. The `sec` flag and the `unpair` hook stay plumbed (transport
  `isLinkSecure` / `unpair`) with safe defaults, ready if a viable approach appears.

## Architecture

Dependencies point inward: UI → domain → the transport interface, and the BLE implementation
depends on that interface, never the reverse — so no Android Bluetooth type leaks into the logic.
The code is organized by package (root `com.example.claudedesktopbuddy`):

- **`protocol`** — the wire protocol: parsing inbound messages and serializing outbound ones. Pure
  Kotlin.
- **`transport`** — the `DesktopTransport` interface (inbound JSON lines in, a sink for outbound
  lines out) plus the line framing (split incoming bytes on `\n`, append `\n` on send).
  Bluetooth-free, so the framing is unit-testable on the JVM.
- **`ble`** — `BleDesktopTransport`, the real BLE peripheral that implements `DesktopTransport`:
  advertising, the GATT server, the RX/TX characteristics. The only place Android Bluetooth APIs
  are used.
- **`buddy`** — the domain: `BuddyState` (what Claude is doing, the pending prompt), the
  framework-free `BuddyViewModel` orchestration, and the thin Android `BuddyAndroidViewModel`
  wrapper. Pure Kotlin apart from the wrapper; this is where most TDD happens.
- **`device`** — `AndroidDeviceStatusProvider`, which reads battery/uptime/heap for the status ack
  (Android APIs, behind the framework-free `DeviceStatusProvider` interface in `buddy`).
- **`log`** — `ExchangeLog`, the raw-traffic log model.
- **`ui`** — the two Jetpack Compose screens. No protocol or Bluetooth logic here.

The logging feature observes the raw line traffic at the transport boundary so it can record
exactly what crossed the wire, independent of parsing.

## Tech stack

- Kotlin, Jetpack Compose (Material 3, adaptive navigation suite).
- kotlinx.serialization for JSON, kotlinx.coroutines for the `Flow`-based transport.
- `minSdk` 24, `targetSdk` 36, `compileSdk` 36.1. Version catalog in `gradle/libs.versions.toml`.
- Tests: JUnit4 + kotlinx-coroutines-test for JVM unit tests (`app/src/test`); Compose UI / Espresso
  for instrumented tests (`app/src/androidTest`). Favor JVM unit tests for domain and framing logic.
- Package root: `com.example.claudedesktopbuddy`.

## Build & test

```bash
./gradlew test                 # JVM unit tests — the main TDD loop
./gradlew connectedAndroidTest # instrumented tests (needs a device/emulator)
./gradlew assembleDebug        # build the debug APK
```

## Git workflow

Commit directly to `main` — this project does not use feature branches. (Per the global commit
rule, mark AI-assisted commits with a trailing ` +ai` on the subject line, not a `Co-Authored-By`
trailer.)

## Permissions note

Acting as a BLE peripheral needs `BLUETOOTH_ADVERTISE` and `BLUETOOTH_CONNECT` at runtime on
Android 12 (API 31)+, and the install-time `BLUETOOTH` / `BLUETOOTH_ADMIN` on API 30 and below.
Location is **not** required — the phone advertises rather than scans. These are declared in
`AndroidManifest.xml`; the runtime permissions are requested from the composition lifecycle
(`BleLifecycle` in `MainActivity`) before the transport starts.