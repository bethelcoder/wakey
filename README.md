# Wakey: PC-Dismissed Custom Android Alarm System

A dual-component alarm ecosystem designed to ensure you get out of bed by strictly requiring physical desktop/laptop browser authentication to silence your mobile alarm.

```
wakey/
├── android-app/             # Component A: Native Android Application (Kotlin)
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/example/pcalarm/
│   │   │   └── res/
│   │   ├── build.gradle.kts
│   │   └── google-services.json.example
│   ├── gradle/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
└── backend/                 # Component B: Node.js & Express Verification Server
    ├── server.js            # User-Agent check & FCM dispatcher
    ├── index.html           # Modern Desktop Control Center UI
    ├── package.json
    ├── .env.example
    └── firebase-service-account.json.example
```

---

## Architecture Overview

1. **Android App (`android-app`)**:
   - Triggers persistent looping alarm audio with high-priority foreground notification and lockscreen overlay activity.
   - Screen features **no dismiss/snooze button** and ignores back gestures.
   - Listens for high-priority Firebase Cloud Messaging (FCM) data payload (`action: "STOP_ALARM"`).

2. **Web Backend (`backend`)**:
   - Express server that serves an authentication dashboard to desktop browsers.
   - Enforces a **strict User-Agent filter** that blocks mobile browsers (iOS, Android, mobile WebViews) with HTTP 403.
   - Dispatches a high-priority FCM message to the target device when the desktop "Disarm Alarm" button is clicked.

---

## Quick Start Guide

### 1. Firebase Setup (Prerequisite)
1. Go to the [Firebase Console](https://console.firebase.google.com/) and create a project (e.g. `wakey-alarm`).
2. **For Android**:
   - Add an Android App with package name `com.example.pcalarm`.
   - Download `google-services.json` and place it in `android-app/app/google-services.json`.
3. **For Backend**:
   - Go to **Project Settings** > **Service accounts**.
   - Click **Generate new private key**.
   - Save the downloaded JSON file as `backend/firebase-service-account.json`.

---

### 2. Backend Setup & Run
```bash
cd backend
npm install
npm start
```
The server will start on `http://localhost:3000`.

---

### 3. Android Setup & Run
1. Open the `android-app` folder in Android Studio or Antigravity IDE.
2. Build and install the app on your physical Android device.
3. Open the app and grant required permissions:
   - **Post Notifications**
   - **Ignore Battery Optimizations** (so FCM reaches the phone in deep sleep)
   - **Exact Alarms**
4. Copy the **FCM Device Token** displayed on the phone's screen and paste it into `backend/.env` or directly into the desktop web dashboard at `http://localhost:3000`.
