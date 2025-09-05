package com.example.reminderapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.example.reminderapp.SettingsManager

/**
 * Utility class for network-related functions
 */
object NetworkUtils {

    /**
     * Check if the device has an active network connection
     * @param context The context
     * @return True if the device is connected to the internet, false otherwise
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null && networkInfo.isConnected
        }
    }

    /**
     * Check if the app should operate in offline mode
     * @param context The context
     * @return True if the app should use offline mode, false otherwise
     */
    fun shouldUseOfflineMode(context: Context): Boolean {
        return !isNetworkAvailable(context)
    }
}