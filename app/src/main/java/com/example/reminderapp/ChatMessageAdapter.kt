package com.example.reminderapp

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.reminderapp.R
import com.example.reminderapp.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.text.Html
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import com.example.reminderapp.game.PaddleGameActivity
import com.google.android.material.button.MaterialButton

/**
 * Adapter for displaying chat messages in a RecyclerView
 */
class ChatMessageAdapter(
    private val onSuggestionClick: (String) -> Unit,
    private val onLinkClick: (String) -> Unit
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatMessageDiffCallback()) {

    private var suggestions = listOf<String>()
    private var isTyping = false
    private var showGameButton = false

    companion object {
        private const val VIEW_TYPE_USER_MESSAGE = 0
        private const val VIEW_TYPE_BOT_MESSAGE = 1
        private const val VIEW_TYPE_TYPING_INDICATOR = 2
        private const val VIEW_TYPE_SUGGESTION_CHIPS = 3
        private const val VIEW_TYPE_GAME_BUTTON = 4
    }

    override fun getItemCount(): Int {
        // Base count is the number of messages
        var count = super.getItemCount()

        // Add 1 for typing indicator if it's showing
        if (isTyping) count++

        // Add 1 for suggestion chips if there are any
        if (suggestions.isNotEmpty()) count++

        // Add 1 for game button if it's showing
        if (showGameButton) count++

        return count
    }

    override fun getItemViewType(position: Int): Int {
        val messageCount = super.getItemCount()

        return when {
            // If position is within the range of actual messages
            position < messageCount -> {
                val message = getItem(position)
                if (message.isFromUser) VIEW_TYPE_USER_MESSAGE else VIEW_TYPE_BOT_MESSAGE
            }
            // If game button is showing and this position is for it
            showGameButton && position == messageCount -> VIEW_TYPE_GAME_BUTTON
            // If typing indicator is showing and this position is for it
            isTyping && position == messageCount + (if (showGameButton) 1 else 0) -> VIEW_TYPE_TYPING_INDICATOR
            // Otherwise, must be for suggestion chips
            else -> VIEW_TYPE_SUGGESTION_CHIPS
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER_MESSAGE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_user, parent, false)
                UserMessageViewHolder(view)
            }
            VIEW_TYPE_BOT_MESSAGE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_bot, parent, false)
                BotMessageViewHolder(view)
            }
            VIEW_TYPE_TYPING_INDICATOR -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_typing_indicator, parent, false)
                TypingIndicatorViewHolder(view)
            }
            VIEW_TYPE_SUGGESTION_CHIPS -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_suggestion_chips, parent, false)
                SuggestionChipsViewHolder(view, onSuggestionClick)
            }
            VIEW_TYPE_GAME_BUTTON -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_game_button, parent, false)
                GameButtonViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val messageCount = super.getItemCount()

        when (holder) {
            is UserMessageViewHolder -> {
                if (position < messageCount) {
                    holder.bind(getItem(position))
                }
            }
            is BotMessageViewHolder -> {
                if (position < messageCount) {
                    holder.bind(getItem(position))
                }
            }
            is SuggestionChipsViewHolder -> {
                holder.bind(suggestions)
            }
            // No binding needed for typing indicator or game button
        }
    }

    inner class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)

        fun bind(message: ChatMessage) {
            messageText.text = message.message
        }
    }

    inner class BotMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val botIcon: ImageView = itemView.findViewById(R.id.botIcon)

        fun bind(message: ChatMessage) {
            if (message.hasLinks) {
                messageText.text = Html.fromHtml(message.message, Html.FROM_HTML_MODE_COMPACT)
                messageText.movementMethod = LinkMovementMethod.getInstance()

                // Set up a custom link handler for reminder links
                messageText.setOnClickListener(null) // Remove previous click listener

                // Use LinkMovementMethod with custom URLSpan handling
                messageText.movementMethod = object : LinkMovementMethod() {
                    override fun onTouchEvent(widget: TextView, buffer: android.text.Spannable, event: android.view.MotionEvent): Boolean {
                        val action = event.action

                        if (action == android.view.MotionEvent.ACTION_UP) {
                            val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
                            val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY

                            val layout = widget.layout
                            val line = layout.getLineForVertical(y)
                            val off = layout.getOffsetForHorizontal(line, x.toFloat())

                            val links = buffer.getSpans(off, off, android.text.style.URLSpan::class.java)

                            if (links.isNotEmpty()) {
                                val url = links[0].url
                                android.util.Log.d("ChatMessageAdapter", "Link clicked: $url")
                                if (url.startsWith("reminder:")) {
                                    android.util.Log.d("ChatMessageAdapter", "Handling reminder link: $url")
                                    onLinkClick(url)
                                    return true
                                }
                                return super.onTouchEvent(widget, buffer, event)
                            }
                        }

                        return super.onTouchEvent(widget, buffer, event)
                    }
                }
            } else {
                messageText.text = message.message
                Linkify.addLinks(messageText, Linkify.WEB_URLS)
            }

            botIcon.setImageResource(R.drawable.ic_chat_bot_28)
        }
    }

    inner class TypingIndicatorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dot1: View = itemView.findViewById(R.id.dot1)
        private val dot2: View = itemView.findViewById(R.id.dot2)
        private val dot3: View = itemView.findViewById(R.id.dot3)
        private val handler = Handler(Looper.getMainLooper())
        private var currentState = 0

        init {
            startDotAnimation()
        }

        private fun startDotAnimation() {
            val runnable = object : Runnable {
                override fun run() {
                    when (currentState) {
                        0 -> {
                            dot1.alpha = 1.0f
                            dot2.alpha = 0.5f
                            dot3.alpha = 0.5f
                        }
                        1 -> {
                            dot1.alpha = 0.5f
                            dot2.alpha = 1.0f
                            dot3.alpha = 0.5f
                        }
                        2 -> {
                            dot1.alpha = 0.5f
                            dot2.alpha = 0.5f
                            dot3.alpha = 1.0f
                        }
                    }

                    currentState = (currentState + 1) % 3
                    handler.postDelayed(this, 300)
                }
            }

            handler.post(runnable)
        }
    }

    inner class SuggestionChipsViewHolder(
        itemView: View,
        private val onSuggestionClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val chipGroup: ChipGroup = itemView.findViewById(R.id.chipGroup)

        fun bind(suggestions: List<String>) {
            chipGroup.removeAllViews()

            for (suggestion in suggestions) {
                val chip = Chip(chipGroup.context).apply {
                    text = suggestion
                    isCheckable = false
                    isClickable = true
                    chipBackgroundColor = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.blue_500)
                    )
                    setTextColor(ContextCompat.getColor(context, android.R.color.white))
                    chipStrokeWidth = 0f
                    elevation = 4f
                    setOnClickListener {
                        onSuggestionClick.invoke(suggestion)
                    }
                }

                chipGroup.addView(chip)
            }
        }
    }

    inner class GameButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val gameButton: MaterialButton = itemView.findViewById(R.id.gameButton)

        init {
            gameButton.setOnClickListener {
                val context = itemView.context
                val intent = Intent(context, PaddleGameActivity::class.java)
                context.startActivity(intent)
            }
        }
    }

    fun showTypingIndicator() {
        isTyping = true
        notifyDataSetChanged()
    }

    fun hideTypingIndicator() {
        isTyping = false
        notifyDataSetChanged()
    }

    fun setSuggestions(suggestions: List<String>) {
        this.suggestions = suggestions
        notifyDataSetChanged()
    }

    fun showGameButton() {
        showGameButton = true
        notifyDataSetChanged()
    }

    fun hideGameButton() {
        if (showGameButton) {
            showGameButton = false
            notifyDataSetChanged()
        }
    }

    override fun submitList(list: List<ChatMessage>?) {
        // Hide game button if more messages are added after it was shown
        if (!list.isNullOrEmpty() && list.size > super.getItemCount() && showGameButton) {
            showGameButton = false
        }
        super.submitList(list)
    }
}

class ChatMessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem == newItem
    }

}
