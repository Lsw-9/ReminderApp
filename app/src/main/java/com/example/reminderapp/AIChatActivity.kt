package com.example.reminderapp

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.reminderapp.ChatMessageAdapter
import com.example.reminderapp.databinding.ActivityAiChatBinding
import com.example.reminderapp.ChatMessage
import com.example.reminderapp.GeminiAIHelper
import com.example.reminderapp.RemoteConfigManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.UUID
import android.text.Editable
import android.text.TextWatcher
import kotlinx.coroutines.delay
import android.graphics.Rect
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import android.util.Log

class AIChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiChatBinding
    private lateinit var adapter: ChatMessageAdapter
    private lateinit var geminiAIHelper: GeminiAIHelper
    private val messages = mutableListOf<ChatMessage>()
    private val pendingReminders = mutableMapOf<String, GeminiAIHelper.ReminderData>()

    // Default suggestions
    private val defaultSuggestions = listOf(
        "Remind me to call my friend tomorrow at 5pm",
        "Set a reminder for my  appointment",
        "What can you help me with?",
        "Create a weekly reminder for team meeting"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI Assistant"

        // Initialize GeminiAIHelper
        geminiAIHelper = GeminiAIHelper(this)

        // Check API availability
        checkApiAvailability()

        // Set up RecyclerView
        adapter = ChatMessageAdapter(
            onSuggestionClick = { suggestion ->
                binding.messageInput.setText(suggestion)
                binding.messageInput.setSelection(suggestion.length)
                binding.messageInput.requestFocus()
            },
            onLinkClick = { url ->
                handleLinkClick(url)
            }
        )

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter

        // Add item decoration for spacing
        binding.recyclerView.addItemDecoration(object : ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                outRect.bottom = resources.getDimensionPixelSize(R.dimen.message_spacing)
            }
        })

        // Improve scrolling behavior
        binding.recyclerView.setHasFixedSize(false)
        binding.recyclerView.itemAnimator = null // Disable animations to prevent glitches

        setupKeyboardListener()

        addBotMessage("👋 Welcome to Reminder App! I'm your AI assistant, and I can help you with:\n\n" +
                "• Creating reminders with natural language (try \"Remind me to call mom tomorrow at 5pm\")\n" +
                "• Providing tips for getting the most out of the app\n\n" +
                "What would you like help with today?")

        binding.recyclerView.post {
            adapter.setSuggestions(defaultSuggestions)
            scrollToBottom()
        }

        binding.sendButton.setOnClickListener {
            val message = binding.messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
            }
        }

        // Set up text watcher for input field
        setupTextWatcher()

        // Initialize Remote Config
        lifecycleScope.launch {
            val configInitialized = RemoteConfigManager.getInstance().fetchAndActivateAsync()
            if (!configInitialized) {
                Toast.makeText(
                    this@AIChatActivity,
                    "Failed to initialize configuration. Some features may not work.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Set up a keyboard listener to adjust the recycler view padding when the keyboard appears/disappears
     */
    private fun setupKeyboardListener() {
        // Get the root view to detect layout changes
        val rootView = binding.root

        // Set up a global layout listener
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)

            val screenHeight = rootView.height
            val keypadHeight = screenHeight - r.bottom

            // If keyboard height is more than 15% of screen height, it's likely visible
            if (keypadHeight > screenHeight * 0.15) {
                // Keyboard is showing - add padding to the bottom of the recycler view
                binding.recyclerView.setPadding(0, 0, 0, keypadHeight)
                scrollToBottom()
            } else {
                // Keyboard is hidden - reset padding
                binding.recyclerView.setPadding(0, 0, 0, 0)
            }
        }
    }

    private fun setupTextWatcher() {
        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val isEmpty = s.isNullOrEmpty()

                // Change send button appearance based on text content
                binding.sendButton.isEnabled = !isEmpty
                binding.sendButton.alpha = if (isEmpty) 0.5f else 1.0f

                lifecycleScope.launch {
                    if (!isEmpty) {
                        adapter.setSuggestions(emptyList())
                    } else if (isEmpty) {

                        delay(300)
                        if (binding.messageInput.text.isEmpty()) {
                            adapter.setSuggestions(defaultSuggestions)
                            scrollToBottom()
                        }
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun sendMessage(message: String) {

        addUserMessage(message)

        binding.messageInput.text.clear()

        adapter.setSuggestions(emptyList())

        when {
            message.equals("Let's play a game", ignoreCase = true) ||
                    message.equals("game", ignoreCase = true) ||
                    message.equals("play game", ignoreCase = true) ||
                    message.equals("play", ignoreCase = true) ||
                    message.equals("Play again", ignoreCase = true) -> {

                adapter.showTypingIndicator()

                lifecycleScope.launch {
                    delay(1000)
                    adapter.hideTypingIndicator()

                    adapter.hideGameButton()

                    addBotMessage("Ok, I can play with you! Let's see if you can beat me in the Ping Pong game.")

                    adapter.showGameButton()

                    scrollToBottom()
                }
                return
            }
            message.equals("Show me my reminders", ignoreCase = true) -> {
                adapter.hideGameButton()

                adapter.showTypingIndicator()

                lifecycleScope.launch {
                    delay(1000)
                    adapter.hideTypingIndicator()

                    val intent = Intent(this@AIChatActivity, MainActivity::class.java)
                    startActivity(intent)

                    addBotMessage("I've opened your reminders list.")
                }
                return
            }
            message.equals("Can I create a reminder manually?", ignoreCase = true) -> {
                adapter.hideGameButton()

                adapter.showTypingIndicator()

                lifecycleScope.launch {
                    delay(1000)
                    adapter.hideTypingIndicator()

                    val intent = Intent(this@AIChatActivity, CreateReminder::class.java)
                    startActivity(intent)

                    addBotMessage("I've opened the reminder creation screen for you.")
                }
                return
            }
        }

        adapter.hideGameButton()

        lifecycleScope.launch {
            delay(300)
            adapter.showTypingIndicator()
        }

        // Process message with AI
        lifecycleScope.launch {
            try {
                delay(1500)

                val response = geminiAIHelper.processMessage(message)

                adapter.hideTypingIndicator()

                when (response) {
                    is GeminiAIHelper.AIResponse.Reminder -> {
                        // Generate a unique ID for this reminder
                        val reminderId = UUID.randomUUID().toString()

                        // Store the reminder data with the ID
                        pendingReminders[reminderId] = response.reminderData
                        // Log the reminder data for debugging
                        Log.d("AIChatActivity", "Created reminder with ID: $reminderId, data: ${response.reminderData}")
                        // Create a message with a clickable link
                        val linkMessage = "I've prepared a reminder for \"${response.reminderData.title}\". <a href=\"reminder:$reminderId\">Click here to review and save this reminder</a>"
                        addBotMessageWithLink(linkMessage)

                        // Add suggestions for after creating a reminder
                        adapter.setSuggestions(listOf(
                            "Create another reminder",
                            "Show me my reminders",
                            "Thanks!"
                        ))
                        scrollToBottom()
                    }
                    is GeminiAIHelper.AIResponse.Chitchat -> {
                        // Add bot message
                        addBotMessage(response.message)

                        // Add suggestions based on context
                        if (response.message.contains("help", ignoreCase = true)) {
                            adapter.setSuggestions(listOf(
                                "Remind me to call my friend tomorrow",
                                "Can you create a reminder?",
                                "What can you do?"
                            ))
                        } else {
                            adapter.setSuggestions(defaultSuggestions)
                        }
                        scrollToBottom()
                    }
                    is GeminiAIHelper.AIResponse.Error -> {
                        // Add error message
                        addBotMessage("Sorry, I encountered an error: ${response.message}")

                        // Add suggestions for error recovery
                        adapter.setSuggestions(listOf(
                            "Try again",
                            "Create a simple reminder",
                            "Help me"
                        ))
                        scrollToBottom()
                    }
                    else -> {
                        // Add default response
                        addBotMessage("I'm not sure how to respond to that. Can you try asking something else?")
                        adapter.setSuggestions(defaultSuggestions)

                        scrollToBottom()
                    }
                }
            } catch (e: Exception) {
                adapter.hideTypingIndicator()

                addBotMessage("Sorry, something went wrong. Please try again.")

                e.printStackTrace()

                scrollToBottom()
            }
        }
    }

    private fun addUserMessage(message: String) {
        val chatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            message = message,
            timestamp = System.currentTimeMillis(),
            isFromUser = true
        )
        messages.add(chatMessage)
        adapter.submitList(messages.toList())
        scrollToBottom()
    }

    private fun addBotMessage(message: String) {
        val chatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            message = message,
            timestamp = System.currentTimeMillis(),
            isFromUser = false
        )
        messages.add(chatMessage)
        adapter.submitList(messages.toList())
        scrollToBottom()
    }

    private fun addBotMessageWithLink(message: String) {
        val chatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            message = message,
            timestamp = System.currentTimeMillis(),
            isFromUser = false,
            hasLinks = true
        )
        messages.add(chatMessage)
        adapter.submitList(messages.toList())
        scrollToBottom()
    }

    /**
     * Navigate to the reminders list screen
     */
    private fun navigateToRemindersList() {
        // Simply finish this activity to go back to the main screen with reminders list
        finish()
    }

    /**
     * Navigate to the create reminder screen
     */
    private fun navigateToCreateReminder() {
        val intent = Intent(this, CreateReminder::class.java)
        startActivity(intent)
    }


    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    /**
     * Check if the Gemini API is available and show appropriate messages
     */
    private fun checkApiAvailability() {
        // Get SharedPreferences to track if message has been shown
        val prefs = getSharedPreferences("ai_chat_prefs", Context.MODE_PRIVATE)
        val hasShownApiMessage = prefs.getBoolean("has_shown_api_message", false)

        // If message has already been shown, skip displaying it again
        if (hasShownApiMessage) {
            return
        }

        lifecycleScope.launch {
            val isApiKeyAvailable = RemoteConfigManager.getInstance().isGeminiApiKeyAvailable()

            if (!isApiKeyAvailable) {
                Toast.makeText(
                    this@AIChatActivity,
                    "AI features may be limited and required internet access. Please check your internet connection.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@AIChatActivity,
                    "I'm sorry, but I've reached my usage limit for now.",
                    Toast.LENGTH_LONG
                ).show()
            }
            prefs.edit().putBoolean("has_shown_api_message", true).apply()
        }
    }

    private fun scrollToBottom() {
        binding.recyclerView.post {
            if (adapter.itemCount > 0) {
                binding.recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            delay(100)
            if (binding.messageInput.text.isEmpty()) {
                adapter.setSuggestions(defaultSuggestions)
            }
            scrollToBottom()
        }

        // Check if returning from game
        val gameResult = intent.getStringExtra("GAME_RESULT")
        val preserveIcon = intent.getBooleanExtra("PRESERVE_ICON", false)

        if (gameResult != null) {
            // Clear the extra to prevent showing it again
            intent.removeExtra("GAME_RESULT")
            intent.removeExtra("PRESERVE_ICON")

            adapter.hideGameButton()

            // Add game review message
            when (gameResult) {
                "WIN" -> {
                    addBotMessage("Congratulations on winning the game! You've got great reflexes. Want to play again?")
                }
                "LOSE" -> {
                    addBotMessage("Nice try! I've been practicing this game for a while. Want to challenge me again?")
                }
                "TIE" -> {
                    addBotMessage("That was a close game! We're evenly matched. Want to break the tie?")
                }
            }

            // Add game-related suggestions
            adapter.setSuggestions(listOf(
                "Play again",
                "No thanks",
                "Show me my reminders"
            ))
        }
    }

    private fun handleLinkClick(url: String) {
        Log.d("AIChatActivity", "Handling link click: $url")

        if (url.startsWith("reminder:")) {
            val reminderId = url.substringAfter("reminder:")
            Log.d("AIChatActivity", "Extracted reminder ID: $reminderId")

            val reminderData = pendingReminders[reminderId]

            if (reminderData != null) {
                Log.d("AIChatActivity", "Found reminder data: $reminderData")

                try {
                    // Create intent to launch CreateReminder activity
                    val intent = Intent(this, CreateReminder::class.java).apply {
                        putExtra(CreateReminder.EXTRA_TITLE, reminderData.title)
                        putExtra(CreateReminder.EXTRA_DESCRIPTION, reminderData.description)
                        putExtra(CreateReminder.EXTRA_TIMESTAMP, reminderData.timestamp)
                        putExtra(CreateReminder.EXTRA_CATEGORY, reminderData.category)
                        putExtra(CreateReminder.EXTRA_FROM_AI, true)

                        // Set the action and data for the intent filter
                        action = Intent.ACTION_VIEW
                        data = android.net.Uri.parse("reminder:$reminderId")

                        // Add flags to create a new task
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }

                    Log.d("AIChatActivity", "Starting CreateReminder activity with intent: $intent")
                    startActivity(intent)


                    pendingReminders.remove(reminderId)

                    addBotMessage("I've opened the reminder for you to review and save.")
                } catch (e: Exception) {
                    Log.e("AIChatActivity", "Error launching CreateReminder activity", e)
                    Toast.makeText(this, "Error opening reminder: ${e.message}", Toast.LENGTH_SHORT).show()
                    addBotMessage("Sorry, I couldn't open the reminder creation screen. Please try again.")
                }
            } else {
                Log.e("AIChatActivity", "Reminder data not found for ID: $reminderId")
                addBotMessage("Sorry, I couldn't find that reminder. Please try creating it again.")
            }
        } else {
            Log.d("AIChatActivity", "URL is not a reminder link: $url")
        }
    }
}