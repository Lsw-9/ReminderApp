package com.example.reminderapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.TimeUnit

class AutoDeleteWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override suspend fun doWork(): Result {
        try {
            val userId = auth.currentUser?.uid ?: return Result.failure()
            val cutoffTime = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))

            // Get completed reminders older than 1 day
            val query = db.collection("reminders")
                .whereEqualTo("userId", userId)
                .whereEqualTo("isCompleted", true)
                .whereLessThan("date", cutoffTime)

            val snapshot = query.get().await()

            // Delete in batch
            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }

            batch.commit().await()
            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }
}