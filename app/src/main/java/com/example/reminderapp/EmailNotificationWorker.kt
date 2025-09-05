package com.example.reminderapp

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.reminderapp.SettingsManager
import com.example.reminderapp.Reminder
import com.example.reminderapp.EmailSender
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class EmailNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "EmailNotificationWorker"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val settingsManager = SettingsManager(context)

    override suspend fun doWork(): Result {
        try {
            // Check if email notifications are enabled
            if (!settingsManager.emailNotificationsEnabled) {
                Log.d(TAG, "Email notifications are disabled")
                return Result.success()
            }

            // Get the reminder ID from input data
            val reminderId = inputData.getString("reminder_id") ?: return Result.failure()
            Log.d(TAG, "Processing email notification for reminder: $reminderId")

            // Get the current user
            val currentUser = auth.currentUser ?: return Result.failure()
            val userEmail = currentUser.email ?: return Result.failure()

            // Get the reminder from Firestore
            val reminderDoc = db.collection("reminders").document(reminderId).get().await()
            val reminder = reminderDoc.toObject(Reminder::class.java) ?: return Result.failure()

            // Send the email
            val success = EmailSender.sendReminderEmail(applicationContext, reminder, userEmail)

            return if (success) {
                Log.d(TAG, "Email notification sent successfully")
                Result.success()
            } else {
                Log.e(TAG, "Failed to send email notification")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending email notification", e)
            return Result.failure()
        }
    }
}