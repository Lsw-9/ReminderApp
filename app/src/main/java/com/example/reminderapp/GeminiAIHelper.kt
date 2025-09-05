package com.example.reminderapp

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import com.google.ai.client.generativeai.type.ServerException
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit
/**
 * Helper class for interacting with Google's Gemini AI API
 */
class GeminiAIHelper(private val context: Context) {

    companion object {
        private const val TAG = "GeminiAIHelper"
        private const val DEFAULT_API_KEY = "-"

        // Message types
        const val MESSAGE_TYPE_REMINDER = "reminder"
        const val MESSAGE_TYPE_CHITCHAT = "chitchat"
        const val MESSAGE_TYPE_ERROR = "error"
    }

    // Initialize the Gemini model with the API key from Remote Config
    private val generativeModel by lazy {
        val apiKey = RemoteConfigManager.getInstance().getGeminiApiKey().ifEmpty { DEFAULT_API_KEY }
        GenerativeModel(
            modelName = "gemini-1.5-pro",
            apiKey = apiKey
        )
    }

    // Store conversation history for context
    private val conversationHistory = mutableListOf<String>()
    private val maxHistoryItems = 10

    private val appTimeZone = ZoneId.of("Asia/Kuala_Lumpur")

    /**
     * Sealed class to represent different types of AI responses
     */
    sealed class AIResponse {
        data class Reminder(val reminderData: ReminderData) : AIResponse()
        data class Chitchat(val message: String) : AIResponse()
        data class Error(val message: String) : AIResponse()
    }

    /**
     * Data class to hold reminder information
     */
    data class ReminderData(
        val title: String,
        val description: String,
        val timestamp: Long,
        val category: String,
        val originalInput: String = ""
    )

