package com.example.reminderapp
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.auth.FirebaseAuth

class WelActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Check if user is already logged in
        if (auth.currentUser != null) {
            // User is already logged in, go directly to MainActivity
            Log.d("WelActivity", "User already logged in: ${auth.currentUser?.email}")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // If not logged in, show welcome screen
        setContentView(R.layout.activity_wel)

        updateSecurityProvider()

        val button: Button = findViewById(R.id.continueButton)


        button.setOnClickListener {
            val intent = Intent(this, AuthActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updateSecurityProvider() {
        try {
            ProviderInstaller.installIfNeeded(this)
            Log.d("ProviderInstaller", "Provider updated successfully.")
        } catch (e: GooglePlayServicesRepairableException) {
            Log.e("ProviderInstaller", "Google Play Services repairable exception: $e")
            // Optionally prompt the user to update Google Play Services
        } catch (e: GooglePlayServicesNotAvailableException) {
            Log.e("ProviderInstaller", "Google Play Services not available: $e")
            // Optionally notify the user about this issue
        } catch (e: Exception) {
            Log.e("ProviderInstaller", "Unexpected exception: $e")
        }

    }
}

