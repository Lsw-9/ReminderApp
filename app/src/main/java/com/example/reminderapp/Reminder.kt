package com.example.reminderapp

import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class Reminder(
    var id: String = "",
    var userId: String = "",
    var title: String = "",
    var description: String = "",
    val category: String = "",
    var date: Long = 0,  // Store as timestamp (milliseconds since epoch)
    var offsetMinutes: Int = 10,  // Default to 10 minutes before
    val emoji: String = "⏰",
    var color: Int = 0,
    var isCompleted: Boolean = false,
    var repeatOption: String = REPEAT_NEVER,
    var isRecurring: Boolean = false  // Flag to indicate if this is a recurring instance
) {
    companion object {
        const val REPEAT_NEVER = "Never"
        const val REPEAT_MINUTE = "Every Minute"
        const val REPEAT_HOUR = "Every Hour"
        const val REPEAT_DAY = "Every Day"
        const val REPEAT_WEEK = "Every Week"
        const val REPEAT_MONTH = "Every Month"
        const val REPEAT_YEAR = "Every Year"

        val REPEAT_OPTIONS = listOf(
            REPEAT_NEVER,
            REPEAT_MINUTE,
            REPEAT_HOUR,
            REPEAT_DAY,
            REPEAT_WEEK,
            REPEAT_MONTH,
            REPEAT_YEAR
        )
    }


    // Empty constructor for Firestore
    constructor() : this("", "", "", "","", 0L, 10, "⏰", 0, false, REPEAT_NEVER, false)

    // Convert timestamp to LocalDate
    fun getLocalDate(): LocalDate {
        return Instant.ofEpochMilli(date)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }

    // Get next occurrence based on repeat option
    fun getNextOccurrence(): Long {
        if (repeatOption == REPEAT_NEVER) return date

        val zoneId = ZoneId.of("Asia/Kuala_Lumpur")
        val currentDate = Instant.ofEpochMilli(date).atZone(zoneId)

        val nextDate = when (repeatOption) {
            REPEAT_MINUTE -> currentDate.plusMinutes(1)
            REPEAT_HOUR -> currentDate.plusHours(1)
            REPEAT_DAY -> currentDate.plusDays(1)
            REPEAT_WEEK -> currentDate.plusWeeks(1)
            REPEAT_MONTH -> currentDate.plusMonths(1)
            REPEAT_YEAR -> currentDate.plusYears(1)
            else -> currentDate
        }

        return nextDate.toInstant().toEpochMilli()
    }

    fun generateNextOccurrences(): List<Reminder> {
        val occurrences = mutableListOf<Reminder>()
        val zoneId = ZoneId.of("Asia/Kuala_Lumpur")

        // Convert repeat option to uppercase for case-insensitive comparison
        val normalizedRepeatOption = repeatOption.uppercase()
        val normalizedOptions = REPEAT_OPTIONS.map { it.uppercase() }

        if (normalizedRepeatOption == REPEAT_NEVER.uppercase() || normalizedRepeatOption !in normalizedOptions) {
            Log.d("Reminder", "No valid repeat option set ($repeatOption), returning empty list")
            return emptyList()
        }

        // Convert the reminder's date to ZonedDateTime
        var currentDateTime = Instant.ofEpochMilli(date)
            .atZone(zoneId)

        // If the original date is in the past, start from now but maintain the same time
        if (currentDateTime.isBefore(ZonedDateTime.now(zoneId))) {
            currentDateTime = ZonedDateTime.now(zoneId)
                .withHour(currentDateTime.hour)
                .withMinute(currentDateTime.minute)
                .withSecond(currentDateTime.second)
        }

        // Calculate end date (12 months from now)
        val endDateTime = ZonedDateTime.now(zoneId)
            .plusMonths(12)

        var safetyCounter = 0
        val maxOccurrences = when (normalizedRepeatOption) {
            REPEAT_MINUTE.uppercase() -> 60  // 1 hour worth of minutes
            REPEAT_HOUR.uppercase() -> 24    // 1 day worth of hours
            REPEAT_DAY.uppercase() -> 31     // 1 month worth of days
            REPEAT_WEEK.uppercase() -> 53    // ~1 year worth of weeks
            REPEAT_MONTH.uppercase() -> 12   // 1 year worth of months
            REPEAT_YEAR.uppercase() -> 2     // 2 years
            else -> 53
        }

        // First advance the date by one period to avoid duplicating the original reminder
        currentDateTime = when (normalizedRepeatOption) {
            REPEAT_MINUTE.uppercase() -> currentDateTime.plusMinutes(1)
            REPEAT_HOUR.uppercase() -> currentDateTime.plusHours(1)
            REPEAT_DAY.uppercase() -> currentDateTime.plusDays(1)
            REPEAT_WEEK.uppercase() -> currentDateTime.plusWeeks(1)
            REPEAT_MONTH.uppercase() -> currentDateTime.plusMonths(1)
            REPEAT_YEAR.uppercase() -> currentDateTime.plusYears(1)
            else -> currentDateTime.plusWeeks(1) // Default to weekly if unknown
        }

        while (currentDateTime.isBefore(endDateTime) && safetyCounter < maxOccurrences) {
            val occurrenceTimestamp = currentDateTime.toInstant().toEpochMilli()

            // Create occurrence with the correct repeat option
            val occurrenceId = "${id}_occurrence_${occurrenceTimestamp}"
            val occurrenceReminder = copy(
                id = occurrenceId,
                date = occurrenceTimestamp,
                isRecurring = true,
                repeatOption = repeatOption  // Keep the original repeat option
            )
            occurrences.add(occurrenceReminder)

            // Advance the date based on repeat option
            currentDateTime = when (normalizedRepeatOption) {
                REPEAT_MINUTE.uppercase() -> currentDateTime.plusMinutes(1)
                REPEAT_HOUR.uppercase() -> currentDateTime.plusHours(1)
                REPEAT_DAY.uppercase() -> currentDateTime.plusDays(1)
                REPEAT_WEEK.uppercase() -> currentDateTime.plusWeeks(1)
                REPEAT_MONTH.uppercase() -> currentDateTime.plusMonths(1)
                REPEAT_YEAR.uppercase() -> currentDateTime.plusYears(1)
                else -> currentDateTime.plusWeeks(1) // Default to weekly if unknown
            }

            safetyCounter++
        }

        Log.d("Reminder", "Generated ${occurrences.size} occurrences for reminder $id")
        return occurrences
    }



fun isSoon(): Boolean {
        val now = Instant.now().toEpochMilli()
        val timeDifference = date - now
        return timeDifference > 0 && timeDifference <= 24 * 60 * 60 * 1000 // Within 24 hours
    }

    fun isFuture(): Boolean {
        val now = Instant.now().toEpochMilli()
        return date > now + 24 * 60 * 60 * 1000 // More than 24 hours in the future
    }

    fun isPrevious(): Boolean {
        val now = Instant.now().toEpochMilli()
        return date < now // Past reminders
    }
}