    /**
     * Process user message to determine if it's a reminder request, time query, or general conversation
     * @param message The user's message
     * @return An AIResponse object containing the appropriate response
     */
    suspend fun processMessage(message: String): AIResponse = withContext(Dispatchers.IO) {
        try {
            // Check if we have a valid API key
            if (!RemoteConfigManager.getInstance().isGeminiApiKeyAvailable()) {
                Log.e(TAG, "No valid Gemini API key available")
                return@withContext AIResponse.Error("Sorry, I'm not available right now. Please check your internet connection and try again later.")
            }

            // Check if this is a time-related query
            val timeResponse = handleTimeQuery(message)
            if (timeResponse != null) {
                addToConversationHistory("User: $message")
                addToConversationHistory("Assistant: $timeResponse")
                return@withContext AIResponse.Chitchat(timeResponse)
            }

            // Check if this is a question about app usage
            val appUsageResponse = handleAppUsageQuestion(message)
            if (appUsageResponse != null) {
                addToConversationHistory("User: $message")
                addToConversationHistory("Assistant: $appUsageResponse")
                return@withContext AIResponse.Chitchat(appUsageResponse)
            }

            // Determine if this is a reminder request or general conversation
            val isReminderRequest = determineMessageType(message)

            // Add user input to conversation history
            addToConversationHistory("User: $message")

            return@withContext if (isReminderRequest) {
                val reminderData = processReminderInput(message)
                if (reminderData != null) {
                    AIResponse.Reminder(reminderData)
                } else {
                    val response = processChitchat(message)
                    AIResponse.Chitchat(response)
                }
            } else {
                val response = processChitchat(message)
                AIResponse.Chitchat(response)
            }
        } catch (e: ServerException) {
            if (e.message?.contains("Resource has been exhausted", ignoreCase = true) == true) {
                Log.e(TAG, "Gemini API quota exceeded", e)
                return@withContext AIResponse.Error("I'm sorry, but I've reached my usage limit for now. Please try again later or contact the app developer to upgrade the API quota.")
            } else {
                Log.e(TAG, "Server error processing user input", e)
                return@withContext AIResponse.Error("AI assistant is temporarily unavailable due to usage limits")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing user input", e)
            return@withContext AIResponse.Error("Sorry, I encountered an error. Please try again.")
        }
    }


    /**
     * Handle time-related queries directly without using the AI model
     * @param input The user's input
     * @return A response string if it's a time query, null otherwise
     */
    private fun handleTimeQuery(input: String): String? {
        val lowerInput = input.toLowerCase(Locale.getDefault())

        // Check if this is a time query
        val timeQueryPatterns = listOf(
            "what time is it", "what's the time", "current time", "time now",
            "what is the time", "tell me the time", "what time", "time is it"
        )

        for (pattern in timeQueryPatterns) {
            if (lowerInput.contains(pattern)) {
                // Get current time in the app's time zone
                val now = ZonedDateTime.now(appTimeZone)
                val formatter = DateTimeFormatter.ofPattern("h:mm a, EEEE, MMMM d, yyyy", Locale.ENGLISH)
                return "It's currently ${formatter.format(now)} in your local time zone."
            }
        }

        return null
    }


    /**
     * Determine if the input is a reminder request or general conversation
     * @param input The user's input
     * @return True if the input appears to be a reminder request, false otherwise
     */
    private fun determineMessageType(input: String): Boolean {

        val lowerInput = input.toLowerCase(Locale.getDefault())

        // First check if this is a question about reminders rather than a command to create one
        val questionPatterns = listOf(
            "how do", "how to", "how can", "how do i", "how to", "how should i",
            "what is", "what's", "explain", "tell me about", "show me how",
            "\\?$" // ends with question mark
        )

        // If it matches a question pattern, it's probably not a reminder creation request
        for (pattern in questionPatterns) {
            if (lowerInput.contains(Regex(pattern))) {
                return false
            }
        }

        // If not a question, check for reminder-related keywords
        val reminderKeywords = listOf(
            "remind", "reminder", "schedule", "set", "create", "make", "add", "appointment", "meeting",
            "event", "task", "todo", "to-do", "to do", "don't forget", "remember"
        )


        // Check for reminder-related keywords
        for (keyword in reminderKeywords) {
            if (lowerInput.contains(keyword)) {
                return true
            }
        }

        // Check for time-related patterns that might indicate a reminder
        val timePatterns = listOf(
            "at \\d", "on \\w+ \\d", "tomorrow", "next \\w+", "\\d(am|pm)", "\\d:\\d\\d",
            "morning", "afternoon", "evening", "night", "today", "tonight", "after \\d+ min"
        )

        for (pattern in timePatterns) {
            if (lowerInput.contains(Regex(pattern))) {
                return true
            }
        }

        return false
    }

    /**
     * Process natural language input to extract reminder details
     * @param input The user's natural language input
     * @return A ReminderData object containing the extracted information
     */
    suspend fun processReminderInput(input: String): ReminderData? = withContext(Dispatchers.IO) {
        try {
            // Check for relative time patterns first
            val relativeTimeReminder = handleRelativeTimeReminder(input)
            if (relativeTimeReminder != null) {
                return@withContext relativeTimeReminder
            }

            // Get current date and time for context
            val now = ZonedDateTime.now(appTimeZone)
            val currentDate = now.toLocalDate()
            val currentTime = now.toLocalTime()
            val currentTimeFormatted = currentTime.format(DateTimeFormatter.ofPattern("HH:mm"))

            // Create a prompt that instructs Gemini to extract reminder information
            val prompt = """
                Extract reminder information from the following text and format it as JSON.
                Text: "$input"
                
                Current date and time: ${currentDate} ${currentTimeFormatted} in Asia/Kuala_Lumpur timezone.
                
                Return a JSON object with these fields:
                - title: The main subject of the reminder
                - description: Additional details about the reminder (can be empty)
                - date: The date in YYYY-MM-DD format (use today's date if not specified)
                - time: The time in HH:MM format (24-hour)
                - category: A suitable category for this reminder (work, personal, health, etc.)
                
                For relative dates like "tomorrow", "next week", etc., calculate the exact date based on today's date.
                For relative times like "in 10 minutes", "after 1 hour", calculate the exact time based on the current time.
                If the text doesn't contain a specific time or date, make a reasonable guess based on context.
                If no category is mentioned, choose an appropriate one based on the content.
            """.trimIndent()

            // Generate content using Gemini API
            val response = generativeModel.generateContent(
                content {
                    text(prompt)
                }
            )

            // Parse the response
            return@withContext parseResponse(response, input)
        } catch (e: ServerException) {
            Log.e(TAG, "Server error processing reminder input", e)
            throw e // Propagate server exceptions to be handled by the caller
        } catch (e: Exception) {
            Log.e(TAG, "Error processing reminder input", e)
            return@withContext null
        }
    }

    /**
     * Handle reminders with relative time references like "after 1 minute"
     * @param input The user's input
     * @return A ReminderData object if the input contains a relative time reference, null otherwise
     */
    private fun handleRelativeTimeReminder(input: String): ReminderData? {
        val lowerInput = input.toLowerCase(Locale.getDefault())

        // Check for patterns like "after X minutes/hours" or "in X minutes/hours"
        val minutePatterns = listOf(
            "after (\\d+) min", "in (\\d+) min",
            "after (\\d+) minute", "in (\\d+) minute",
            "after (\\d+) minutes", "in (\\d+) minutes"
        )

        val hourPatterns = listOf(
            "after (\\d+) hour", "in (\\d+) hour",
            "after (\\d+) hours", "in (\\d+) hours"
        )

        // Extract title from the input
        val titlePattern = "remind me to (.+?)(after|in|at|on|tomorrow|next|later)".toRegex(RegexOption.IGNORE_CASE)
        val titleMatch = titlePattern.find(lowerInput)
        val title = titleMatch?.groupValues?.get(1)?.trim() ?: "New Reminder"

        // Check for minute patterns
        for (pattern in minutePatterns) {
            val regex = pattern.toRegex(RegexOption.IGNORE_CASE)
            val matchResult = regex.find(lowerInput)

            if (matchResult != null) {
                val minutes = matchResult.groupValues[1].toIntOrNull() ?: 1
                val now = System.currentTimeMillis()
                val timestamp = now + TimeUnit.MINUTES.toMillis(minutes.toLong())

                // Determine category based on content
                val category = determineCategoryFromContent(lowerInput)

                Log.d(TAG, "Created relative time reminder: $title in $minutes minutes (${Date(timestamp)})")

                return ReminderData(
                    title = title,
                    description = "",
                    timestamp = timestamp,
                    category = category,
                    originalInput = input
                )
            }
        }

        // Check for hour patterns
        for (pattern in hourPatterns) {
            val regex = pattern.toRegex(RegexOption.IGNORE_CASE)
            val matchResult = regex.find(lowerInput)

            if (matchResult != null) {
                val hours = matchResult.groupValues[1].toIntOrNull() ?: 1
                val now = System.currentTimeMillis()
                val timestamp = now + TimeUnit.HOURS.toMillis(hours.toLong())

                // Determine category based on content
                val category = determineCategoryFromContent(lowerInput)

                Log.d(TAG, "Created relative time reminder: $title in $hours hours (${Date(timestamp)})")

                return ReminderData(
                    title = title,
                    description = "",
                    timestamp = timestamp,
                    category = category,
                    originalInput = input
                )
            }
        }

        return null
    }

    /**
     * Determine a suitable category based on the content of the reminder
     */
    private fun determineCategoryFromContent(content: String): String {
        val lowerContent = content.toLowerCase(Locale.getDefault())

        return when {
            lowerContent.contains("work") || lowerContent.contains("meeting") ||
                    lowerContent.contains("project") || lowerContent.contains("deadline") -> "work"

            lowerContent.contains("doctor") || lowerContent.contains("medicine") ||
                    lowerContent.contains("pill") || lowerContent.contains("health") ||
                    lowerContent.contains("exercise") || lowerContent.contains("workout") ||
                    lowerContent.contains("gym") || lowerContent.contains("water") -> "health"

            lowerContent.contains("call") || lowerContent.contains("text") ||
                    lowerContent.contains("email") || lowerContent.contains("message") -> "communication"

            lowerContent.contains("buy") || lowerContent.contains("shop") ||
                    lowerContent.contains("purchase") || lowerContent.contains("store") -> "shopping"

            lowerContent.contains("birthday") || lowerContent.contains("anniversary") ||
                    lowerContent.contains("celebration") -> "event"

            else -> "personal"
        }
    }


    /**
     * Process general conversation (chitchat)
     * @param input The user's input
     * @return A response string
     */
    suspend fun processChitchat(input: String): String = withContext(Dispatchers.IO) {
        try {
            // Create a prompt that includes conversation history for context
            val historyText = conversationHistory.takeLast(maxHistoryItems).joinToString("\n")

            // Get current date and time for context
            val now = ZonedDateTime.now(appTimeZone)
            val currentDate = now.toLocalDate()
            val currentTime = now.format(DateTimeFormatter.ofPattern("h:mm a"))

            val prompt = """
                 You are a friendly AI assistant in a reminder app called "Reminder App" designed to help manage tasks, create reminders with alarms, and organize reminders by categories. Respond conversationally to the user's message.
                
                Current date and time: $currentDate, $currentTime in Asia/Kuala_Lumpur timezone.
                
                 ABOUT THE APP:
                Reminder App is a comprehensive task management app with the following features:
                - Create reminders with title, description, date, time, and category
                - Set custom notifications with alarm sounds or default notification sounds
                - Categorize reminders with color-coded labels
                - View reminders in calendar mode or list mode
                - Search for specific reminders by title
                - Receive email notifications for important reminders
                - Snooze reminders for later
                - Mark reminders as complete
                - Get AI assistance (you) for creating reminders through natural language
                
                APP STRUCTURE:
                - Home screen: Shows upcoming reminders grouped by "Soon" (next 24 hours), "Future," and "Previous"
                - Calendar view: Shows all reminders in a monthly calendar format
                - Profile screen: Contains user account information and app settings
                - Settings: Control notification sounds, email notifications, and app behavior
                - Create Reminder screen: Form to add new reminders with all details
                - About dialog: Accessed from Profile screen, shows app information and contact details
                
                SUPPORT INFORMATION:
                - The app was developed by LIM SENG WEI
                - For support, users can email: reminderapp12@gmail.com
                - The About section can be accessed from the Profile screen
                
                HOW TO USE THE APP:
                1. CREATE REMINDERS:
                   - Tap the + floating button on the home or calendar screen
                   - Fill in the reminder details (title, date, time, etc.)
                   - Or ask me to create one by saying "Remind me to..."
                
                2. MANAGE REMINDERS:
                   - Tap on a reminder to view full details
                   - Swipe actions let you delete or mark as complete
                   - Edit reminders by tapping on them and selecting the edit option
                
                3. CATEGORIES:
                   - Create custom categories to organize reminders
                   - Each category has its own color for visual organization
                   - Filter reminders by category on the home screen
                
                4. NOTIFICATIONS:
                   - Set custom sounds for important reminders
                   - Enable email notifications for critical reminders
                   - Customize snooze duration in settings
                   - Choose between alarm mode (more intrusive) and standard notifications
                   - Descriptions appear in notifications only when provided (they're hidden if empty)
                   - When a notification appears, users can: snooze, dismiss, or mark as complete
                
                5. SEARCH:
                   - Use the search icon to find specific reminders
                   - Filter results by typing the reminder title
                
                Keep your responses helpful, friendly, and concise (1-3 sentences). If the user seems to be trying to set a reminder but you're not sure, suggest they try phrasing it as "Remind me to..." or give an example.
                
                Recent conversation history:
                $historyText
                
                User's message: "$input"
                
                Your response:
            """.trimIndent()

            // Generate content using Gemini API
            val response = generativeModel.generateContent(
                content {
                    text(prompt)
                }
            )

            val responseText = response.text?.trim() ?: "I'm not sure how to respond to that."

            addToConversationHistory("Assistant: $responseText")

            return@withContext responseText
        } catch (e: ServerException) {
            Log.e(TAG, "Server error processing chitchat", e)
            throw e // Propagate server exceptions to be handled by the caller
        } catch (e: Exception) {
            Log.e(TAG, "Error processing chitchat", e)
            return@withContext "Sorry, I'm having trouble understanding. If you're trying to set a reminder, try saying something like 'Remind me to call John tomorrow at 3pm'."
        }
    }

    /**
     * Add a message to the conversation history
     */
    private fun addToConversationHistory(message: String) {
        conversationHistory.add(message)
        if (conversationHistory.size > maxHistoryItems * 2) {
            conversationHistory.removeAt(0)
        }
    }

    /**
     * Parse the Gemini API response to extract reminder data
     */
    private fun parseResponse(response: GenerateContentResponse, originalInput: String): ReminderData? {
        try {
            // Extract the text from the response
            val responseText = response.text?.trim() ?: return null

            // Find JSON content in the response
            val jsonStart = responseText.indexOf("{")
            val jsonEnd = responseText.lastIndexOf("}") + 1

            if (jsonStart == -1 || jsonEnd == -1 || jsonStart >= jsonEnd) {
                Log.e(TAG, "Invalid JSON format in response: $responseText")
                return null
            }

            val jsonContent = responseText.substring(jsonStart, jsonEnd)
            val jsonObject = JSONObject(jsonContent)

            // Extract fields from JSON
            val title = jsonObject.optString("title", "")
            val description = jsonObject.optString("description", "")
            val dateStr = jsonObject.optString("date", LocalDate.now(appTimeZone).toString())
            val timeStr = jsonObject.optString("time", "12:00")
            val category = jsonObject.optString("category", "Personal")

            // Parse date and time
            val date = try {
                LocalDate.parse(dateStr)
            } catch (e: DateTimeParseException) {
                Log.w(TAG, "Could not parse date: $dateStr, using today", e)
                LocalDate.now(appTimeZone)
            }

            val time = try {
                LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"))
            } catch (e: DateTimeParseException) {
                Log.w(TAG, "Could not parse time: $timeStr, using noon", e)
                LocalTime.NOON
            }

            // Combine date and time into a timestamp
            val dateTime = LocalDateTime.of(date, time)
            val malaysiaZone = ZoneId.of("Asia/Kuala_Lumpur")
            val timestamp = dateTime.atZone(malaysiaZone).toInstant().toEpochMilli()

            // Log the date information for debugging
            Log.d(TAG, """
                Date parsing details:
                Original input: $originalInput
                Parsed date string: $dateStr
                Parsed time string: $timeStr
                LocalDate: $date
                LocalTime: $time
                LocalDateTime: $dateTime
                Malaysia time: ${dateTime.atZone(malaysiaZone)}
                Timestamp: $timestamp
                As Date object: ${java.util.Date(timestamp)}
            """.trimIndent())

            return ReminderData(
                title = title.ifEmpty { "New Reminder" },
                description = description,
                timestamp = timestamp,
                category = category,
                originalInput = originalInput
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini response", e)
            return null
        }
    }

    /**
     * Handle questions about app usage and features directly without using the AI model
     * @param input The user's input
     * @return A response string if it's an app usage question, null otherwise
     */
    private fun handleAppUsageQuestion(input: String): String? {
        val lowerInput = input.toLowerCase(Locale.getDefault())

        // Map of question patterns to their answers
        val questionAnswers = mapOf(
            "how (do|to|can) (i )?(create|make|add|set) (a )?reminder" to
                    "To create a reminder, tap the + floating button on the home or calendar screen, then fill in the reminder details. Alternatively, you can just tell me what you want to be reminded about by saying something like 'Remind me to call mom tomorrow at 5pm'.",

            "how (do|to|can) (i )?(use|create|make|add) (a )?(custom )?categor" to
                    "To create or use categories, go to the Create Reminder screen and tap on the Category field. You can select an existing category or create a new one by tapping 'Add Category'. Each category can have its own color for easy visual organization.",

            "how (do|to|can) (i )?search" to
                    "To search for reminders, tap the search icon in the top bar on the home or calendar screen. Then type the title of the reminder you're looking for. Results will appear as you type.",

            "how (do|to|can) (i )?(use|view|open) (the )?calendar" to
                    "To use the calendar view, tap the Calendar icon in the bottom navigation bar. This shows all your reminders in a monthly calendar format. You can tap on any date to see reminders for that day.",

            "how (do|to|can) (i )?(get|enable|setup|configure) (the )?email notification" to
                    "To enable email notifications, go to the Profile screen, tap Settings, then toggle on 'Email Notifications'. Make sure your email address is correctly set in your profile.",

            "how (do|to|can) (i )?snooze" to
                    "When a reminder notification appears, you can tap the 'Snooze' button to postpone it. You can adjust the snooze duration using the + and - buttons. You can also customize the default snooze duration in Settings.",

            "how (do|to|can) (i )?(edit|change|modify|update) (a )?reminder" to
                    "To edit a reminder, tap on it from the home screen or calendar view. Then tap the edit button to modify its details.",

            "how (do|to|can) (i )?(delete|remove) (a )?reminder" to
                    "To delete a reminder, swipe left on it from the list view. You can also tap on a reminder, then use the delete option from the detailed view."
        )

        // Check each pattern and return the corresponding answer if matched
        for ((pattern, answer) in questionAnswers) {
            if (lowerInput.contains(Regex(pattern))) {
                return answer
            }
        }

        return null
    }

}
