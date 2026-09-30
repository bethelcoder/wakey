package com.example.wakey

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmRingerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val CHANNEL_ID = "AlarmServiceChannel"
    private val TAG = "AlarmRingerService"
    private var currentAlarmLabel: String = "WAKE UP!"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand received action: $action")

        if (action == "ACTION_STOP_ALARM") {
            stopAlarm()
            return START_NOT_STICKY
        }

        currentAlarmLabel = intent?.getStringExtra("ALARM_LABEL") ?: "WAKE UP!"
        startAlarmPlayback()
        showFullScreenAlarm()
        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "PCAlarm::RingerWakeLock"
            ).apply {
                acquire(10 * 60 * 1000L /* 10 minutes max timeout safety */)
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun startAlarmPlayback() {
        try {
            if (mediaPlayer == null) {
                val rawId = resources.getIdentifier("heavy_alarm", "raw", packageName)
                val alarmUri: Uri = if (rawId != 0) {
                    Uri.parse("android.resource://$packageName/$rawId")
                } else {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
                Log.d(TAG, "Alarm playback started successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting media player, falling back to default alarm ringtone", e)
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, fallbackUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Fallback alarm ringtone also failed", fallbackError)
            }
        }
    }

    private fun showFullScreenAlarm() {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ALARM_LABEL", currentAlarmLabel)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm_bell)
            .setContentTitle(currentAlarmLabel)
            .setContentText("Go to your desktop laptop browser to disable this alarm.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)

        try {
            startActivity(fullScreenIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to direct startActivity, relying on fullScreenIntent", e)
        }
    }

    private fun stopAlarm() {
        Log.d(TAG, "Stopping alarm audio and dismissing overlay")
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media player", e)
        } finally {
            mediaPlayer = null
        }

        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)

        val closeIntent = Intent("CLOSE_ALARM_ACTIVITY").apply {
            setPackage(packageName)
        }
        sendBroadcast(closeIntent)

        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Activation Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority channel for anti-snooze alarm ringing"
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
