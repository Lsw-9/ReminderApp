package com.example.reminderapp

import android.graphics.Color
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

class ReminderRepository {
    private val db = FirebaseFirestore.getInstance()
    private val remindersCollection = db.collection("reminders")
    private var remindersListener: ListenerRegistration? = null

    // Add caching to prevent redundant fetches
    private val reminderCache = ConcurrentHashMap<String, List<Reminder>>()
    private val lastUpdateTime = ConcurrentHashMap<String, Long>()
    private val CACHE_EXPIRY_MS = 30000 // 30 seconds cache expiry

    // Batch operation tracker
    private var pendingOperations = 0
    private var batchUpdateInProgress = false

    fun saveReminder(reminder: Reminder, onComplete: (Boolean) -> Unit) {
        // Convert reminder to Map to ensure proper data types
        val reminderMap = hashMapOf(
            "id" to reminder.id,
            "userId" to reminder.userId,
            "title" to reminder.title,
            "description" to reminder.description,
            "date" to reminder.date,
            "emoji" to reminder.emoji,
            "color" to reminder.color,
            "isCompleted" to reminder.isCompleted,
            "repeatOption" to reminder.repeatOption,
            "category" to (reminder.category?.trim()?.lowercase() ?: ""),
            "offsetMinutes" to reminder.offsetMinutes
        )

        remindersCollection.document(reminder.id)
            .set(reminderMap)
            .addOnSuccessListener {
                // Invalidate the cache for this user when a reminder is updated
                reminderCache.remove(reminder.userId)
                Log.d(
                    "ReminderRepository",
                    "Reminder saved successfully with category: '${reminderMap["category"]}'"
                )
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e("ReminderRepository", "Error saving reminder", e)
                onComplete(false)
            }
    }

    fun getAllUserReminders(userId: String, callback: (List<Reminder>) -> Unit) {
        // Check cache first
        val currentTime = System.currentTimeMillis()
        val cachedValue = reminderCache[userId]
        val lastUpdate = lastUpdateTime[userId] ?: 0L

        if (cachedValue != null && (currentTime - lastUpdate < CACHE_EXPIRY_MS)) {
            Log.d("ReminderRepository", "Using cached reminders for user $userId (${cachedValue.size} reminders)")
            callback(cachedValue)
            return
        }


        if (remindersListener != null) {
            if (cachedValue != null) {
                callback(cachedValue)
            }
            return
        }
        remindersListener = remindersCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ReminderRepository", "Listen failed.", e)
                    callback(emptyList())
                    return@addSnapshotListener
                }

                val validReminders = mutableListOf<Reminder>()
                val invalidDocIds = mutableListOf<String>()

                snapshot?.documents?.forEach { doc ->
                    try {
                        val data = doc.data
                        if (data != null) {
                            val date = when (val dateValue = data["date"]) {
                                is Long -> dateValue
                                is String -> dateValue.toLongOrNull()
                                is Number -> dateValue.toLong()
                                else -> null
                            }

                            if (data["category"] != null) {
                                //Log.d("ReminderRepository", "Reminder retrieved with category: ${data["category"]}")
                            }

                            if (date != null) {
                                // Valid reminder
                                data["date"] = date
                                // Only update if type is wrong to reduce operations
                                if (data["date"] !is Long) {
                                    if (!batchUpdateInProgress) {
                                        doc.reference.set(data)
                                    }
                                }

                                Reminder(
                                    id = data["id"] as? String ?: "",
                                    userId = data["userId"] as? String ?: "",
                                    title = data["title"] as? String ?: "",
                                    description = data["description"] as? String ?: "",
                                    category = (data["category"] as? String)?.trim()?.lowercase() ?: "",
                                    date = date,
                                    offsetMinutes = (data["offsetMinutes"] as? Number)?.toInt() ?: 10,
                                    emoji = data["emoji"] as? String ?: "⏰",
                                    color = (data["color"] as? Number)?.toInt() ?: Color.WHITE,
                                    isCompleted = data["isCompleted"] as? Boolean ?: false,
                                    repeatOption = data["repeatOption"] as? String ?: "Never"

                                ).let { validReminders.add(it) }
                            } else {
                                invalidDocIds.add(doc.id)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ReminderRepository", "Error converting document ${doc.id}", e)
                        invalidDocIds.add(doc.id)
                    }
                }

                // Cache the result
                reminderCache[userId] = validReminders
                lastUpdateTime[userId] = System.currentTimeMillis()

                if (invalidDocIds.isNotEmpty()) {
                    deleteInvalidReminders(invalidDocIds) {
                        Log.d(
                            "ReminderRepository",
                            "Cleaned up ${invalidDocIds.size} invalid reminders"
                        )
                    }
                }

                callback(validReminders)
            }
    }

    // Clear listener when not needed
    fun removeListener() {
        remindersListener?.remove()
        remindersListener = null
    }

