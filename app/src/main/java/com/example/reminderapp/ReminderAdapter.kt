package com.example.reminderapp


import android.content.Context
import android.graphics.Color
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.example.reminderapp.databinding.ItemReminderBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.TimeUnit
import android.widget.PopupMenu
import android.content.Intent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.graphics.drawable.GradientDrawable
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.recyclerview.widget.DiffUtil
import android.os.Handler
import android.os.Looper
import android.app.Activity
import androidx.core.util.lruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.BlurMaskFilter
import android.graphics.drawable.LayerDrawable

class ReminderAdapter (private val listener: OnReminderActionListener? = null) : RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder>() {
    private val reminders = mutableListOf<Reminder>()
    private val countdownTimers = mutableMapOf<String, CountDownTimer>()
    private val reminderRepository = ReminderRepository()
    private var highlightedReminderId: String? = null

    // Add caching for improved view performance
    private val backgroundCache = androidx.collection.LruCache<String, Drawable>(50)
    private val handler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    // Add debounced update mechanism
    private var pendingUpdate: List<Reminder>? = null
    private var updateScheduled = false
    private val updateDelayMs = 200L // Debounce delay

    // Background animation frame limiter
    private var lastAnimationUpdateTime = mutableMapOf<String, Long>()
    private val ANIMATION_FRAME_LIMIT_MS = 200 // Update background at most 5 times per second

    interface OnReminderActionListener {
        fun onReminderDeleted()
    }
    companion object {
        private const val FADE_SPEED = 300.0
        private const val FADE_MIN_ALPHA = 0.3f
        private const val FADE_MAX_ALPHA = 1.0f
        private const val URGENCY_THRESHOLD = 0.8f
    }

    fun getHighlightedReminderId(): String? = highlightedReminderId

    // Add method to highlight specific reminder
    fun highlightReminder(reminderId: String) {
        highlightedReminderId = reminderId
        notifyDataSetChanged()
    }

