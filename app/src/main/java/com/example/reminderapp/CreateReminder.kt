package com.example.reminderapp


import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.reminderapp.databinding.ActivityCreateReminderBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maltaisn.icondialog.pack.IconPack
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import com.example.reminderapp.R
import android.widget.ArrayAdapter
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.Instant
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
import android.text.InputType
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.emoji2.emojipicker.EmojiPickerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.example.reminderapp.Reminder
import android.os.Handler
import android.os.Looper

class CreateReminder : AppCompatActivity() {
    private lateinit var binding: ActivityCreateReminderBinding
    private val reminderRepository = ReminderRepository()
    private lateinit var auth: FirebaseAuth
    private var selectedDate: Long = 0
    private var selectedEmoji = ""
    private var selectedColor: Int = Color.WHITE
    private var iconPack: IconPack? = null

    private var repeatOption: String = "Never"

    private var isEdit = false
    private var reminderId: String? = null

    private lateinit var categoryAdapter: ArrayAdapter<String>
    private var selectedCategory: String = "No Category"

    private val categoryRepository = CategoryRepository()
    private var reminder = Reminder()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Reminder"

        auth = FirebaseAuth.getInstance()
        setupRepeatSwitch()
        setupClickListeners()
        handleIncomingDate()
        setupCategorySpinner()
        setupReminderOffset()
        checkAlarmPermission()

        updateNetworkStatusIndicator()

        // Initialize icon preview
        binding.iconPreview.text = if (selectedEmoji.isEmpty()) "" else selectedEmoji

        // Initialize color preview
        updateColorPreview(selectedColor)

        isEdit = intent.getBooleanExtra("IS_EDIT", false)
        reminderId = intent.getStringExtra("REMINDER_ID")

        if (isEdit && reminderId != null) {
            loadReminder(reminderId!!)
        }

        // Check if coming from AI Chat
        val fromAI = intent.getBooleanExtra(EXTRA_FROM_AI, false)
        if (fromAI) {
            // Pre-fill fields from AI-generated data
            val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
            val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""
            val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
            val category = intent.getStringExtra(EXTRA_CATEGORY) ?: ""

            binding.etTitle.setText(title)
            binding.etDescription.setText(description)

            // Set date and time
            selectedDate = timestamp
            updateDateTimeDisplay()

            // Store the category to be set later when categoryAdapter is initialized
            if (category.isNotEmpty()) {
                selectedCategory = category
            }

            // Show a toast to confirm
            Toast.makeText(this, "Reminder created from AI assistant. Please review and save.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateNetworkStatusIndicator()
    }

    private fun updateNetworkStatusIndicator() {
        val isOffline = com.example.reminderapp.NetworkUtils.shouldUseOfflineMode(this)

        binding.saveButton.text = if (isOffline) {
            "Save"
        } else {
            "Save"
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun handleIncomingDate() {
        intent.getStringExtra(EXTRA_SELECTED_DATE)?.let { dateStr ->
            try {
                Log.d("CreateReminder", "Handling incoming date string: $dateStr")
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val date = LocalDate.parse(dateStr, formatter)
                val zoneId = ZoneId.of("Asia/Kuala_Lumpur")

                val zonedDateTime = date.atStartOfDay(zoneId)
                selectedDate = zonedDateTime.toInstant().toEpochMilli()

                Log.d("CreateReminder", """
                    Parsed date details:
                    Year: ${date.year}
                    Month: ${date.monthValue}
                    Day: ${date.dayOfMonth}
                    TimeZone: ${zoneId}
                    ZonedDateTime: ${zonedDateTime}
                    Selected timestamp: $selectedDate
                    As Date object: ${Date(selectedDate)}
                """.trimIndent())

                binding.dateText.text = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            } catch (e: Exception) {
                Log.e("CreateReminder", "Error parsing date: $dateStr", e)
            }
        }
    }



    private fun setupClickListeners() {
        binding.apply {
            dateCard.setOnClickListener { showDatePicker() }
            timeCard.setOnClickListener { showTimePicker() }
            iconCard.setOnClickListener { showIconPicker() }
            colorCard.setOnClickListener { showColorPickerDialog() }
            cancelButton.setOnClickListener { finish() }
            saveButton.setOnClickListener { saveReminder() }


        }
    }

    private fun showDatePicker() {
        val zoneId = ZoneId.of("Asia/Kuala_Lumpur")
        val currentDateTime = if (selectedDate > 0) {
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(selectedDate), zoneId)
        } else {
            ZonedDateTime.now(zoneId)
        }

        DatePickerDialog(
            this,
            { _, year, month, day ->
                val newDateTime = currentDateTime
                    .withYear(year)
                    .withMonth(month + 1)
                    .withDayOfMonth(day)

                selectedDate = newDateTime.toInstant().toEpochMilli()
                val selectedDateStr = String.format("%02d/%02d/%d", day, month + 1, year)
                binding.dateText.text = selectedDateStr

                Log.d("CreateReminder", """
                    Date set:
                    Date: $selectedDateStr
                    TimeZone: ${zoneId}
                    ZonedDateTime: ${newDateTime}
                    Selected timestamp: $selectedDate
                    As Date object: ${Date(selectedDate)}
                """.trimIndent())
            },
            currentDateTime.year,
            currentDateTime.monthValue - 1,
            currentDateTime.dayOfMonth
        ).show()
    }


    private fun setupReminderOffset() {
        binding.reminderOffsetSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.reminderOffsetText.visibility = View.VISIBLE
                showReminderOffsetDialog()
            } else {
                binding.reminderOffsetText.visibility = View.VISIBLE
                binding.reminderOffsetText.text = "Off"
                reminder.offsetMinutes = 0
            }
        }

        binding.reminderOffsetText.setOnClickListener {
            if (binding.reminderOffsetSwitch.isChecked) {
                showReminderOffsetDialog()
            }
        }

        // Initialize the offset time if editing a reminder
        if (isEdit && reminderId != null) {
            reminderRepository.getReminder(reminderId!!) { reminder ->
                reminder?.let {
                    binding.reminderOffsetSwitch.isChecked = it.offsetMinutes > 0
                    binding.reminderOffsetText.text = if (it.offsetMinutes > 0) {
                        "${it.offsetMinutes} minutes"
                    } else {
                        "Off"
                    }
                    this.reminder.offsetMinutes = it.offsetMinutes
                }
            }
        } else {
            binding.reminderOffsetText.text = "Off"
            reminder.offsetMinutes = 0
        }
    }

