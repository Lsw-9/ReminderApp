package com.example.reminderapp

/**
 * Model class for chat messages in the AI chat interface
 */
data class ChatMessage(
    val id: String,
    val message: String,
    val timestamp: Long,
    val isFromUser: Boolean,
    val hasLinks: Boolean = false
)