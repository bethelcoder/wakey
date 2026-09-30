package com.example.wakey

import android.Manifest
import android.app.AlarmManager
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.messaging.FirebaseMessaging
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // Views
    private lateinit var viewHome: LinearLayout
    private lateinit var viewAlarms: LinearLayout
    private lateinit var viewSettings: LinearLayout

    // Nav elements
    private lateinit var tabHome: LinearLayout
    private lateinit var tabAlarms: LinearLayout
    private lateinit var tabSettings: LinearLayout
    private lateinit var ivNavHome: ImageView
    private lateinit var ivNavAlarms: ImageView
    private lateinit var ivNavSettings: ImageView
    private lateinit var tvNavHome: TextView
    private lateinit var tvNavAlarms: TextView
    private lateinit var tvNavSettings: TextView
    private lateinit var fabAddAlarm: FloatingActionButton
    private lateinit var btnQuickSettings: ImageView

    // Hero Card Views
    private lateinit var cardHeroNextAlarm: LinearLayout
    private lateinit var tvHeroAlarmLabel: TextView
    private lateinit var tvHeroAlarmTime: TextView
    private lateinit var tvHeroAlarmDate: TextView
    private lateinit var tvHeroSoundName: TextView
    private lateinit var btnEditHeroAlarm: ImageView

    // Alarms List Views
    private lateinit var rvAlarms: RecyclerView
    private lateinit var tvActiveAlarmsCount: TextView
    private lateinit var tvEmptyAlarms: TextView
    private lateinit var alarmAdapter: AlarmAdapter

    // Settings Tab Views
    private lateinit var tvFcmToken: TextView
    private lateinit var btnCopyToken: Button
    private lateinit var btnTestAlarmNow: Button
    private lateinit var btnPermissions: Button

    private lateinit var alarmManager: AlarmManager
    private val TAG = "MainActivity"

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission is required for alarm display", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        bindViews()
        setupBottomNavigation()
        setupAlarmsRecyclerView()
        setupFcmToken()
        setupSettingsTab()
        setupHeroCard()

        // Check and auto-schedule all active alarms
        AlarmRepository.scheduleAllActiveAlarms(this)
        refreshUI()
        checkRequiredPermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    private fun bindViews() {
        viewHome = findViewById(R.id.viewHome)
        viewAlarms = findViewById(R.id.viewAlarms)
        viewSettings = findViewById(R.id.viewSettings)

        tabHome = findViewById(R.id.tabHome)
        tabAlarms = findViewById(R.id.tabAlarms)
        tabSettings = findViewById(R.id.tabSettings)

        ivNavHome = findViewById(R.id.ivNavHome)
        ivNavAlarms = findViewById(R.id.ivNavAlarms)
        ivNavSettings = findViewById(R.id.ivNavSettings)

        tvNavHome = findViewById(R.id.tvNavHome)
        tvNavAlarms = findViewById(R.id.tvNavAlarms)
        tvNavSettings = findViewById(R.id.tvNavSettings)

        fabAddAlarm = findViewById(R.id.fabAddAlarm)
        btnQuickSettings = findViewById(R.id.btnQuickSettings)

        cardHeroNextAlarm = findViewById(R.id.cardHeroNextAlarm)
        tvHeroAlarmLabel = findViewById(R.id.tvHeroAlarmLabel)
        tvHeroAlarmTime = findViewById(R.id.tvHeroAlarmTime)
        tvHeroAlarmDate = findViewById(R.id.tvHeroAlarmDate)
        tvHeroSoundName = findViewById(R.id.tvHeroSoundName)
        btnEditHeroAlarm = findViewById(R.id.btnEditHeroAlarm)

        rvAlarms = findViewById(R.id.rvAlarms)
        tvActiveAlarmsCount = findViewById(R.id.tvActiveAlarmsCount)
        tvEmptyAlarms = findViewById(R.id.tvEmptyAlarms)

        tvFcmToken = findViewById(R.id.tvFcmToken)
        btnCopyToken = findViewById(R.id.btnCopyToken)
        btnTestAlarmNow = findViewById(R.id.btnTestAlarmNow)
        btnPermissions = findViewById(R.id.btnPermissions)
    }

    private fun setupBottomNavigation() {
        tabHome.setOnClickListener { switchTab(0) }
        tabAlarms.setOnClickListener { switchTab(1) }
        tabSettings.setOnClickListener { switchTab(2) }
        btnQuickSettings.setOnClickListener { switchTab(2) }

        fabAddAlarm.setOnClickListener {
            showAddOrEditAlarmDialog(null)
        }
    }

    private fun switchTab(tabIndex: Int) {
        val colorActive = ContextCompat.getColor(this, R.color.brand_orange)
        val colorInactive = ContextCompat.getColor(this, R.color.text_muted)

        viewHome.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        viewAlarms.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        viewSettings.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE

        // Tab 0: Home
        ivNavHome.setColorFilter(if (tabIndex == 0) colorActive else colorInactive)
        tvNavHome.setTextColor(if (tabIndex == 0) colorActive else colorInactive)

        // Tab 1: Alarms
        ivNavAlarms.setColorFilter(if (tabIndex == 1) colorActive else colorInactive)
        tvNavAlarms.setTextColor(if (tabIndex == 1) colorActive else colorInactive)

        // Tab 2: Settings
        ivNavSettings.setColorFilter(if (tabIndex == 2) colorActive else colorInactive)
        tvNavSettings.setTextColor(if (tabIndex == 2) colorActive else colorInactive)
    }

    private fun setupAlarmsRecyclerView() {
        val alarms = AlarmRepository.getAlarms(this)
        alarmAdapter = AlarmAdapter(
            context = this,
            alarms = alarms,
            onToggle = { alarm, isEnabled ->
                AlarmRepository.toggleAlarm(this, alarm.id, isEnabled)
                refreshUI()
            },
            onItemClick = { alarm ->
                showAddOrEditAlarmDialog(alarm)
            },
            onItemLongClick = { alarm ->
                showDeleteConfirmDialog(alarm)
            }
        )

        rvAlarms.layoutManager = LinearLayoutManager(this)
        rvAlarms.adapter = alarmAdapter
    }

    private fun setupHeroCard() {
        btnEditHeroAlarm.setOnClickListener {
            val nextAlarm = AlarmRepository.getNextUpcomingAlarm(this)
            if (nextAlarm != null) {
                showAddOrEditAlarmDialog(nextAlarm)
            } else {
                showAddOrEditAlarmDialog(null)
            }
        }
    }

    private fun setupSettingsTab() {
        btnCopyToken.setOnClickListener {
            val token = tvFcmToken.text.toString()
            if (token.isNotEmpty() && !token.startsWith("Fetching") && !token.startsWith("Error")) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("FCM Token", token)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "FCM Token copied! Paste in desktop browser.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Token not ready yet. Please wait...", Toast.LENGTH_SHORT).show()
            }
        }

        btnTestAlarmNow.setOnClickListener {
            Toast.makeText(this, "Triggering alarm immediately...", Toast.LENGTH_SHORT).show()
            val serviceIntent = Intent(this, AlarmRingerService::class.java).apply {
                putExtra("ALARM_LABEL", "Test Alarm")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        btnPermissions.setOnClickListener {
            checkRequiredPermissions()
            Toast.makeText(this, "Permissions checked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                tvFcmToken.text = "Error fetching FCM token: ${task.exception?.message}"
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "Device FCM Token: $token")
            tvFcmToken.text = token

            // Cache token locally
            val prefs = getSharedPreferences("WakeyAlarmPrefs", Context.MODE_PRIVATE)
            prefs.edit().putString("FCM_TOKEN", token).apply()
        }
    }

    private fun refreshUI() {
        val alarms = AlarmRepository.getAlarms(this)
        alarmAdapter.updateData(alarms)

        val activeCount = alarms.count { it.isEnabled }
        tvActiveAlarmsCount.text = "$activeCount Active"
        tvEmptyAlarms.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE

        // Update Hero Card
        val nextAlarm = AlarmRepository.getNextUpcomingAlarm(this)
        if (nextAlarm != null) {
            val triggerMillis = nextAlarm.getNextTriggerMillis()
            val cal = Calendar.getInstance().apply { timeInMillis = triggerMillis }

            tvHeroAlarmLabel.text = nextAlarm.label
            tvHeroAlarmTime.text = nextAlarm.getFormattedTime()
            tvHeroSoundName.text = nextAlarm.sound

            val now = Calendar.getInstance()
            val isToday = cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
                    cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)

            val nowPlusOne = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val isTomorrow = cal.get(Calendar.DAY_OF_YEAR) == nowPlusOne.get(Calendar.DAY_OF_YEAR) &&
                    cal.get(Calendar.YEAR) == nowPlusOne.get(Calendar.YEAR)

            val dayFormatter = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
            val dateStr = dayFormatter.format(cal.time).uppercase()

            tvHeroAlarmDate.text = when {
                isToday -> "TODAY, $dateStr"
                isTomorrow -> "TOMORROW, $dateStr"
                else -> dateStr
            }
        } else {
            tvHeroAlarmLabel.text = "No Active Alarms"
            tvHeroAlarmTime.text = "--:--"
            tvHeroAlarmDate.text = "TAP '+' BELOW TO SCHEDULE AN ALARM"
            tvHeroSoundName.text = "Heavy Alarm"
        }
    }

    private fun showAddOrEditAlarmDialog(existingAlarm: AlarmModel?) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_add_alarm)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val tvTitle = dialog.findViewById<TextView>(R.id.tvDialogTitle)
        val timePicker = dialog.findViewById<TimePicker>(R.id.dialogTimePicker)
        val etLabel = dialog.findViewById<EditText>(R.id.etAlarmLabel)

        val btnMon = dialog.findViewById<TextView>(R.id.btnDayMon)
        val btnTue = dialog.findViewById<TextView>(R.id.btnDayTue)
        val btnWed = dialog.findViewById<TextView>(R.id.btnDayWed)
        val btnThu = dialog.findViewById<TextView>(R.id.btnDayThu)
        val btnFri = dialog.findViewById<TextView>(R.id.btnDayFri)
        val btnSat = dialog.findViewById<TextView>(R.id.btnDaySat)
        val btnSun = dialog.findViewById<TextView>(R.id.btnDaySun)

        val btnPresetWeekdays = dialog.findViewById<TextView>(R.id.btnPresetWeekdays)
        val btnPresetWeekends = dialog.findViewById<TextView>(R.id.btnPresetWeekends)
        val btnPresetEveryday = dialog.findViewById<TextView>(R.id.btnPresetEveryday)

        val btnCancel = dialog.findViewById<Button>(R.id.btnDialogCancel)
        val btnSave = dialog.findViewById<Button>(R.id.btnDialogSave)
        val btnDelete = dialog.findViewById<Button>(R.id.btnDialogDelete)

        val isEditMode = existingAlarm != null
        tvTitle.text = if (isEditMode) "Edit Alarm" else "Set New Alarm"

        val selectedDays = mutableSetOf<Int>()
        if (isEditMode) {
            existingAlarm?.let {
                timePicker.hour = it.hour
                timePicker.minute = it.minute
                etLabel.setText(it.label)
                selectedDays.addAll(it.days)
                btnDelete.visibility = View.VISIBLE
            }
        } else {
            val now = Calendar.getInstance()
            timePicker.hour = 6
            timePicker.minute = 30
            etLabel.setText("Morning Routine")
            selectedDays.addAll(listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY))
        }

        fun updateDialogDayChips() {
            fun updateChip(btn: TextView, day: Int) {
                val active = selectedDays.contains(day)
                btn.setBackgroundResource(if (active) R.drawable.bg_day_chip_active else R.drawable.bg_day_chip_inactive)
                btn.setTextColor(
                    ContextCompat.getColor(
                        this,
                        if (active) R.color.day_chip_active_text else R.color.day_chip_inactive_text
                    )
                )
            }
            updateChip(btnMon, Calendar.MONDAY)
            updateChip(btnTue, Calendar.TUESDAY)
            updateChip(btnWed, Calendar.WEDNESDAY)
            updateChip(btnThu, Calendar.THURSDAY)
            updateChip(btnFri, Calendar.FRIDAY)
            updateChip(btnSat, Calendar.SATURDAY)
            updateChip(btnSun, Calendar.SUNDAY)
        }

        fun toggleDay(day: Int) {
            if (selectedDays.contains(day)) {
                selectedDays.remove(day)
            } else {
                selectedDays.add(day)
            }
            updateDialogDayChips()
        }

        btnMon.setOnClickListener { toggleDay(Calendar.MONDAY) }
        btnTue.setOnClickListener { toggleDay(Calendar.TUESDAY) }
        btnWed.setOnClickListener { toggleDay(Calendar.WEDNESDAY) }
        btnThu.setOnClickListener { toggleDay(Calendar.THURSDAY) }
        btnFri.setOnClickListener { toggleDay(Calendar.FRIDAY) }
        btnSat.setOnClickListener { toggleDay(Calendar.SATURDAY) }
        btnSun.setOnClickListener { toggleDay(Calendar.SUNDAY) }

        btnPresetWeekdays.setOnClickListener {
            selectedDays.clear()
            selectedDays.addAll(listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY))
            updateDialogDayChips()
        }

        btnPresetWeekends.setOnClickListener {
            selectedDays.clear()
            selectedDays.addAll(listOf(Calendar.SATURDAY, Calendar.SUNDAY))
            updateDialogDayChips()
        }

        btnPresetEveryday.setOnClickListener {
            selectedDays.clear()
            selectedDays.addAll(listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY))
            updateDialogDayChips()
        }

        updateDialogDayChips()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            existingAlarm?.let {
                AlarmRepository.deleteAlarm(this, it.id)
                refreshUI()
                Toast.makeText(this, "Alarm deleted", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val labelText = etLabel.text.toString().trim().ifEmpty { "Morning Alarm" }
            val hour = timePicker.hour
            val minute = timePicker.minute

            val alarmToSave = existingAlarm?.copy(
                hour = hour,
                minute = minute,
                label = labelText,
                isEnabled = true,
                days = selectedDays.toMutableSet()
            ) ?: AlarmModel(
                hour = hour,
                minute = minute,
                label = labelText,
                isEnabled = true,
                days = selectedDays.toMutableSet(),
                mission = "PC Desktop Verification",
                sound = "Heavy Alarm"
            )

            AlarmRepository.addOrUpdateAlarm(this, alarmToSave)
            refreshUI()
            Toast.makeText(this, "Alarm '${alarmToSave.label}' saved!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteConfirmDialog(alarm: AlarmModel) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Alarm")
            .setMessage("Do you want to delete '${alarm.label}' (${alarm.getFormattedTime()} ${alarm.getAmPm()})?")
            .setPositiveButton("Delete") { _, _ ->
                AlarmRepository.deleteAlarm(this, alarm.id)
                refreshUI()
                Toast.makeText(this, "Alarm deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun checkRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot launch battery optimization intent", e)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
}