    private fun showTimePicker() {
        val zoneId = ZoneId.of("Asia/Kuala_Lumpur")
        val currentDateTime = if (selectedDate > 0) {
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(selectedDate), zoneId)
        } else {
            ZonedDateTime.now(zoneId)
        }

        Log.d("CreateReminder", "Current ZonedDateTime before time picker: $currentDateTime")

        TimePickerDialog(
            this,
            { _, hour, minute ->
                val newDateTime = currentDateTime
                    .withHour(hour)
                    .withMinute(minute)
                    .withSecond(0)
                    .withNano(0)

                selectedDate = newDateTime.toInstant().toEpochMilli()
                val selectedTime = String.format("%02d:%02d", hour, minute)
                binding.timeText.text = selectedTime

                Log.d("CreateReminder", """
                    Time set:
                    Hour: $hour
                    Minute: $minute
                    TimeZone: ${zoneId}
                    ZonedDateTime: ${newDateTime}
                    Selected timestamp: $selectedDate
                    As Date object: ${Date(selectedDate)}
                """.trimIndent())
            },
            currentDateTime.hour,
            currentDateTime.minute,
            true
        ).show()
    }

    private fun showReminderOffsetDialog() {
        val options = arrayOf("5 minutes", "10 minutes", "30 minutes", "1 hour", "Custom")

        MaterialAlertDialogBuilder(this)
            .setTitle("Remind Before")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setReminderOffset(5)  // 5 minutes
                    1 -> setReminderOffset(10) // 10 minutes
                    2 -> setReminderOffset(30) // 30 minutes
                    3 -> setReminderOffset(60) // 1 hour
                    4 -> showCustomReminderOffsetDialog() // Custom time
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                binding.reminderOffsetSwitch.isChecked = false
                dialog.dismiss()
            }
            .show()
    }

    private fun setReminderOffset(minutes: Int) {
        binding.reminderOffsetText.text = "$minutes minutes"
        reminder.offsetMinutes = minutes
        binding.reminderOffsetSwitch.isChecked = true
    }

    private fun showCustomReminderOffsetDialog() {
        val input = EditText(this).apply {
            hint = "Enter minutes"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Custom Reminder Time")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                val minutes = input.text.toString().toIntOrNull() ?: 0
                if (minutes > 0) {
                    setReminderOffset(minutes)
                } else {
                    Toast.makeText(this, "Invalid time", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                binding.reminderOffsetSwitch.isChecked = false
                dialog.dismiss()
            }
            .setOnCancelListener {
                binding.reminderOffsetSwitch.isChecked = false
            }
            .show()
    }

    private fun showIconPicker() {
        val bottomSheet = BottomSheetDialog(this).apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            window?.setDimAmount(0.5f)
        }

        val view = layoutInflater.inflate(R.layout.bottom_sheet_emoji_picker, null)
        bottomSheet.setContentView(view)

        // Add a "Clear Icon" option
        val clearButton = view.findViewById<Button>(R.id.clearIconButton)
        clearButton?.setOnClickListener {
            selectedEmoji = ""
            binding.iconPreview.text = ""
            bottomSheet.dismiss()
        }

        val emojiPicker = view.findViewById<EmojiPickerView>(R.id.emoji_picker)
        emojiPicker.setOnEmojiPickedListener { emoji ->
            selectedEmoji = emoji.emoji
            binding.iconPreview.text = selectedEmoji
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }


    private fun showColorPickerDialog() {
        ColorPickerDialog.Builder(this)
            .setTitle("Choose Color")
            .setPositiveButton("Select", object : ColorEnvelopeListener {
                override fun onColorSelected(envelope: ColorEnvelope, fromUser: Boolean) {
                    selectedColor = envelope.color
                    updateColorPreview(selectedColor)
                }
            })
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .attachAlphaSlideBar(false)
            .attachBrightnessSlideBar(true)
            .show()
    }

    private fun updateColorPreview(color: Int) {
        // Update both color previews with rounded corners
        binding.colorPreview.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = resources.getDimension(R.dimen.card_corner_radius)
            setColor(color)
        }
    }

    private fun setupCategorySpinner() {
        val userId = auth.currentUser?.uid ?: return

        categoryRepository.getCategories(userId) { categories ->
            val categoryList = mutableListOf("No Category")
            categoryList.addAll(categories.map { it.name })
            categoryList.add("Create New")

            categoryAdapter = CustomCategorySpinnerAdapter(
                this,
                categoryList
            )

            binding.categorySpinner.apply {
                setAdapter(categoryAdapter)
                setOnItemClickListener { _, _, position, _ ->
                    val selectedItem = categoryList[position]
                    when (selectedItem) {
                        "Create New" -> showAddCategoryDialog()
                        "No Category" -> selectedCategory = "No Category"
                        else -> {
                            selectedCategory = selectedItem
                            Log.d("CreateReminder", "Category selected: $selectedCategory")
                        }
                    }
                }
            }
            // Set the category if it was provided from AI Chat
            if (selectedCategory.isNotEmpty() && selectedCategory != "No Category") {
                for (i in 0 until categoryList.size) {
                    if (categoryList[i].equals(selectedCategory, ignoreCase = true)) {
                        binding.categorySpinner.setText(categoryList[i], false)
                        break
                    }
                }
            }
        }
    }


    private fun showAddCategoryDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null)
        val editText = dialogView.findViewById<EditText>(R.id.editCategoryName)

        MaterialAlertDialogBuilder(this)
            .setTitle("Add New Category")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val categoryName = editText.text.toString().trim()

                if (categoryName.isNotEmpty()) {
                    val userId = auth.currentUser?.uid ?: return@setPositiveButton
                    val newCategory = Category(
                        name = categoryName.lowercase(),
                        userId = userId
                    )

                    categoryRepository.addCategory(newCategory) { success ->
                        if (success) {

                            setupCategorySpinner()

                            binding.categorySpinner.setText(categoryName, false)
                            selectedCategory = categoryName.lowercase()

                            Toast.makeText(this, "Category added", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to add category", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Category name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }



    private fun setupRepeatSwitch() {
        binding.repeatSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (repeatOption == "Never") {
                    showRepeatOptionsDialog()
                }
            } else {
                repeatOption = "Never"
                binding.repeatText.text = repeatOption
            }
        }
    }

    private fun showRepeatOptionsDialog() {
        val options = arrayOf(
            "Never",
            "Every Day",
            "Every Week",
            "Every Month"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Repeat Options")
            .setSingleChoiceItems(options, options.indexOf(repeatOption)) { dialog, which ->
                repeatOption = options[which]
                binding.repeatText.text = repeatOption
                if (repeatOption == "Never") {
                    binding.repeatSwitch.isChecked = false
                }
                dialog.dismiss()
            }
            .setOnCancelListener {
                // If user cancels without selecting, turn off the switch
                if (binding.repeatText.text == "Never") {
                    binding.repeatSwitch.isChecked = false
                }
            }
            .show()
    }


    private fun loadReminder(reminderId: String) {

        reminderRepository.getReminder(reminderId) { reminder ->
            reminder?.let {
                runOnUiThread {
                binding.apply {
                    etTitle.setText(it.title)
                    etDescription.setText(it.description)
                    selectedDate = it.date
                    selectedEmoji = it.emoji
                    selectedColor = it.color

                    if (it.category.isNotBlank()) {
                        val displayCategory = it.category.replaceFirstChar { char ->
                            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
                        }
                        categorySpinner.setText(displayCategory, false)
                        selectedCategory = it.category
                    } else {
                        categorySpinner.setText("No Category", false)
                        selectedCategory = "No Category"
                    }


                    val zoneId = ZoneId.of("Asia/Kuala_Lumpur")
                    val reminderDateTime = Instant.ofEpochMilli(it.date).atZone(zoneId)
                    dateText.text = reminderDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    timeText.text = reminderDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))

                    iconPreview.text = selectedEmoji

                    val drawable = ContextCompat.getDrawable(
                        this@CreateReminder,
                        R.drawable.circle_shape
                    )?.mutate() as GradientDrawable
                    drawable.setColor(selectedColor)
                    colorPreview.background = drawable


                    // Set repeat option
                    repeatOption = it.repeatOption
                    repeatText.text = repeatOption
                    repeatSwitch.isChecked = repeatOption != "Never"

                    // Set reminder offset
                    reminder.offsetMinutes = it.offsetMinutes
                    binding.reminderOffsetText.text = "${it.offsetMinutes} minutes"

                }
                }
            }
        }
    }



    private fun saveReminder() {
        val title = binding.etTitle.text.toString()
        val date = binding.dateText.text.toString()
        val time = binding.timeText.text.toString()



        if (title.isEmpty() || date.isEmpty() || time.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedDate == 0L) {
            Toast.makeText(this, "Please select both date and time", Toast.LENGTH_SHORT).show()
            return
        }


        // Apply the reminder offset
        val reminderTime = selectedDate - (reminder.offsetMinutes * 60 * 1000)

        val zoneId = ZoneId.of("Asia/Kuala_Lumpur")
        val currentDateTime = ZonedDateTime.now(zoneId)
        val selectedDateTime = Instant.ofEpochMilli(selectedDate).atZone(zoneId)

        Log.d("CreateReminder", """
            Saving reminder:
            Local Zone: $zoneId
            Current time (Malaysia): ${currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))}
            Selected time (Malaysia): ${selectedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))}
            Time difference: ${(selectedDate - currentDateTime.toInstant().toEpochMilli()) / 1000 / 60} minutes
            Raw current time: ${System.currentTimeMillis()}
            Raw selected time: $selectedDate
        """.trimIndent())

        if (selectedDateTime.isBefore(currentDateTime)) {
            Toast.makeText(this, "Please select a future date and time", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("CreateReminder", """
            About to save reminder:
            Date text: $date
            Time text: $time
            Selected timestamp: $selectedDate
            Selected date (Malaysia): ${selectedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))}
        """.trimIndent())

        val reminderCategory = when {
            selectedCategory.isNotBlank() && selectedCategory != "No Category" -> {
                selectedCategory.trim().lowercase()
            }
            else -> ""
        }

        val reminder = Reminder(
            id = reminderId ?: UUID.randomUUID().toString(),
            userId = auth.currentUser?.uid ?: return,
            title = binding.etTitle.text.toString(),
            description = binding.etDescription.text.toString(),
            category = reminderCategory,
            date = selectedDate,
            emoji = selectedEmoji,
            color = selectedColor,
            repeatOption = repeatOption,
            offsetMinutes = reminder.offsetMinutes
        )


        Log.d("CreateReminder", "Saving reminder with category: $reminderCategory")

        // Check for network connection
        val isOfflineMode = com.example.reminderapp.NetworkUtils.shouldUseOfflineMode(this)

        if (isOfflineMode) {
            // First, schedule local notifications immediately - this ensures they work in offline mode
            reminderRepository.scheduleNotifications(reminder, this)

            Log.d("CreateReminder", "Offline mode: Scheduled notifications first, then saving reminder")

            // Now try to save the reminder to Firestore (will be cached locally and synced later)
            reminderRepository.saveReminder(reminder) { success ->
                Log.d("CreateReminder", "Offline save callback received: success=$success")


                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Reminder saved in offline mode. Will sync when online.",
                        Toast.LENGTH_LONG
                    ).show()

                    setResult(RESULT_OK, Intent().apply {
                        putExtra("REMINDER_ID", reminder.id)
                    })
                    finish()
                }
            }

            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed) {
                    Log.d("CreateReminder", "Timeout triggered in offline mode - ensuring activity finishes")
                    setResult(RESULT_OK, Intent().apply {
                        putExtra("REMINDER_ID", reminder.id)
                    })
                    finish()
                }
            }, 1000)

            return
        }

        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage("Saving reminder...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (progressDialog.isShowing) {
                Log.d("CreateReminder", "Timeout triggered - dismissing dialog")
                progressDialog.dismiss()

                // Show appropriate message if we timed out
                Toast.makeText(
                    this,
                    "Operation timed out. Reminder may have been saved.",
                    Toast.LENGTH_LONG
                ).show()

                setResult(RESULT_OK, Intent().apply {
                    putExtra("REMINDER_ID", reminder.id)
                })
                finish()
            }
        }

        // Set timeout for 5 seconds
        timeoutHandler.postDelayed(timeoutRunnable, 5000)

        reminderRepository.saveReminder(reminder) { success ->
            // Cancel the timeout since we got a response
            timeoutHandler.removeCallbacks(timeoutRunnable)

            if (progressDialog.isShowing) {
                progressDialog.dismiss()
            }

            if (success) {

                reminderRepository.scheduleNotifications(reminder, this@CreateReminder)
                Log.d("CreateReminder", "Successfully saved reminder with ID: ${reminder.id}")

                runOnUiThread {
                    Toast.makeText(
                        this,
                        if (isEdit) "Reminder updated successfully" else "Reminder created successfully",
                        Toast.LENGTH_SHORT
                    ).show()

                    setResult(RESULT_OK, Intent().apply {
                        putExtra("REMINDER_ID", reminder.id)
                    })
                    finish()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Error saving reminder. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun updateDateTimeDisplay() {
        if (selectedDate > 0) {
            val instant = Instant.ofEpochMilli(selectedDate)
            val zonedDateTime = ZonedDateTime.ofInstant(instant, ZoneId.of("Asia/Kuala_Lumpur"))

            val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

            binding.dateText.text = zonedDateTime.format(dateFormatter)
            binding.timeText.text = zonedDateTime.format(timeFormatter)
        }
    }


    companion object {
        const val EXTRA_SELECTED_DATE = "selected_date"
        const val EXTRA_REMINDER_ID = "REMINDER_ID"
        const val EXTRA_TITLE = "TITLE"
        const val EXTRA_DESCRIPTION = "DESCRIPTION"
        const val EXTRA_TIMESTAMP = "TIMESTAMP"
        const val EXTRA_CATEGORY = "CATEGORY"
        const val EXTRA_FROM_AI = "FROM_AI"
        private const val REQUEST_CODE_SCHEDULE_EXACT_ALARM = 1001
    }
    private fun checkAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // Show explanation and request permission
                MaterialAlertDialogBuilder(this)
                    .setTitle("Permission Required")
                    .setMessage("This app needs permission to set exact alarms to work properly.")
                    .setPositiveButton("Grant Permission") { _, _ ->
                        startActivity(Intent(ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCHEDULE_EXACT_ALARM) {
            checkAlarmPermission()
        }
    }
}

