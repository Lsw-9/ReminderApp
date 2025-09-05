package com.example.reminderapp

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReminderAlertActivity : AppCompatActivity() {

    private var snoozeDuration = 15 // Default snooze time in minutes
    private lateinit var bellIcon: ImageView
    private lateinit var shakeAnimation: Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_ReminderApp_NoActionBar)
        Log.d("ReminderAlert", "Activity created for notification ID: ${intent.getIntExtra("notification_id", -1)}")
        setContentView(R.layout.activity_reminder_alert)

        // Stop media and dismiss notification
        (application as ReminderApplication).stopMediaPlayback()
        val notificationId = intent.getIntExtra("notification_id", 0)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        bellIcon = findViewById(R.id.icon)
        shakeAnimation = AnimationUtils.loadAnimation(this, R.anim.shake_animation)

        // Start bell animation
        startBellAnimation()

        handleIntent(intent)
    }

    private fun startBellAnimation() {
        try {
            if (!::shakeAnimation.isInitialized) {
                shakeAnimation = AnimationUtils.loadAnimation(this, R.anim.shake_animation)
                Log.d("ReminderAlert", "Reinitialized animation")
            }

            shakeAnimation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {

                    bellIcon.startAnimation(shakeAnimation)
                    Log.d("ReminderAlert", "Animation restarted")
                }

                override fun onAnimationRepeat(animation: Animation?) {}
            })

            bellIcon.clearAnimation()
            bellIcon.startAnimation(shakeAnimation)
            Log.d("ReminderAlert", "Started bell animation")
        } catch (e: Exception) {
            Log.e("ReminderAlert", "Error starting animation: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        if (::bellIcon.isInitialized && ::shakeAnimation.isInitialized) {
            bellIcon.startAnimation(shakeAnimation)
            Log.d("ReminderAlert", "Resumed bell animation")
        }
    }

    private fun handleIntent(intent: Intent) {
        // Get reminder date/time from intent
        val reminderDate = intent.getLongExtra("original_time", System.currentTimeMillis())
        val calendar = Calendar.getInstance().apply {
            timeInMillis = reminderDate
        }

        // Get title and message
        val title = intent.getStringExtra("title") ?: "Reminder"
        val message = intent.getStringExtra("message") ?: ""

        // Format time (HH:MM)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeString = timeFormat.format(Date(reminderDate))

        // Format date (e.g., MON, 15 JAN)
        val dateFormat = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
        val dateString = dateFormat.format(Date(reminderDate)).uppercase()

        // Set time and date in UI
        findViewById<TextView>(R.id.alert_time).text = timeString
        findViewById<TextView>(R.id.alert_message).text =
            dateString ?: title
        findViewById<TextView>(R.id.alert_title).text = title

        // Check snooze settings
        val settingsManager = SettingsManager(this)
        if (settingsManager.snoozeEnabled) {
            snoozeDuration = settingsManager.snoozeDuration
            updateSnoozeButtonText()
        } else {
            findViewById<View>(R.id.snooze_container).visibility = View.GONE
        }

        // Create pending intents
        val snoozePendingIntent = createSnoozePendingIntent(intent)
        val donePendingIntent = createDonePendingIntent(intent)

        // Setup snooze adjustment buttons
        findViewById<ImageButton>(R.id.btn_decrease).setOnClickListener {
            if (snoozeDuration > 5) {
                snoozeDuration -= 5
                updateSnoozeButtonText()
            }
        }

        findViewById<ImageButton>(R.id.btn_increase).setOnClickListener {
            if (snoozeDuration < 60) {
                snoozeDuration += 5
                updateSnoozeButtonText()
            }
        }

        // Set button actions
        findViewById<Button>(R.id.btn_snooze).setOnClickListener {
            try {
                val updatedIntent = Intent(this, SnoozeActionReceiver::class.java).apply {
                    action = "SNOOZE_ACTIVITY_${System.currentTimeMillis()}"  // Unique action
                    putExtras(intent.extras ?: Bundle())
                    putExtra("notification_id", intent.getIntExtra("notification_id", 0))
                    putExtra("reminder_id", intent.getStringExtra("reminder_id"))
                    putExtra("original_time", intent.getLongExtra("original_time", 0L))
                    putExtra("snooze_duration", snoozeDuration)
                }

                val updatedPendingIntent = PendingIntent.getBroadcast(
                    this,
                    System.currentTimeMillis().toInt(),
                    updatedIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )

                updatedPendingIntent.send()
            } catch (e: PendingIntent.CanceledException) {
                Log.e("ReminderAlert", "Snooze pending intent canceled", e)
            }
            finish()
        }

        findViewById<ImageButton>(R.id.btn_dismiss).setOnClickListener {
            finish()
        }

        findViewById<ImageButton>(R.id.btn_complete).setOnClickListener {
            try {
                donePendingIntent.send()
            } catch (e: PendingIntent.CanceledException) {
                Log.e("ReminderAlert", "Done pending intent canceled", e)
            }
            finish()
        }

    }

    private fun updateSnoozeButtonText() {
        findViewById<Button>(R.id.btn_snooze).text = "Snooze ${snoozeDuration} min"
    }

    private fun createSnoozePendingIntent(originalIntent: Intent): PendingIntent {
        Log.d("ReminderAlert", "Creating activity snooze PI for ID: ${originalIntent.getIntExtra("notification_id", -1)}")
        val intent = Intent(this, SnoozeActionReceiver::class.java).apply {
            action = "SNOOZE_ACTIVITY_${System.currentTimeMillis()}"  // Unique action
            putExtras(originalIntent.extras ?: Bundle())
            putExtra("notification_id", originalIntent.getIntExtra("notification_id", 0))
            putExtra("reminder_id", originalIntent.getStringExtra("reminder_id"))
            putExtra("original_time", originalIntent.getLongExtra("original_time", 0L))
            putExtra("snooze_duration", snoozeDuration)
        }
        return PendingIntent.getBroadcast(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createDonePendingIntent(originalIntent: Intent): PendingIntent {
        Log.d("ReminderAlert", "Creating activity done PI for ID: ${originalIntent.getIntExtra("notification_id", -1)}")
        val intent = Intent(this, DoneActionReceiver::class.java).apply {
            action = "DONE_ACTIVITY_${System.currentTimeMillis()}"  // Unique action
            putExtras(originalIntent.extras ?: Bundle())
            putExtra("notification_id", originalIntent.getIntExtra("notification_id", 0))
            putExtra("reminder_id", originalIntent.getStringExtra("reminder_id"))
        }
        return PendingIntent.getBroadcast(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}