# Notification History

🇺🇸 **English** · 🇧🇷 [Português](README.pt-BR.md)

Android app that keeps a local history of your notifications and recovers messages deleted in messengers. Free, no ads, and **100% offline**: the app has no internet permission.

> Current version: **0.4.0**. Tested on Xiaomi (HyperOS 3.0, Android 16). Requires Android 8.0+ (API 26).

## Features

- **Notification history** from any app, grouped by conversation, with an app filter and per-app capture on/off.
- **Deleted messages**: WhatsApp and WhatsApp Business. Detects both the "message was deleted" placeholder and messages that disappear from the notification, and keeps the original text.
- **Other messengers**: Telegram, Instagram and Messenger, plus generic capture for any app.
- **Search** by text (partial words), "deleted only" filter, multi-select to mark read/unread, pin and delete.
- **Metadata**: tappable links, image references and notification actions.
- **Configurable retention**: 90 days by default. Deleted messages have their own window, and pinned messages never expire.
- **Security**: encrypted database (SQLCipher with an Android Keystore key), biometric/PIN lock, and content hidden from Recents and screenshots.
- **Backup**: export and import a ZIP/JSON in a folder you choose, without duplicating messages.
- **Received media** (experimental): copies photos from a folder you pick when the file time matches the message.
- UI in **English** and **Portuguese (BR)**.

## Installing on a phone

1. Download or build the APK (see [Build](#build-and-tests)).
2. On the phone, enable **Developer options → USB debugging**. On Xiaomi, also enable **Install via USB** (it may require an active SIM and a Mi account).
3. Install:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Open the app and follow the onboarding:
   - grant **notification access**;
   - on Xiaomi/MIUI/HyperOS, enable **Autostart** and remove the app from **battery saver**, so the system doesn't kill the capture.

## Build and tests

Requirements: Android SDK (platform 36) and JDK 17–21. Android Studio's bundled JDK (`jbr`) works; very new JDKs (e.g. 25) aren't supported by Gradle 8.14.

```bash
./gradlew assembleDebug testDebugUnitTest
```

On Windows, use `gradlew.bat` and point `JAVA_HOME` to `C:\Program Files\Android\Android Studio\jbr`. Create a `local.properties` with `sdk.dir=...` (it stays out of git).

> Tip: build outside folders synced by OneDrive. The sync locks files in `build/` and `.git/`.

Stack: Kotlin 2.4, Jetpack Compose (Material 3), Room + FTS, SQLCipher, WorkManager, AGP 8.13, Gradle 8.14.

## Structure

| Module / folder | Contents |
|---|---|
| `app/` | The app. Business rules live in the **Ingestor** (`ingest/`); the notification service only forwards data. |
| `qa-sender/` | QA helper app ("NH QA Sender") that simulates chats and deletes messages, drivable via `adb`. See `docs/implementations/2026-11-qa-sender.md`. |
| `docs/implementations/` | What was built in each part, decisions and gotchas. |
| `docs/agents/` | Git Flow, versioning, testing/QA and issue tracker. |
| `AGENTS.md` / `CONTEXT.md` | Rules for AI agents and the domain vocabulary. |

## Workflow

Git Flow: `feat/*` / `fix/*` → `staging` → `develop` → `release/*` → `main`. Protected branches don't take direct pushes. SemVer versioning with a bilingual CHANGELOG. Details in `docs/agents/branching.md` and `docs/agents/versioning.md`.

## Known limitations

- Only captures messages that produced a notification after installation (not muted chats or the chat open on screen).
- "Deleted by removal" detection compares with the previous notification kept in memory. If the system kills the app between the two, that deletion may be missed.
- Media recovery depends on folders reachable through Android's folder picker. On Android 11+, many app folders are blocked.
- Exported backups are **not encrypted**, and copied media is stored unencrypted in the app's private storage.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
