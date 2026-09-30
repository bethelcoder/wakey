package com.example.wakey

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "FCMService"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New Firebase Cloud Messaging Token refreshed: $token")
        
        // Cache token locally
        val prefs = getSharedPreferences("WakeyAlarmPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("FCM_TOKEN", token).apply()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")
        Log.d(TAG, "FCM Payload data: ${remoteMessage.data}")

        val action = remoteMessage.data["action"]
        if (action == "STOP_ALARM") {
            Log.d(TAG, "STOP_ALARM action received from PC! Dispatching kill intent to service.")
            val stopIntent = Intent(this, AlarmRingerService::class.java).apply {
                this.action = "ACTION_STOP_ALARM"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(stopIntent)
            } else {
                startService(stopIntent)
            }
        }
    }
}
