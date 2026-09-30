package com.example.wakey

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class AlarmActivity : AppCompatActivity() {

    private lateinit var ivAlarmIcon: ImageView
    private lateinit var tvAlarmOverlayLabel: TextView

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)

        ivAlarmIcon = findViewById(R.id.ivAlarmIcon)
        tvAlarmOverlayLabel = findViewById(R.id.tvAlarmOverlayLabel)

        val label = intent?.getStringExtra("ALARM_LABEL") ?: "ACTIVE ALARM"
        tvAlarmOverlayLabel.text = label.uppercase()

        setupLockscreenFlags()
        setupPulseAnimation()

        // Prevent Back navigation from dismissing alarm
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing: intentionally prevent dismissing from phone
            }
        })

        // Register local broadcast receiver to close activity when PC disarm payload arrives
        val filter = IntentFilter("CLOSE_ALARM_ACTIVITY")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(this, closeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeReceiver, filter)
        }
    }

    private fun setupLockscreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupPulseAnimation() {
        val anim = AlphaAnimation(0.3f, 1.0f).apply {
            duration = 500
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        ivAlarmIcon.startAnimation(anim)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Intercept volume buttons to prevent snoozing/muting
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(closeReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intercept legacy back button
    }
}
