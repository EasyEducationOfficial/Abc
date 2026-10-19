# MsgVault - Local Notification-Based Deleted Message Saver

MsgVault is a native Android application built with Jetpack Compose, Material 3, and Room Database (SQLite). Its primary purpose is to capture incoming social media message notifications locally on the device and preserve them even if the sender unsends or deletes the message later.

---

## Key Features

1. **Local Notification Interception:**
   - Extends Android's official `NotificationListenerService`.
   - Captures incoming messages from WhatsApp, Messenger, Instagram, Telegram, Signal, Discord, Snapchat, Viber, LINE, Google Messages, and Skype.
   - Extracts `MessagingStyle` bundled messages, multi-line notifications, conversation titles, and attachment metadata (e.g. `[Photo]`, `[Voice Message]`).

2. **Deleted Message Retention Guarantee:**
   - Once a notification is saved to the local SQLite database, the record is **never** deleted when the notification is dismissed or unsent.
   - Preserved across device reboots and app updates.
   - Configurable retention policies: *Keep Forever*, *7 Days*, *30 Days*, *90 Days*.

3. **100% Offline & Private:**
   - Zero cloud servers, zero analytics, zero external network requests.
   - All message records remain strictly on-device.

4. **Privacy & Security Controls:**
   - Optional 4-digit PIN code App Lock.
   - Biometric authentication unlock (fingerprint/face).
   - Instant database wipe options with confirmation safeguards.

5. **Management & Search:**
   - Instant local search across sender names, message content, app titles, and group names.
   - Filter chips by messaging platform (WhatsApp, Telegram, etc.) and time intervals (Today, Yesterday, Last 7 Days).
   - Bookmark favorite/starred messages.
   - Share and copy message text via native Sharesheet and Clipboard.
   - Multi-select batch deletion.

6. **Diagnostics & Testing:**
   - Live notification listener connection diagnostic checks.
   - Built-in test notification generator allowing immediate verification of the interception pipeline.
   - OEM battery optimization guide (Samsung OneUI, Xiaomi MIUI/HyperOS, OnePlus OxygenOS, Google Pixel).

---

## Technical Specifications

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Database:** Room Database (SQLite) with KSP
- **Architecture:** MVVM + Repository Pattern
- **Min SDK:** 26 (Android 8.0 Oreo)
- **Target SDK:** 36 (Android 15+)
- **Preferences:** Jetpack DataStore Preferences

---

## Building & Installation

### 1. Build Debug APK
```bash
gradle assembleDebug
```
The debug APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Run Local JVM & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

### 3. Granting Android Notification Access
On first launch:
1. Open MsgVault.
2. Tap **Grant Notification Access** (or navigate to Android Settings → Apps & notifications → Special app access → Notification access).
3. Toggle **MsgVault** to **Allowed**.
4. Return to MsgVault and tap **Test Capture** on the dashboard to verify instant local saving.
