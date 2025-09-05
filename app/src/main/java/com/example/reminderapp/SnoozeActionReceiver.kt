package com.example.reminderapp

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.util.UUID

class SnoozeActionReceiver : BroadcastReceiver() {
    private var isProcessing = false
    override fun onReceive(context: Context, intent: Intent) {

        if (isProcessing) {
            Log.d("SnoozeAction", "Already processing a snooze request")
            return
        }
        isProcessing = true

        val reminderId = intent.getStringExtra("reminder_id") ?: run {
            Log.e("SnoozeAction", "Missing reminder ID")
            isProcessing = false
            return
        }

        Log.d("SnoozeAction", "Received snooze request - " +
                "Notification ID: ${intent.getIntExtra("notification_id", -1)}, " +
                "Reminder ID: $reminderId")


        val originalTime = intent.getLongExtra("original_time", 0L)
        val notificationId = intent.getIntExtra("notification_id", 0)

        val settingsManager = SettingsManager(context)

        val snoozeMinutes = settingsManager.snoozeDuration
        val newTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000)

        val reminderRepository = ReminderRepository()
        reminderRepository.getReminder(reminderId) { reminder ->
            reminder?.let {
                // Create new reminder with fresh ID to avoid notification conflicts
                val newId = UUID.randomUUID().toString()
                val newReminder = it.copy(
                    id = newId,
                    date = newTime,
                    isCompleted = false
                )
                reminderRepository.saveReminder(newReminder) { success ->
                    if (success) {
                        reminderRepository.scheduleNotifications(newReminder, context)
                        cancelOriginalNotification(context, notificationId)
                        showSnoozeConfirmation(context, snoozeMinutes)
                    }
                    isProcessing = false
                }
                }?: run {
                    isProcessing = false
            }
        }

        // Stop any ongoing media playback
        (context.applicationContext as? ReminderApplication)?.apply {
            stopMediaPlayback()
        }
    }

    private fun cancelOriginalNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }
    private fun showSnoozeConfirmation(context: Context, snoozeMinutes: Int) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "Snoozed for $snoozeMinutes minutes", Toast.LENGTH_SHORT).show()
        }
    }
}