# PC Alarm Web Backend Server

A Node.js & Express service that intercepts disarm requests, verifies physical Desktop/PC User-Agent headers, and dispatches high-priority Firebase Cloud Messaging (FCM) commands to silence the Android alarm.

## Features
- **User-Agent Desktop Gate**: Rejects all mobile browsers (iOS, Android, etc.) with a 403 status to prevent turning off alarms from bed.
- **High-Priority FCM Dispatch**: Wakes up sleeping Android devices immediately using FCM data payloads (`action: "STOP_ALARM"`).
- **Interactive Control Center**: Web UI with live connection health and in-browser FCM token management.

## Environment Variables (`.env`)

| Variable | Description | Default |
|---|---|---|
| `PORT` | Local port to listen on | `3000` |
| `FIREBASE_SERVICE_ACCOUNT_PATH` | Path to your Firebase service account JSON key | `./firebase-service-account.json` |
| `TARGET_DEVICE_TOKEN` | Android device's unique FCM registration token | `PASTE_YOUR_FCM_REGISTRATION_TOKEN_HERE` |

## Setup & Running

1. **Install Dependencies**:
   ```bash
   npm install
   ```

2. **Add Firebase Credentials**:
   - Go to [Firebase Console](https://console.firebase.google.com/) -> Project Settings -> **Service accounts**.
   - Click **Generate new private key**.
   - Download and save the JSON file as `backend/firebase-service-account.json`.

3. **Start the Server**:
   ```bash
   npm start
   # Or in development mode with auto-reload:
   npm run dev
   ```

4. **Access the Dashboard**:
   - Open `http://localhost:3000` on your desktop/laptop browser.