    private fun deleteInvalidReminders(documentIds: List<String>, onComplete: () -> Unit) {
        if (documentIds.isEmpty()) {
            onComplete()
            return
        }

        batchUpdateInProgress = true
        var completedCount = 0
        val totalCount = documentIds.size

        documentIds.forEach { docId ->
            remindersCollection.document(docId)
                .delete()
                .addOnCompleteListener {
                    completedCount++
                    if (completedCount == totalCount) {
                        batchUpdateInProgress = false
                        onComplete()
                    }
                }
        }
    }

    // Specialized method to get reminders only once (not a listener)
    fun getRemindersOnce(userId: String, callback: (List<Reminder>) -> Unit) {

        val cachedValue = reminderCache[userId]
        val lastUpdate = lastUpdateTime[userId] ?: 0L
        val currentTime = System.currentTimeMillis()

        if (cachedValue != null && (currentTime - lastUpdate < CACHE_EXPIRY_MS)) {
            Log.d("ReminderRepository", "Using cached reminders for one-time fetch")
            callback(cachedValue)
            return
        }

        remindersCollection
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                val validReminders = mutableListOf<Reminder>()

                snapshot.documents.forEach { doc ->
                    try {
                        val data = doc.data
                        if (data != null) {
                            val date = when (val dateValue = data["date"]) {
                                is Long -> dateValue
                                is String -> dateValue.toLongOrNull()
                                is Number -> dateValue.toLong()
                                else -> null
                            }

                            if (date != null) {
                                Reminder(
                                    id = data["id"] as? String ?: "",
                                    userId = data["userId"] as? String ?: "",
                                    title = data["title"] as? String ?: "",
                                    description = data["description"] as? String ?: "",
                                    category = (data["category"] as? String)?.trim()?.lowercase() ?: "",
                                    date = date,
                                    offsetMinutes = (data["offsetMinutes"] as? Number)?.toInt() ?: 10,
                                    emoji = data["emoji"] as? String ?: "⏰",
                                    color = (data["color"] as? Number)?.toInt() ?: Color.WHITE,
                                    isCompleted = data["isCompleted"] as? Boolean ?: false,
                                    repeatOption = data["repeatOption"] as? String ?: "Never"
                                ).let { validReminders.add(it) }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ReminderRepository", "Error converting document", e)
                    }
                }

                reminderCache[userId] = validReminders
                lastUpdateTime[userId] = System.currentTimeMillis()
                callback(validReminders)
            }
            .addOnFailureListener { e ->
                Log.e("ReminderRepository", "Error getting reminders", e)
                callback(emptyList())
            }
    }

    fun getRemindersForDate(userId: String, date: String, onComplete: (List<Reminder>) -> Unit) {
        // Convert date string to timestamp range
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val localDate = LocalDate.parse(date, dateFormatter)
        val startOfDay =
            localDate.atStartOfDay(ZoneId.of("Asia/Kuala_Lumpur")).toInstant().toEpochMilli()
        val endOfDay =
            localDate.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kuala_Lumpur")).toInstant()
                .toEpochMilli()

        remindersCollection
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("date", startOfDay)
            .whereLessThan("date", endOfDay)
            .get()
            .addOnSuccessListener { documents ->
                val reminders = documents.mapNotNull { doc ->
                    try {
                        doc.toObject(Reminder::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
                onComplete(reminders)
            }
            .addOnFailureListener {
                onComplete(emptyList())
            }
    }


    fun deleteReminder(reminderId: String, userId: String, callback: (Boolean) -> Unit) {
        remindersCollection
            .document(reminderId)
            .delete()
            .addOnSuccessListener {
                // After successful deletion, trigger a category count update
                val categoryRepo = CategoryRepository()
                categoryRepo.getCategoriesWithCount(userId) { _ ->
                }
                callback(true)
            }
            .addOnFailureListener {
                callback(false)
            }
    }


    fun getReminder(reminderId: String, callback: (Reminder?) -> Unit) {
        remindersCollection.document(reminderId)
            .get()
            .addOnSuccessListener { document ->
                val reminder = document.toObject(Reminder::class.java)
                callback(reminder)
            }
            .addOnFailureListener {
                callback(null)
            }
    }


    fun cleanup() {
        remindersListener?.remove()
        remindersListener = null
    }

    fun scheduleNotifications(reminder: Reminder, context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = reminder.date

        // Schedule main notification
        scheduleSingleNotification(
            reminder.id.hashCode(),
            triggerTime,
            reminder.title,
            "Deadline: ${reminder.description}",
            reminder,
            context
        )

        // Schedule offset notification if needed
        if (reminder.offsetMinutes > 0) {
            val offsetTime = triggerTime - (reminder.offsetMinutes * 60 * 1000)
            scheduleSingleNotification(
                "${reminder.id}_offset".hashCode(),
                offsetTime,
                reminder.title,
                "Reminder: ${reminder.description} (${reminder.offsetMinutes} minutes before)",
                reminder,
                context
            )
        }
    }

    private fun scheduleSingleNotification(
        notificationId: Int,
        triggerTime: Long,
        title: String,
        message: String,
        reminder: Reminder,
        context: Context
    ) {
        Log.d("ReminderRepo", "Scheduling notification ID: $notificationId - $title ($message) at ${Date(triggerTime)}")

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra("title", title)
            // Only include the message if reminder description is not empty
            if (reminder.description.isNotEmpty()) {
            putExtra("message", message)
            }
            putExtra("reminder_id", reminder.id)
            putExtra("notification_id", notificationId)
            putExtra("original_time", triggerTime)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    // Check for offline mode before requesting permission
                    val isOfflineMode = com.example.reminderapp.NetworkUtils.shouldUseOfflineMode(context)

                    if (isOfflineMode) {
                        // In offline mode, just log the issue but still try to schedule
                        Log.w("ReminderRepo", "Cannot schedule exact alarms in offline mode. Notification may not work.")
                    } else {
                        // Only show the permission dialog in online mode
                        context.startActivity(Intent(ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                        return
                    }
                } catch (e: Exception) {
                    Log.e("ReminderRepo", "Error checking permissions: ${e.message}")
                }
            }
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Log.d("ReminderRepo", "Successfully scheduled notification for ${Date(triggerTime)}")
        } catch (e: Exception) {
            Log.e("ReminderRepo", "Error scheduling notification: ${e.message}")
        }
    }



fun searchReminders(userId: String, query: String, callback: (List<Reminder>) -> Unit) {
        remindersCollection
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                val reminders = documents.mapNotNull { doc ->
                    try {
                        doc.toObject(Reminder::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
                val filteredReminders = reminders.filter { reminder ->
                    reminder.title.contains(query, ignoreCase = true) ||
                            reminder.description.contains(query, ignoreCase = true)
                }
                callback(filteredReminders)
            }
            .addOnFailureListener {
                callback(emptyList())
            }
    }

    private fun cancelReminderNotifications(reminderId: String, context: Context) {
        Log.d("ReminderRepo", "Cancelling notifications for reminder: $reminderId")
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderBroadcastReceiver::class.java)

        // Cancel main notification
        val mainPendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        mainPendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }

        // Cancel offset notification
        val offsetPendingIntent = PendingIntent.getBroadcast(
            context,
            "${reminderId}_offset".hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        offsetPendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        Log.d("ReminderRepo", "Cancelled ${mainPendingIntent != null} main and ${offsetPendingIntent != null} offset notifications")
    }

    fun cancelReminderAlarms(reminderId: String, context: Context) {
        cancelReminderNotifications(reminderId, context)
    }

    private fun convertDocumentToReminder(data: Map<String, Any>): Reminder? {
        return try {
            val date = when (val dateValue = data["date"]) {
                is Long -> dateValue
                is String -> dateValue.toLongOrNull()
                is Number -> dateValue.toLong()
                else -> null
            } ?: return null

            Reminder(
                id = data["id"] as? String ?: "",
                userId = data["userId"] as? String ?: "",
                title = data["title"] as? String ?: "",
                description = data["description"] as? String ?: "",
                category = (data["category"] as? String)?.trim()?.lowercase() ?: "",
                date = date,
                emoji = data["emoji"] as? String ?: "⏰",
                color = (data["color"] as? Number)?.toInt() ?: Color.WHITE,
                isCompleted = data["isCompleted"] as? Boolean ?: false,
                repeatOption = data["repeatOption"] as? String ?: "Never",
                offsetMinutes = (data["offsetMinutes"] as? Number)?.toInt() ?: 0
            )
        } catch (e: Exception) {
            Log.e("ReminderRepository", "Error converting document", e)
            null
        }
    }

    fun deleteAllReminders(userId: String, callback: (Boolean) -> Unit) {
        remindersCollection
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                val batch = db.batch()
                for (document in documents) {
                    batch.delete(document.reference)
                }
                batch.commit()
                    .addOnSuccessListener {
                        callback(true)
                    }
                    .addOnFailureListener {
                        callback(false)
                    }
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    fun deleteCompletedRemindersOlderThan(days: Int, onComplete: (Int) -> Unit) {
        val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())

        remindersCollection
            .whereEqualTo("isCompleted", true)
            .whereLessThan("date", cutoffTime)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val batch = db.batch()
                querySnapshot.documents.forEach { document ->
                    batch.delete(document.reference)
                }
                batch.commit().addOnSuccessListener {
                    onComplete(querySnapshot.size())
                }
            }
            .addOnFailureListener {
                onComplete(0)
            }
    }

    fun completeReminder(reminderId: String, userId: String, callback: (Boolean) -> Unit) {
        val completedDate = System.currentTimeMillis()
        remindersCollection.document(reminderId)
            .update(
                mapOf(
                    "isCompleted" to true,
                    "completedDate" to completedDate,
                    "date" to completedDate
                )
            )
            .addOnSuccessListener {
                val categoryRepo = CategoryRepository()
                categoryRepo.getCategoriesWithCount(userId) { _ ->
                }
                callback(true)
            }
            .addOnFailureListener {
                callback(false)
            }
    }
}

