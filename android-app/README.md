# Wakey Android Mobile Application (Kotlin)

An Android application (API 26-34+) designed to ring continuously during an alarm, displaying an un-dismissible lockscreen barrier until a high-priority `STOP_ALARM` signal is received from your desktop PC via Firebase Cloud Messaging (FCM).

## Architecture & Components

- **`MainActivity.kt`**: Dashboard UI for setting exact alarms, checking battery optimization bypass permissions, and viewing/copying your phone's unique FCM registration token.
- **`AlarmReceiver.kt`**: Triggered by `AlarmManager` exact wakeup alarms, starting the foreground ringer service.
- **`AlarmRingerService.kt`**: High-priority foreground service running continuous audio playback and displaying the full-screen persistent notification and intent.
- **`AlarmActivity.kt`**: Lockscreen overlay activity (`showWhenLocked`, `turnScreenOn`, dismissKeyguard) with disabled back buttons to prevent dismissing from bed.
- **`MyFirebaseMessagingService.kt`**: Background FCM message receiver that intercepts `{ "action": "STOP_ALARM" }` and dispatches the kill command to `AlarmRingerService`.

## Setup Instructions

1. **Firebase Configuration**:
   - Go to [Firebase Console](https://console.firebase.google.com/) and create a project.
   - Add an Android App with package name: `com.example.wakey`.
   - Download `google-services.json` and place it at `android-app/app/google-services.json`.

2. **Custom Alarm Sound (Optional)**:
   - Place an audio file named `heavy_alarm.mp3` inside `android-app/app/src/main/res/raw/`.
   - If omitted, the app will automatically use your phone's default system alarm sound.

3. **Build & Run**:
   - Open the `android-app` directory in Android Studio or Antigravity IDE.
   - Sync Gradle and run the app on your physical Android device or emulator.

4. **Permissions Configuration on First Launch**:
   - Grant **Notification** permission.
   - Whitelist the app from **Battery Optimization** (allows background FCM signals while phone is sleeping).
   - Ensure **Alarms & Reminders (Exact Alarms)** permission is enabled in system settings.
