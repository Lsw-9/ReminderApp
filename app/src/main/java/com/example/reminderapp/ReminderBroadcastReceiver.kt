package com.example.reminderapp

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat

class ReminderBroadcastReceiver : BroadcastReceiver() {



    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onReceive(context: Context, intent: Intent) {

        Log.d("ReminderReceiver", "Received broadcast - ID: ${intent.getIntExtra("notification_id", -1)}, " +
                "Title: ${intent.getStringExtra("title")}, " +
                "Message: ${intent.getStringExtra("message")}")

        // Stop any existing media first
        (context.applicationContext as ReminderApplication).stopMediaPlayback()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Get notification ID from intent
        val notificationId = intent.getIntExtra("notification_id", 0)
        // Create notification channel
        createNotificationChannel(context)

        // Build notification
        val notification = buildNotification(context, intent)

        // Show notification
        notificationManager.notify(notificationId, notification)

        // Store sound/vibration references
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            ringtone = playAlarmSound(context, intent)
            vibrator = startVibration(context)
        }
        // Schedule email notification if enabled
        scheduleEmailNotification(context, intent)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Delete existing channel to force update
            context.getSystemService(NotificationManager::class.java)?.deleteNotificationChannel("reminder_channel")

            val settingsManager = SettingsManager(context)

