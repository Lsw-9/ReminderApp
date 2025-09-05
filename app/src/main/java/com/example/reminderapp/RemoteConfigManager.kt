package com.example.reminderapp

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

/**
 * Utility class to manage Firebase Remote Config
 * Used to securely fetch API keys and other configuration values
 */
class RemoteConfigManager {

    companion object {
        private const val TAG = "RemoteConfigManager"

        // Remote config keys
        const val KEY_GEMINI_API_KEY = "gemini_api_key"

        // Default values
        private const val DEFAULT_GEMINI_API_KEY = ""

        // Singleton instance
        @Volatile
        private var INSTANCE: RemoteConfigManager? = null

        fun getInstance(): RemoteConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RemoteConfigManager().also { INSTANCE = it }
            }
        }
    }

    // Firebase Remote Config instance
    private val remoteConfig = FirebaseRemoteConfig.getInstance()

    // Default values map
    private val defaults = mapOf(
        KEY_GEMINI_API_KEY to DEFAULT_GEMINI_API_KEY
    )

    /**
     * Initialize Remote Config with appropriate settings
     * Should be called early in the app lifecycle (e.g., in Application.onCreate)
     */
    fun initialize() {
        try {
            // Set default values
            remoteConfig.setDefaultsAsync(defaults)

            // Configure fetch settings
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600) // 1 hour
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)

            // Fetch and activate
            fetchAndActivate()

            Log.d(TAG, "RemoteConfig initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing RemoteConfig", e)
        }
    }

    /**
     * Fetch and activate remote config values
     * Returns true if activation was successful
     */
    private fun fetchAndActivate() {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "Config params updated: $updated")
                    Log.d(TAG, "Gemini API Key: ${getGeminiApiKey().take(5)}...")
                } else {
                    Log.e(TAG, "Failed to fetch remote config")
                }
            }
    }

    /**
     * Fetch and activate remote config values asynchronously
     * Returns true if activation was successful
     */
    suspend fun fetchAndActivateAsync(): Boolean {
        return try {
            val updated = remoteConfig.fetchAndActivate().await()
            Log.d(TAG, "Config params updated: $updated")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching remote config", e)
            false
        }
    }

    /**
     * Get the Gemini API key from Remote Config
     */
    fun getGeminiApiKey(): String {
        return remoteConfig.getString(KEY_GEMINI_API_KEY)
    }

    /**
     * Check if a valid Gemini API key is available
     */
    fun isGeminiApiKeyAvailable(): Boolean {
        val key = getGeminiApiKey()
        return key.isNotEmpty() && key != DEFAULT_GEMINI_API_KEY
    }
}