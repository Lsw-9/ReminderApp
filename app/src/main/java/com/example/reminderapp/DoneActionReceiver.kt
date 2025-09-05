package com.example.reminderapp

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

class DoneActionReceiver : BroadcastReceiver() {
    private var isProcessing = false

    override fun onReceive(context: Context, intent: Intent) {

        // Prevent duplicate processing
        if (isProcessing) {
            Log.d("DoneAction", "Already processing a done request")
            return
        }
        isProcessing = true

        val reminderId = intent.getStringExtra("reminder_id") ?: run {
            Log.e("DoneAction", "Missing reminder ID")
            isProcessing = false
            return
        }

        Log.d("DoneAction", "Received done request - " +
                "Notification ID: ${intent.getIntExtra("notification_id", -1)}, " +
                "Reminder ID: $reminderId")


        val reminderRepository = ReminderRepository()
        val notificationId = intent.getIntExtra("notification_id", 0)

        // Mark reminder as completed
        reminderRepository.getReminder(reminderId) { reminder ->
            reminder?.let {
                val updatedReminder = it.copy(isCompleted = true)
                reminderRepository.saveReminder(updatedReminder) { success ->
                    if (success) {
                        cancelNotification(context, notificationId)
                        showDoneConfirmation(context)
                        refreshReminderList(context)
                    }
                    isProcessing = false
                }
            } ?: run {
                isProcessing = false
            }
        }

        // Stop any ongoing media playback
        (context.applicationContext as? ReminderApplication)?.apply {
            stopMediaPlayback()
        }
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }
    private fun showDoneConfirmation(context: Context) {

            Toast.makeText(context, "Reminder marked as done", Toast.LENGTH_SHORT).show()

    }

    private fun refreshReminderList(context: Context) {
        context.sendBroadcast(Intent("REFRESH_REMINDERS"))
    }
}