            val (soundUri, soundType) = when {
                settingsManager.alarmEnabled -> {
                    if (!settingsManager.alarmRingtone.isNullOrEmpty()) {
                        Uri.parse(settingsManager.alarmRingtone) to RingtoneManager.TYPE_ALARM
                    } else {
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) to RingtoneManager.TYPE_ALARM
                    }
                }
                else -> {
                    if (!settingsManager.notificationRingtone.isNullOrEmpty()) {
                        Uri.parse(settingsManager.notificationRingtone) to RingtoneManager.TYPE_NOTIFICATION
                    } else {
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) to RingtoneManager.TYPE_NOTIFICATION
                    }
                }
            }
            Log.d("ReminderReceiver", "Sound URI: $soundUri")

            val channel = NotificationChannel(
                "reminder_channel",
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for reminder notifications"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(settingsManager.alarmEnabled)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(soundUri, Notification.AUDIO_ATTRIBUTES_DEFAULT)
                if (soundType != null) {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(context: Context, intent: Intent): Notification {
        val settingsManager = SettingsManager(context)

        // Get the title and description from the intent
        val title = intent.getStringExtra("title") ?: "Reminder"
        val description = intent.getStringExtra("message") ?: ""

        // Create the notification builder
        val builder = NotificationCompat.Builder(context, "reminder_channel")
            .setSmallIcon(R.drawable.baseline_notifications_24)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(if (settingsManager.alarmEnabled) Notification.DEFAULT_ALL
            else Notification.DEFAULT_LIGHTS or Notification.DEFAULT_VIBRATE)
            .setSound(if (settingsManager.alarmEnabled) {
                settingsManager.alarmRingtone?.let { Uri.parse(it) }
            } else {
                settingsManager.notificationRingtone?.let { Uri.parse(it) }
            })
            .setAutoCancel(true)
            .setFullScreenIntent(getFullScreenPendingIntent(context, intent), true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setTimeoutAfter(30000) // Auto-dismiss after 30 seconds

        if (description.isNotEmpty()) {
            builder.setContentText(description)
        }

        if (settingsManager.snoozeEnabled) {
            builder.addAction(
                R.drawable.ic_snooze,
                "Snooze",
                getSnoozePendingIntent(context, intent)
            )
        }

        builder.addAction(
            R.drawable.ic_done,
            "Mark Done",
            getDonePendingIntent(context, intent)
        )

        return builder.build()
    }

    private fun playAlarmSound(context: Context, intent: Intent): Ringtone? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            Log.d("ReminderReceiver", "Device is in silent mode, not playing sound")
            return null
        }

        val settingsManager = SettingsManager(context)
        val soundUri = when {
            settingsManager.alarmEnabled && !settingsManager.alarmRingtone.isNullOrEmpty() ->
                Uri.parse(settingsManager.alarmRingtone)
            !settingsManager.alarmEnabled && !settingsManager.notificationRingtone.isNullOrEmpty() ->
                Uri.parse(settingsManager.notificationRingtone)
            else -> RingtoneManager.getDefaultUri(
                if (settingsManager.alarmEnabled) RingtoneManager.TYPE_ALARM
                else RingtoneManager.TYPE_NOTIFICATION
            )
        }

        val ringtone = try {
            RingtoneManager.getRingtone(context, soundUri)
        } catch (e: Exception) {
            Log.e("ReminderReceiver", "Error playing ringtone: ${e.message}")
            RingtoneManager.getRingtone(context,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )
        }
        ringtone?.play()

        ReminderApplication.currentRingtone = ringtone

        // Stop after 30 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            ringtone?.stop()
            ReminderApplication.currentRingtone = null
        }, 30000)
        return ringtone
    }


    private fun startVibration(context: Context): Vibrator {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            val vibrationPattern = longArrayOf(0, 1000, 1000) // Wait 0, vibrate 1000, sleep 1000
            vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, 0))
            ReminderApplication.currentVibrator = vibrator
        }
        return vibrator
    }

    private fun getSnoozePendingIntent(context: Context, intent: Intent): PendingIntent {
        Log.d("ReminderReceiver", "Creating snooze PendingIntent for ID: ${intent.getIntExtra("notification_id", -1)}")
        val snoozeIntent = Intent(context, SnoozeActionReceiver::class.java).apply {
            putExtra("notification_id", intent.getIntExtra("notification_id", 0))
            putExtra("reminder_id", intent.getStringExtra("reminder_id"))
            putExtra("original_time", intent.getLongExtra("original_time", 0L))
            action = "SNOOZE_NOTIFICATION_${intent.getStringExtra("reminder_id")}"
        }
        return PendingIntent.getBroadcast(
            context,
            System.currentTimeMillis().toInt(),
            snoozeIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getDonePendingIntent(context: Context, intent: Intent): PendingIntent {
        Log.d("ReminderReceiver", "Creating done PendingIntent for ID: ${intent.getIntExtra("notification_id", -1)}")
        val doneIntent = Intent(context, DoneActionReceiver::class.java).apply {
            putExtra("notification_id", intent.getIntExtra("notification_id", 0))
            putExtra("reminder_id", intent.getStringExtra("reminder_id"))
            action = "DONE_NOTIFICATION_${intent.getStringExtra("reminder_id")}"
        }
        return PendingIntent.getBroadcast(
            context,
            System.currentTimeMillis().toInt(),
            doneIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getFullScreenPendingIntent(context: Context, intent: Intent): PendingIntent {
        val fullScreenIntent = Intent(context, ReminderAlertActivity::class.java).apply {
            putExtras(intent.extras ?: Bundle())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @SuppressLint("SuspiciousIndentation")
    private fun scheduleEmailNotification(context: Context, intent: Intent) {
        try {
            val settingsManager = SettingsManager(context)
            if (!settingsManager.emailNotificationsEnabled) {
                Log.d("ReminderReceiver", "Email notifications are disabled")
                return
            }

            // Get network status
            val isOfflineMode = com.example.reminderapp.NetworkUtils.shouldUseOfflineMode(context)
            if (isOfflineMode) {
                Log.d("ReminderReceiver", "Device is offline, email will be scheduled but sent when online")
            }

            val reminderId = intent.getStringExtra("reminder_id") ?: return
            Log.d("ReminderReceiver", "Scheduling email notification for reminder: $reminderId")

        // Create input data for the worker
        val inputData = androidx.work.Data.Builder()
            .putString("reminder_id", reminderId)
            .build()

        // Create a one-time work request
        val emailWorkRequest = androidx.work.OneTimeWorkRequestBuilder<EmailNotificationWorker>()
            .setInputData(inputData)
            .build()

            // Enqueue the work request - WorkManager handles offline scenarios automatically
            androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "email_notification_$reminderId",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    emailWorkRequest
                )

            Log.d("ReminderReceiver", "Email notification work request enqueued successfully")
        } catch (e: Exception) {
            // Catch any exceptions to prevent crashing the notification process
            Log.e("ReminderReceiver", "Error scheduling email: ${e.message}", e)
        }
    }
    }
