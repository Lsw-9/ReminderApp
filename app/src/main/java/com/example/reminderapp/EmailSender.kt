package com.example.reminderapp

import android.content.Context
import android.util.Log
import com.example.reminderapp.SettingsManager
import com.example.reminderapp.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate


class EmailSender {
    companion object {
        private const val TAG = "EmailSender"

        // Gmail SMTP settings - using SSL instead of TLS
        private const val SMTP_HOST = "smtp.gmail.com"
        private const val SMTP_PORT = "465"  // SSL port

        // App-specific email account
        private const val APP_EMAIL = "reminderapp12@gmail.com"
        private const val APP_PASSWORD = "vvwxmyhppakxxjsx" // App password

        suspend fun sendReminderEmail(context: Context, reminder: Reminder, userEmail: String): Boolean = withContext(Dispatchers.IO) {
            try {
                val settingsManager = SettingsManager(context)
                if (!settingsManager.emailNotificationsEnabled) {
                    Log.d(TAG, "Email notifications are disabled")
                    return@withContext false
                }

                Log.d(TAG, "Preparing to send email notification to $userEmail for reminder: ${reminder.title}")

                // Create a trust manager that does not validate certificate chains
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

                // Install the all-trusting trust manager
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())

                // Set up mail properties for SSL connection
                val properties = Properties()
                properties["mail.smtp.host"] = SMTP_HOST
                properties["mail.smtp.port"] = SMTP_PORT
                properties["mail.smtp.auth"] = "true"
                properties["mail.smtp.ssl.enable"] = "true"
                properties["mail.smtp.timeout"] = "10000"
                properties["mail.smtp.connectiontimeout"] = "10000"
                properties["mail.smtp.ssl.trust"] = "*"  // Trust all hosts
                properties["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
                properties["mail.smtp.socketFactory.port"] = SMTP_PORT

                // Create authenticator
                val authenticator = object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(APP_EMAIL, APP_PASSWORD)
                    }
                }

                // Create session with SSL context
                val session = Session.getInstance(properties, authenticator)
                session.setDebug(true)

                // Create message
                val message = MimeMessage(session)
                message.setFrom(InternetAddress(APP_EMAIL))
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(userEmail))
                message.subject = "Reminder: ${reminder.title}"

                // Format date
                val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                val reminderDate = Date(reminder.date)

                // Create email body
                val emailBody = """
                    <html>
                    <body style="font-family: Arial, sans-serif; color: #333333;">
                        <div style="padding: 20px; background-color: #f8f8f8; border-radius: 5px;">
                            <h2 style="color: #6200EE;">Reminder: ${reminder.title}</h2>
                            <p><strong>Date:</strong> ${dateFormat.format(reminderDate)}</p>
                            ${if (reminder.description.isNotEmpty()) "<p><strong>Description:</strong> ${reminder.description}</p>" else ""}
                            ${if (reminder.category.isNotEmpty()) "<p><strong>Category:</strong> ${reminder.category}</p>" else ""}
                            <p style="margin-top: 20px; font-size: 12px; color: #888888;">
                                This is an automated message from your Reminder App.
                            </p>
                        </div>
                    </body>
                    </html>
                """.trimIndent()

                message.setContent(emailBody, "text/html; charset=utf-8")

                try {
                    // Send message
                    Transport.send(message)
                    Log.d(TAG, "Email sent successfully to $userEmail")
                    return@withContext true
                } catch (e: MessagingException) {
                    Log.e(TAG, "Failed to send email: ${e.message}", e)
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing email: ${e.message}", e)
                return@withContext false
            }
        }
    }
}