package com.example.reminderapp

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

class SettingsManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)

    var alarmEnabled: Boolean
        get() = sharedPreferences.getBoolean("alarm_enabled", false)
        set(value) = sharedPreferences.edit().putBoolean("alarm_enabled", value).apply()

    var alarmRingtone: String?
        get() = sharedPreferences.getString("alarm_ringtone", null)
        set(value) = sharedPreferences.edit().putString("alarm_ringtone", value).apply()

    var notificationRingtone: String?
        get() = sharedPreferences.getString("notification_ringtone", null)
        set(value) = sharedPreferences.edit().putString("notification_ringtone", value).apply()

    var snoozeEnabled: Boolean
        get() = sharedPreferences.getBoolean("snooze_enabled", false)
        set(value) = sharedPreferences.edit().putBoolean("snooze_enabled", value).apply()

    var snoozeDuration: Int
        get() = sharedPreferences.getInt("snooze_duration", 10)
        set(value) = sharedPreferences.edit().putInt("snooze_duration", value).apply()

    var emailNotificationsEnabled: Boolean
        get() = sharedPreferences.getBoolean("email_notifications", false)
        set(value) = sharedPreferences.edit().putBoolean("email_notifications", value).apply()

    var emailUsername: String?
        get() = sharedPreferences.getString("email_username", null)
        set(value) = sharedPreferences.edit().putString("email_username", value).apply()

    var emailPassword: String?
        get() = sharedPreferences.getString("email_password", null)
        set(value) = sharedPreferences.edit().putString("email_password", value).apply()

    var darkThemeEnabled: Boolean
        get() = sharedPreferences.getBoolean("dark_theme", false)
        set(value) = sharedPreferences.edit().putBoolean("dark_theme", value).apply()

    var autoDeleteEnabled: Boolean
        get() = sharedPreferences.getBoolean("auto_delete", false)
        set(value) = sharedPreferences.edit().putBoolean("auto_delete", value).apply()

    var firebaseSyncEnabled: Boolean
        get() = sharedPreferences.getBoolean("firebase_sync", true)
        set(value) = sharedPreferences.edit().putBoolean("firebase_sync", value).apply()

    fun getRingtoneName(context: Context): String {
        return notificationRingtone?.let { uriString ->
            try {
                RingtoneManager.getRingtone(context, Uri.parse(uriString))?.getTitle(context)
            } catch (e: Exception) {
                "System default"
            }
        } ?: "System default"
    }

    fun getAlarmName(context: Context): String {
        return alarmRingtone?.let { uriString ->
            try {
                RingtoneManager.getRingtone(context, Uri.parse(uriString))?.getTitle(context)
            } catch (e: Exception) {
                "Default alarm"
            }
        } ?: "Default alarm"
    }
}