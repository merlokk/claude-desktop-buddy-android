# claude-desktop-buddy-android

An Android port of [`anthropics/claude-desktop-buddy`](https://github.com/anthropics/claude-desktop-buddy).

The original is firmware for a small ESP32 hardware "buddy" that pairs with the Claude desktop app
over Bluetooth Low Energy: it shows what Claude is doing and lets you approve or deny permission
prompts from the device. This project replaces that hardware with an **Android phone** — the phone
plays the same role the ESP32 plays, acting as the **BLE peripheral** (GATT server) that advertises
the Nordic UART Service, so from the desktop's point of view it is just another Hardware Buddy.

## Features

- **Buddy screen** — shows what Claude is currently doing and, when a permission prompt arrives,
  the question (the tool being requested plus a hint) with **Approve** / **Deny** buttons.
- **Logs screen** — the raw line-by-line JSON exchanged with the desktop. Toggleable, and **off by
  default**.

## Requirements

- **JDK 21.** The Gradle daemon is pinned to a Java 21 toolchain
  (`gradle/gradle-daemon-jvm.properties`); Gradle auto-provisions it if your default JDK differs.
- **Android SDK** with API level **36** installed.
- An Android device or emulator running **API 24 (Android 7.0)** or newer. The BLE peripheral role
  needs real Bluetooth hardware that supports BLE advertising — most physical phones do; emulators
  generally do not.
- Android Studio is optional; the Gradle wrapper builds everything from the command line.

The toolchain (Gradle 9.4.1, AGP 9.2.1, Kotlin 2.2.10) is pinned by the wrapper and version
catalog, so no manual install of Gradle or Kotlin is needed.

## Setup

Point the build at your Android SDK by creating `local.properties` in the project root:

```properties
sdk.dir=/path/to/your/Android/Sdk
```

Android Studio writes this file automatically when you open the project.

## Build

> **Windows:** use `.\gradlew` (or `gradlew.bat`) in place of `./gradlew` in every command below.

```bash
./gradlew assembleDebug      # build the debug APK
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Run

With a device connected (USB debugging enabled) or an emulator running:

```bash
./gradlew installDebug       # build and install on the connected device
```

Then launch **claude desktop buddy** from the app launcher. On first start the app requests the
Bluetooth permissions it needs to advertise (`BLUETOOTH_ADVERTISE` and `BLUETOOTH_CONNECT` on
Android 12+); grant them, and make sure Bluetooth is turned on.

### Pairing with the desktop

1. Enable developer mode in the Claude desktop app.
2. Open **Developer → Open Hardware Buddy**.
3. With the Android app in the foreground (it advertises the Nordic UART service while on screen),
   the desktop should discover and connect to it.

Once connected, the buddy screen reflects what Claude is doing, and permission prompts can be
answered from the phone. Flip on the **Logs** toggle to watch the raw JSON exchange.

## Test

```bash
./gradlew test               # JVM unit tests — the protocol, framing, state, and logic
```

The protocol parsing/serialization, line framing, buddy state, exchange log, and orchestration are
pure Kotlin and covered by JVM unit tests. The BLE transport is the thin Android edge and is
verified on a device rather than by unit tests.

## Project layout

```
app/src/main/java/com/example/claudedesktopbuddy/
  protocol/    Wire protocol: parsing inbound messages, serializing outbound ones
  transport/   DesktopTransport interface + line framing (\n) — Bluetooth-free
  buddy/       BuddyState (domain) + BuddyViewModel orchestration + Android ViewModel wrapper
  log/         ExchangeLog — the raw-traffic log model
  ble/         BleDesktopTransport — the real BLE peripheral (Nordic UART GATT server)
  ui/          Compose screens (BuddyScreen, LogsScreen)
```

The dependencies point inward: UI → domain → the transport interface, and the BLE implementation
depends on that interface, never the reverse — so no Android Bluetooth type leaks into the logic.
See `CLAUDE.md` for the conventions used in this repository.