    // Helper method for safely showing toasts
    private fun safeShowToast(context: Context, message: String) {
        try {
            if (context is Activity && (context.isFinishing || context.isDestroyed)) {
                Log.d("ReminderAdapter", "Can't show toast - activity finishing/destroyed: $message")
                return
            }

            Handler(Looper.getMainLooper()).post {
                try {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("ReminderAdapter", "Error showing toast: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("ReminderAdapter", "Error preparing toast: ${e.message}")
        }
    }

    private fun updateProgressBackground(
        startTime: Long,
        endTime: Long,
        targetColor: Int,
        view: View,
        reminderId: String
    ) {
        // Check if should update this frame (limit animation frame rate)
        val currentTime = System.currentTimeMillis()
        val lastUpdate = lastAnimationUpdateTime[reminderId] ?: 0L

        if (currentTime - lastUpdate < ANIMATION_FRAME_LIMIT_MS) {
            return
        }

        lastAnimationUpdateTime[reminderId] = currentTime

        // Check cached drawable
        val cacheKey = "${reminderId}_${currentTime / 500}" // Change every 500ms for animation

        // Use cached background if available and recent
        backgroundCache.get(cacheKey)?.let { cachedDrawable ->
            view.background = cachedDrawable
            return
        }
        class FrostedGlassProgressDrawable(
            private val reminderDate: Long,
            private val color: Int,
            private val reminderId: String
        ) : Drawable() {
            private val basePaint = Paint().apply {
                color = Color.WHITE // White base background
                style = Paint.Style.FILL
                alpha = 255
            }

            private val glassPaint = Paint().apply {
                color = this@FrostedGlassProgressDrawable.color
                style = Paint.Style.FILL
                alpha = 180 // Semi-transparent for frosted effect
            }

            private val highlightPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                alpha = 80 // transparent for glass highlight
            }

            private val borderPaint = Paint().apply {
                color = this@FrostedGlassProgressDrawable.color
                style = Paint.Style.STROKE
                strokeWidth = 8f
                alpha = 200
            }

            private val blurMaskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)

            override fun draw(canvas: Canvas) {
                val width = bounds.width()
                val height = bounds.height()

                val now = System.currentTimeMillis()
                val storedStartTime = ReminderApplication.reminderStartTimes[reminderId] ?: now
                val totalDuration = reminderDate - storedStartTime
                val elapsedTime = now - storedStartTime
                val progress = (elapsedTime.toFloat() / totalDuration).coerceIn(0f, 1f)

                // Draw white background
                canvas.drawColor(Color.WHITE)

                // Draw the frosted glass effect (blurred semi-transparent color)
                if (progress > 0) {

                    glassPaint.maskFilter = blurMaskFilter

                    // Apply pulsing transparency when approaching deadline
                    if (progress > URGENCY_THRESHOLD) {
                        val fadeAlpha = (Math.sin(now / FADE_SPEED) *
                                ((FADE_MAX_ALPHA - FADE_MIN_ALPHA) / 2) +
                                (FADE_MAX_ALPHA + FADE_MIN_ALPHA) / 2).toFloat()
                        glassPaint.alpha = (fadeAlpha * 180).toInt()
                    } else {
                        glassPaint.alpha = 180
                    }

                    canvas.drawRect(0f, 0f, width * progress, height.toFloat(), glassPaint)

                    highlightPaint.alpha = 40
                    canvas.drawRect(0f, 0f, width * progress, height * 0.15f, highlightPaint)

                    highlightPaint.alpha = 25
                    canvas.drawRect(0f, height * 0.4f, width * progress, height * 0.5f, highlightPaint)
                }

                // Draw pulsing border at progress edge
                val borderAlpha = (Math.sin(now / 200.0) * 127 + 128).toInt()
                borderPaint.alpha = borderAlpha
                canvas.drawLine(
                    width * progress, 0f,
                    width * progress, height.toFloat(),
                    borderPaint
                )

                invalidateSelf()
            }

            override fun setAlpha(alpha: Int) {
                glassPaint.alpha = alpha
                borderPaint.alpha = alpha
            }

            override fun setColorFilter(filter: ColorFilter?) {
                glassPaint.colorFilter = filter
                borderPaint.colorFilter = filter
            }

            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }

        view.background = FrostedGlassProgressDrawable(endTime, targetColor, reminderId)
    }


    // Modify updateReminders to be more aggressive with edited items
    fun updateReminders(newReminders: List<Reminder>) {
        // Debounce updates to prevent rapid consecutive calls
        pendingUpdate = newReminders

        if (!updateScheduled) {
            updateScheduled = true
            handler.postDelayed({
                processPendingUpdate()
                updateScheduled = false
            }, updateDelayMs)
        }
    }

    private fun processPendingUpdate() {
        val reminderUpdate = pendingUpdate ?: return

        coroutineScope.launch {
        val highlightedId = highlightedReminderId
        val currentTime = System.currentTimeMillis()

        // Create a map of existing reminders for state preservation
        val existingReminderMap = reminders.associateBy { it.id }

            // Sort reminders: upcoming first (sorted by nearest), then expired (sorted by most recent)
            val sortedReminders = reminderUpdate.sortedWith { a, b ->
                val timeRemainingA = a.date - currentTime
                val timeRemainingB = b.date - currentTime

            when {
                // If both are upcoming or both are expired, sort by time
                (timeRemainingA > 0 && timeRemainingB > 0) ||
                        (timeRemainingA <= 0 && timeRemainingB <= 0) ->
                    timeRemainingA.compareTo(timeRemainingB)

                timeRemainingA > 0 -> -1
                else -> 1
            }
        }

            val newReminderIds = sortedReminders.map { it.id }.toSet()

            val diffResult = calculateDiff(reminders, sortedReminders)

            withContext(Dispatchers.Main) {
                // Cancel existing timers for reminders that are no longer present
                countdownTimers.entries.removeAll { entry ->
                    if (!newReminderIds.contains(entry.key)) {
                        entry.value.cancel()
                        true
                    } else false
                }

                sortedReminders.forEach { reminder ->

                    val existingReminder = existingReminderMap[reminder.id]

                    // Reset start time if:
                    // 1. It's a new reminder (not in the map)
                    // 2. The reminder's due date has changed (edited)
                    if (!ReminderApplication.reminderStartTimes.containsKey(reminder.id) ||
                        (existingReminder != null && existingReminder.date != reminder.date)) {

                        val totalTimeUntilReminder = reminder.date - currentTime
                        val elapsedTime = (totalTimeUntilReminder * 0.2).toLong()
                        ReminderApplication.reminderStartTimes[reminder.id] = currentTime - elapsedTime
                    }
                }

                reminders.clear()
                reminders.addAll(sortedReminders)

                if (highlightedId != null && sortedReminders.any { it.id == highlightedId }) {
                    highlightedReminderId = highlightedId
                }

                diffResult.dispatchUpdatesTo(this@ReminderAdapter)
            }
        }
    }

    private fun calculateDiff(oldList: List<Reminder>, newList: List<Reminder>): DiffUtil.DiffResult {
        return DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = newList.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldList[oldItemPosition].id == newList[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = oldList[oldItemPosition]
                val new = newList[newItemPosition]
                return old.id == new.id &&
                        old.date == new.date &&
                        old.color == new.color &&
                        old.isRecurring == new.isRecurring
            }
        })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val binding = ItemReminderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReminderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        holder.bind(reminders[position])
    }

    override fun getItemCount(): Int = reminders.size

    override fun onViewRecycled(holder: ReminderViewHolder) {
        super.onViewRecycled(holder)
        val reminder = reminders.getOrNull(holder.adapterPosition) ?: return
        countdownTimers[reminder.id]?.cancel()
        countdownTimers.remove(reminder.id)
    }


    inner class ReminderViewHolder(private val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(reminder: Reminder) {
            binding.apply {
                tvTitle.text = reminder.title
                // Set emoji text or hide it if empty
                if (reminder.emoji.isNotEmpty()) {
                    ivIcon.text = reminder.emoji
                    ivIcon.visibility = View.VISIBLE
                } else {
                    ivIcon.visibility = View.GONE
                }


                // Handle repeat option
                repeatIndicator.apply {
                    if (reminder.repeatOption != Reminder.REPEAT_NEVER) {
                        val repeatText = when (reminder.repeatOption) {
                            Reminder.REPEAT_MINUTE -> "Every Minute"
                            Reminder.REPEAT_HOUR -> "Every Hour"
                            Reminder.REPEAT_DAY -> "Every Day"
                            Reminder.REPEAT_WEEK -> "Every Week"
                            Reminder.REPEAT_MONTH -> "Every Month"
                            Reminder.REPEAT_YEAR -> "Every Year"
                            else -> "Repeating"
                        }
                        binding.repeatText.text = repeatText
                        visibility = View.VISIBLE
                    } else {
                        visibility = View.GONE
                    }
                }

                // Format and set time
                val zoneId = ZoneId.of("Asia/Kuala_Lumpur")
                val reminderDateTime = Instant.ofEpochMilli(reminder.date)
                    .atZone(zoneId)

                // Format date and time
                reminderTime.text = reminderDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                reminderDate.text = reminderDateTime.format(DateTimeFormatter.ofPattern("d MMM yy"))

                // Add visual indicator for expired reminders
                val currentTime = System.currentTimeMillis()
                val isExpired = reminder.date < currentTime

                if (isExpired) {
                    tvCountdown.setTextColor(Color.RED)
                    tvCountdown.text = "Expired"
                    root.alpha = 0.7f

                    // Apply frosted glass effect for expired reminders
                    val drawable = GradientDrawable()
                    drawable.cornerRadius = 16f
                    // Use a semi-transparent version of the color for frosted look
                    val expiredColor = ColorUtils.setAlphaComponent(reminder.color, 180)
                    drawable.setColor(expiredColor)
                    reminderBackground.background = drawable
                } else {
                    tvCountdown.setTextColor(Color.BLACK)
                    root.alpha = 1.0f
                    setupProgressAndCountdown(reminder, binding)
                }

                // Create a more contrasting version of the selected color for labels
                val labelColor = createContrastingColor(reminder.color)
                val textColor = getContrastingTextColor(labelColor)

                // Apply the contrasting color to labels with rounded background
                reminderTime.apply {
                    background = createRoundedBackground(labelColor)
                    setTextColor(textColor)
                }

                reminderDate.apply {
                    background = createRoundedBackground(labelColor)
                    setTextColor(textColor)
                }


                repeatIndicator.apply {
                    background = createRoundedBackground(labelColor)
                }
                repeatText.setTextColor(textColor)


                menuButton.setOnClickListener { view ->
                    showPopupMenu(view, reminder)
                }

                if (reminder.id == highlightedReminderId) {
                    root.alpha = 1f
                    root.animate()
                        .alpha(0.6f)
                        .setDuration(1000)
                        .withEndAction {
                            root.animate()
                                .alpha(1f)
                                .setDuration(1000)
                                .withEndAction {
                                    highlightedReminderId = null
                                }
                        }
                        .start()
                } else {
                    root.alpha = if (isExpired) 0.7f else 1f
                }
                // Apply transparency for recurring instances
                if (reminder.isRecurring && !isExpired) {
                    val alpha = 0.5f
                    if (reminderBackground.background is GradientDrawable) {
                        // Keep white background but reduce the overall card opacity
                        root.alpha = 0.85f
                    }
                }
            }
        }

        private fun setupProgressAndCountdown(reminder: Reminder, binding: ItemReminderBinding) {
            val startTime = ReminderApplication.reminderStartTimes.getOrPut(reminder.id) {
                System.currentTimeMillis()
            }

            // Apply rounded corners to the reminder background for a more modern look
            try {
                val drawable = GradientDrawable()
                drawable.cornerRadius = 16f
                drawable.setColor(Color.WHITE)
                binding.reminderBackground.background = drawable
            } catch (e: Exception) {
                Log.e("ReminderAdapter", "Error setting rounded corners", e)
            }

            countdownTimers[reminder.id]?.cancel()
            val timeRemaining = reminder.date - System.currentTimeMillis()

            if (timeRemaining > 0) {
                // Calculate initial display value
                binding.tvCountdown.text = formatTimeRemaining(timeRemaining)

                // Set longer intervals for updates when time is far away
                val updateInterval = when {
                    timeRemaining > TimeUnit.DAYS.toMillis(1) -> 60000L // Update once per minute for days away
                    timeRemaining > TimeUnit.HOURS.toMillis(1) -> 10000L // Update every 10 seconds for hours away
                    else -> 1000L // Update every second when less than an hour away
                }

                val timer = object : CountDownTimer(timeRemaining, updateInterval) {
                    private var lastTimerUpdate = 0L

                    override fun onTick(millisUntilFinished: Long) {
                        val currentTime = System.currentTimeMillis()

                        // Update countdown text
                        binding.tvCountdown.text = formatTimeRemaining(millisUntilFinished)

                        // Only update progress drawing at a reasonable framerate
                        if (currentTime - lastTimerUpdate > 250) { // Max 4 updates per second
                            updateProgressBackground(
                                startTime,
                                reminder.date,
                                reminder.color,
                                binding.reminderBackground,
                                reminder.id
                            )
                            lastTimerUpdate = currentTime
                        }
                    }

                    override fun onFinish() {
                        binding.tvCountdown.text = "Time is up!"

                        // When time is up, apply a finished state with frosted glass look
                        val drawable = GradientDrawable()
                        drawable.cornerRadius = 16f
                        // Use a semi-transparent version of the color for frosted look
                        val finishedColor = ColorUtils.setAlphaComponent(reminder.color, 180)
                        drawable.setColor(finishedColor)
                        binding.reminderBackground.background = drawable

                        ReminderApplication.reminderStartTimes.remove(reminder.id)
                    }
                }
                timer.start()
                countdownTimers[reminder.id] = timer
            } else {
                binding.tvCountdown.text = "Time is up!"

                val drawable = GradientDrawable()
                drawable.cornerRadius = 16f
                val expiredColor = ColorUtils.setAlphaComponent(reminder.color, 180)
                drawable.setColor(expiredColor)
                binding.reminderBackground.background = drawable

                ReminderApplication.reminderStartTimes.remove(reminder.id)
            }
        }

        private fun formatTimeRemaining(millisUntilFinished: Long): String {
            val days = TimeUnit.MILLISECONDS.toDays(millisUntilFinished)
            val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished) % 24
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60

            return when {
                days > 0 -> String.format("%d day%s, %d hr%s",
                    days, if (days > 1) "s" else "",
                    hours, if (hours > 1) "s" else "")
                hours > 0 -> String.format("%d hr%s, %d min%s",
                    hours, if (hours > 1) "s" else "",
                    minutes, if (minutes > 1) "s" else "")
                minutes > 0 -> String.format("%d min%s, %d sec%s",
                    minutes, if (minutes > 1) "s" else "",
                    seconds, if (seconds > 1) "s" else "")
                else -> String.format("%d sec%s",
                    seconds, if (seconds > 1) "s" else "")
            }
        }

        private fun showPopupMenu(view: View, reminder: Reminder) {
            val popup = PopupMenu(view.context, view)
            popup.menuInflater.inflate(R.menu.reminder_item_menu, popup.menu)

            // Only show complete option for non-expired reminders
            val isExpired = reminder.date < System.currentTimeMillis()
            popup.menu.findItem(R.id.action_complete)?.isVisible = !isExpired

            // Extract the original ID from occurrence ID if this is a recurring reminder
            val isOccurrence = reminder.id.contains("_occurrence_")
            val originalId = if (isOccurrence) {
                reminder.id.split("_occurrence_").firstOrNull() ?: reminder.id
            } else {
                reminder.id
            }



            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_complete -> {
                        completeReminder(view.context, reminder)
                        true
                    }
                    R.id.action_edit -> {
                        val intent = Intent(view.context, CreateReminder::class.java).apply {

                            putExtra("REMINDER_ID", originalId)
                            putExtra("IS_EDIT", true)


                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        view.context.startActivity(intent)
                        true
                    }
                    R.id.action_delete -> {
                        showDeleteConfirmationDialog(view.context, reminder, originalId, isOccurrence)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        private fun completeReminder(context: Context, reminder: Reminder) {
            reminderRepository.cancelReminderAlarms(reminder.id, context)
            // Mark the reminder as completed in the repository
            reminderRepository.completeReminder(reminder.id, reminder.userId) { success ->
                if (success) {
                    // Use the callback to refresh the list instead of manual updates
                    listener?.onReminderDeleted()
                    safeShowToast(context, "Reminder completed")
                } else {
                    safeShowToast(context, "Failed to complete reminder")
                }
            }
        }

        private fun showDeleteConfirmationDialog(context: Context, reminder: Reminder, originalId: String, isOccurrence: Boolean) {
            val message = if (isOccurrence) {
                "This is a recurring reminder. Do you want to delete only this occurrence or all occurrences?"
            } else {
                "Are you sure you want to delete this reminder?"
            }

            Log.d("ReminderAdapter", "Deleting reminder. ID: ${reminder.id}, Original ID: $originalId, IsOccurrence: $isOccurrence")

            val builder = MaterialAlertDialogBuilder(context)
                .setTitle("Delete Reminder")
                .setMessage(message)

            if (isOccurrence) {
                // For occurrences, offer options to delete just this or all
                builder.setPositiveButton("Delete All") { _, _ ->
                    deleteReminder(context, originalId, reminder.userId)
                }
                    .setNeutralButton("Cancel", null)
            } else {
                // For regular reminders, simple delete confirmation
                builder.setPositiveButton("Delete") { _, _ ->
                    deleteReminder(context, originalId, reminder.userId)
                }
                    .setNegativeButton("Cancel", null)
            }

            builder.show()
        }

        private fun deleteReminder(context: Context, reminderId: String, userId: String) {
            reminderRepository.deleteReminder(reminderId, userId) { success: Boolean ->
                if (success) {
                    // Find and remove all occurrences of this reminder from the adapter
                    val originalIdPrefix = reminderId + "_occurrence_"
                    val positionsToRemove = reminders.mapIndexedNotNull { index, item ->
                        if (item.id == reminderId || item.id.startsWith(originalIdPrefix)) index else null
                    }

                    Log.d("ReminderAdapter", "Deleting reminder ID: $reminderId and ${positionsToRemove.size} occurrences")

                    // If deleting the original, remove it and all occurrences
                    if (positionsToRemove.isNotEmpty()) {

                        for (position in positionsToRemove.sortedDescending()) {
                            if (position < reminders.size) {
                                reminders.removeAt(position)
                                notifyItemRemoved(position)
                            }
                        }

                        if (positionsToRemove.isNotEmpty()) {
                            val minPosition = positionsToRemove.minOrNull() ?: 0
                            notifyItemRangeChanged(minPosition, reminders.size - minPosition)
                        }
                    }

                    listener?.onReminderDeleted()
                    safeShowToast(context, "Reminder deleted")
                } else {
                    safeShowToast(context, "Failed to delete reminder")
                }
            }
        }

        private fun createContrastingColor(baseColor: Int): Int {
            // Create a color that works well with the frosted glass effect
            // Extract HSL components
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(baseColor, hsl)

            // Adjust lightness based on the base color
            if (hsl[2] > 0.7f) {
                // For very light colors, make them darker and more saturated
                hsl[1] = (hsl[1] * 1.3f).coerceIn(0.2f, 0.8f) // Increase saturation slightly
                hsl[2] = (hsl[2] * 0.75f).coerceIn(0.3f, 0.6f) // Darken
            } else if (hsl[2] < 0.3f) {
                // For very dark colors, make them lighter but maintain saturation
                hsl[1] = (hsl[1] * 0.9f).coerceIn(0.1f, 0.7f) // Reduce saturation slightly
                hsl[2] = (hsl[2] * 2.2f).coerceIn(0.4f, 0.65f) // Lighten
            } else {
                // For mid-range colors, make minor adjustments for better frosted glass look
                hsl[1] = (hsl[1] * 1.1f).coerceIn(0.15f, 0.6f) // Mild saturation adjustment

                // Keep lightness in middle range for best frosted effect
                if (hsl[2] < 0.4f) {
                    hsl[2] = (hsl[2] * 1.3f).coerceIn(0.4f, 0.6f)
                } else if (hsl[2] > 0.6f) {
                    hsl[2] = (hsl[2] * 0.85f).coerceIn(0.4f, 0.6f)
                }
            }

            return ColorUtils.HSLToColor(hsl)
        }

        private fun getContrastingTextColor(backgroundColor: Int): Int {
            // Calculate relative luminance
            val luminance = ColorUtils.calculateLuminance(backgroundColor)

            // For frosted glass effect, higher contrast
            return if (luminance > 0.45) {
                // For light backgrounds, use a darker text that's not pure black
                ColorUtils.setAlphaComponent(Color.BLACK, 230)
            } else {
                // For dark backgrounds, use slightly off-white for a softer look
                ColorUtils.setAlphaComponent(Color.WHITE, 245)
            }
        }

        private fun createRoundedBackground(color: Int): Drawable {
            try {
                // Create base drawable with rounded corners
                val baseDrawable = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(ColorUtils.setAlphaComponent(color, 180))
                }

                // Create highlight drawable for glass reflection effect
                val highlightDrawable = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(Color.WHITE)
                    alpha = 60
                }

                // Combine into a layer drawable
                val layers = arrayOf(baseDrawable, highlightDrawable)
                val layerDrawable = LayerDrawable(layers)

                // Position the highlight at the top third only
                layerDrawable.setLayerInset(1, 0, 0, 0, layerDrawable.intrinsicHeight * 2 / 3)

                return layerDrawable
            } catch (e: Exception) {
                Log.e("ReminderAdapter", "Error creating layered label background", e)

                // Fallback to simple rounded drawable if there's an error
                val drawable = GradientDrawable()
                drawable.cornerRadius = 12f
                drawable.setColor(color)
                return drawable
            }
        }

    }
}
