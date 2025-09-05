package com.example.reminderapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import com.example.reminderapp.databinding.ActivitySettingsBinding
import com.google.firebase.auth.FirebaseAuth
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.TimeUnit
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.widget.Switch
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class Settings : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settingsManager = SettingsManager(this)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        loadSavedSettings()
        setupNotificationAlarm()
        setupNotificationRingtone()
        setupSnoozeReminder()
        setupEmailNotification()
        setupThemeSwitch()
        setupAutoDelete()
        setupFirebaseSync()
        setupClearData()
    }

    private fun loadSavedSettings() {
        // Notification Settings
        binding.alarmSwitch.isChecked = settingsManager.alarmEnabled
        binding.alarmValue.text = if (settingsManager.alarmEnabled)
            settingsManager.getAlarmName(this)
        else
            "Off"
        binding.ringtoneValue.text = settingsManager.getRingtoneName(this)
        binding.snoozeSwitch.isChecked = settingsManager.snoozeEnabled
        binding.snoozeValue.text = if (settingsManager.snoozeEnabled) "${settingsManager.snoozeDuration} min" else "Off"
        binding.emailSwitch.isChecked = settingsManager.emailNotificationsEnabled
        binding.emailValue.text = if (settingsManager.emailNotificationsEnabled) "On" else "Off"

        // Appearance
        binding.themeSwitch.isChecked = settingsManager.darkThemeEnabled
        binding.themeValue.text = if (settingsManager.darkThemeEnabled) "Dark Mode" else "Light Mode"

        // Data & Backup
        binding.autoDeleteSwitch.isChecked = settingsManager.autoDeleteEnabled
        binding.autoDeleteValue.text = if (settingsManager.autoDeleteEnabled) "On" else "Off"
        binding.syncSwitch.isChecked = settingsManager.firebaseSyncEnabled
        binding.syncValue.text = if (settingsManager.firebaseSyncEnabled) "On" else "Off"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupNotificationAlarm() {
        binding.alarmSwitch.isChecked = settingsManager.alarmEnabled
        binding.alarmValue.text = if (settingsManager.alarmEnabled)
            settingsManager.getAlarmName(this)
        else
            "Off"

        binding.alarmSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.alarmEnabled = isChecked
            if (isChecked) {
                showAlarmPicker()
            } else {
                binding.alarmValue.text = "Off"
            }
        }
    }

    private fun showAlarmPicker() {
        val currentAlarm = settingsManager.alarmRingtone?.let { Uri.parse(it) }
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentAlarm)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        }
        startActivityForResult(intent, ALARM_PICKER_REQUEST)
    }

    private fun setupNotificationRingtone() {
        binding.ringtoneLayout.setOnClickListener {
            val currentTone = settingsManager.notificationRingtone?.let { Uri.parse(it) }
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Ringtone")
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentTone)
            }
            startActivityForResult(intent, RINGTONE_PICKER_REQUEST)
        }
    }

    private fun setupSnoozeReminder() {
        binding.snoozeSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.snoozeEnabled = isChecked
            if (isChecked) {
                showSnoozeDurationDialog()
            } else {
                binding.snoozeValue.text = "Off"
            }
        }
    }

    private fun showSnoozeDurationDialog() {
        val options = arrayOf("5", "10", "15", "30")
        AlertDialog.Builder(this)
            .setTitle("Select Snooze Duration (minutes)")
            .setSingleChoiceItems(options, options.indexOf(settingsManager.snoozeDuration.toString())) { dialog, which ->
                settingsManager.snoozeDuration = options[which].toInt()
                binding.snoozeValue.text = "${options[which]} min"
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                binding.snoozeSwitch.isChecked = false
                dialog.dismiss()
            }
            .show()
    }

    private fun setupEmailNotification() {
        binding.emailSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.emailNotificationsEnabled = isChecked
            if (isChecked) {
                // Get the current user's email
                val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

                MaterialAlertDialogBuilder(this)
                    .setTitle("Email Notifications")
                    .setMessage("The app will send email notifications to $currentUserEmail when reminders are due.\n\n" +
                            "Note: Emails will be sent from the app's email service. Make sure to check your spam folder if you don't see notifications.")
                    .setPositiveButton("OK", null)
                    .show()
                binding.emailValue.text = "On"
            } else {
                binding.emailValue.text = "Off"
            }
        }
    }

    private fun setupThemeSwitch() {
        binding.themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.darkThemeEnabled = isChecked
            binding.themeValue.text = if (isChecked) "Dark Mode" else "Light Mode"
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            recreate()
        }
    }

    private fun setupAutoDelete() {
        binding.autoDeleteSwitch.isChecked = settingsManager.autoDeleteEnabled
        binding.autoDeleteValue.text = if (settingsManager.autoDeleteEnabled) "On" else "Off"

        binding.autoDeleteSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Show confirmation dialog
                MaterialAlertDialogBuilder(this)
                    .setTitle("Auto-Delete Enabled")
                    .setMessage("Passed reminders will be automatically deleted 24 hours after their due date. Continue?")
                    .setPositiveButton("Enable") { _, _ ->
                        settingsManager.autoDeleteEnabled = true
                        binding.autoDeleteValue.text = "On"
                        scheduleAutoDeleteWorker()
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        binding.autoDeleteSwitch.isChecked = false
                    }
                    .show()
            } else {
                settingsManager.autoDeleteEnabled = false
                binding.autoDeleteValue.text = "Off"
                cancelAutoDeleteWorker()
            }
        }
    }

    private fun scheduleAutoDeleteWorker() {
        val workRequest = PeriodicWorkRequestBuilder<AutoDeleteWorker>(
            1, TimeUnit.DAYS // Run daily
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "autoDeleteWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun cancelAutoDeleteWorker() {
        WorkManager.getInstance(this).cancelUniqueWork("autoDeleteWork")
    }

    private fun setupFirebaseSync() {
        binding.syncSwitch.isChecked = settingsManager.firebaseSyncEnabled
        binding.syncValue.text = if (settingsManager.firebaseSyncEnabled) "On" else "Off"

        binding.syncSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.firebaseSyncEnabled = isChecked
            binding.syncValue.text = if (isChecked) "On" else "Off"

            if (isChecked) syncWithFirebase()
        }
    }


    private fun syncWithFirebase() {
        Toast.makeText(this, "Syncing with Firebase...", Toast.LENGTH_SHORT).show()
    }

    private fun setupClearData() {
        binding.clearDataLayout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear All Data")
                .setMessage("Are you sure you want to delete all reminders? This action cannot be undone.")
                .setPositiveButton("Clear") { _, _ ->
                    clearAllData()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun clearAllData() {
        ReminderRepository().deleteAllReminders(
            FirebaseAuth.getInstance().currentUser?.uid ?: return
        ) { success ->
            if (success) {
                Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ALARM_PICKER_REQUEST -> {
                if (resultCode == RESULT_OK) {
                    val alarmUri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                    settingsManager.alarmRingtone = alarmUri?.toString()

                    binding.alarmValue.text = alarmUri?.let {
                        RingtoneManager.getRingtone(this, it).getTitle(this)
                    } ?: "Default alarm"
                } else {
                    binding.alarmSwitch.isChecked = false
                    settingsManager.alarmEnabled = false
                }
            }
            RINGTONE_PICKER_REQUEST -> {
                val ringtoneUri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                settingsManager.notificationRingtone = ringtoneUri?.toString()
                binding.ringtoneValue.text = ringtoneUri?.let {
                    RingtoneManager.getRingtone(this, it).getTitle(this)
                } ?: "System default"
            }
        }
    }

    private fun saveRingtoneUri(uriString: String) {
        getSharedPreferences("app_settings", MODE_PRIVATE)
            .edit()
            .putString("notification_ringtone", uriString)
            .apply()
    }

    companion object {
        private const val RINGTONE_PICKER_REQUEST = 1
        private const val ALARM_PICKER_REQUEST = 2
    }
}
