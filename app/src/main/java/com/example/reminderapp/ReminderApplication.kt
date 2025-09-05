package com.example.reminderapp

import android.app.Application
import android.media.Ringtone
import android.os.Vibrator
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.FirebaseApp
import javax.net.ssl.SSLContext
import javax.net.ssl.HttpsURLConnection


import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate


class ReminderApplication : Application() {

    companion object {
        val reminderStartTimes = mutableMapOf<String, Long>()
        var currentRingtone: Ringtone? = null
        var currentVibrator: Vibrator? = null
    }

    fun stopMediaPlayback() {
        currentRingtone?.stop()
        currentVibrator?.cancel()
        currentRingtone = null
        currentVibrator = null
    }

override fun onCreate() {
        super.onCreate()
    // Clear media playback on app restart
    stopMediaPlayback()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

    // Enable Firestore offline persistence
    val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
        .setPersistenceEnabled(true)
        .setCacheSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
        .build()
    com.google.firebase.firestore.FirebaseFirestore.getInstance().firestoreSettings = settings

    // Initialize RemoteConfigManager
    RemoteConfigManager.getInstance().initialize()

        // Initialize security provider synchronously
        try {
            ProviderInstaller.installIfNeeded(applicationContext)

            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            SSLContext.setDefault(sslContext)

            // Set default hostname verifier to be more lenient
            HttpsURLConnection.setDefaultHostnameVerifier { hostname, session ->
                // Allow all Google and Gmail hostnames
                hostname.contains("gmail.com") ||
                        hostname.contains("google.com") ||
                        hostname.contains("googleapis.com") ||
                        hostname.contains("gstatic.com") ||
                        hostname.contains("firebaseio.com")
            }

        } catch (e: Exception) {
            Log.e("Security", "Error installing security provider", e)
        }

        val availability = GoogleApiAvailability.getInstance()
        val status = availability.isGooglePlayServicesAvailable(this)
        if (status != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            Log.e("GooglePlay", "Google Play Services not available")
        }
    }
}