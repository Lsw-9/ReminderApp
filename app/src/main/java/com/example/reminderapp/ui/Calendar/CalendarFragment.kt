package com.example.reminderapp.ui.Calendar

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.reminderapp.databinding.FragmentCalendarBinding
import com.kizitonwose.calendar.view.ViewContainer
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.MonthDayBinder
import java.time.YearMonth
import java.time.LocalDate
import android.graphics.Color
import android.util.Log
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.reminderapp.CreateReminder
import com.example.reminderapp.R
import com.example.reminderapp.Reminder
import com.example.reminderapp.ReminderAdapter
import com.example.reminderapp.ReminderRepository
import com.example.reminderapp.SharedViewModel
import com.google.firebase.auth.FirebaseAuth
import java.time.DayOfWeek
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.example.reminderapp.MainActivity
import com.example.reminderapp.SearchableFragment

class CalendarFragment : Fragment(), SearchableFragment, ReminderAdapter.OnReminderActionListener {

    private lateinit var binding: FragmentCalendarBinding
    private lateinit var calendarView: CalendarView
    private lateinit var reminderAdapter: ReminderAdapter
    private lateinit var viewModel: SharedViewModel

    private val reminderRepository = ReminderRepository()
    private var remindersMap: Map<LocalDate, List<Reminder>> = emptyMap()
    private lateinit var auth: FirebaseAuth

    private lateinit var searchAdapter: ReminderAdapter
    private var normalView: View? = null
    private var searchView: View? = null
    private val searchResults = MutableLiveData<List<Reminder>>()
    private var isSearchActive = false

    private val createReminderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val reminderId = result.data?.getStringExtra("REMINDER_ID")
            reminderId?.let { id ->
                // Reload reminders
                loadReminders { reminders ->
                    // Find the position of the reminder
                    val position = reminders.indexOfFirst { it.id == id }
                    if (position != -1) {
                        // Scroll to the reminder
                        binding.reminderRecyclerView.smoothScrollToPosition(position)
                        // Highlight the reminder
                        reminderAdapter.highlightReminder(id)
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(SharedViewModel::class.java)

        setupRecyclerView()
        setupAuth()
        setupCalendar()
        loadReminders()
        observeSelectedDate()


        searchResults.observe(viewLifecycleOwner) { reminders ->
            if (isSearchActive) {
                reminderAdapter.updateReminders(reminders)
                updateEmptyState(reminders.isEmpty())
            }
        }
    }


    override fun onResume() {
        super.onResume()
        loadReminders()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        reminderRepository.cleanup()
    }


    private fun setupAuth() {
        auth = FirebaseAuth.getInstance()
    }

    private fun loadReminders(callback: ((List<Reminder>) -> Unit)? = null) {
        val userId = auth.currentUser?.uid ?: return

        reminderRepository.getAllUserReminders(userId) { reminders ->
            // Process reminders including recurring ones
            val processedReminders = processReminders(reminders)

            // Convert reminders to map grouped by date
            remindersMap = processedReminders.groupBy { reminder ->
                Instant.ofEpochMilli(reminder.date)
                    .atZone(ZoneId.of("Asia/Kuala_Lumpur"))
                    .toLocalDate()
            }

            // Update UI
            updateCalendarWithReminders()
            updateRemindersForSelectedDate()

            // Call the callback with reminders if provided
            callback?.invoke(processedReminders)

            Log.d("CalendarFragment", "Reminders reloaded, count: ${processedReminders.size}")
        }
    }

    private fun processReminders(reminders: List<Reminder>): List<Reminder> {
        val processedReminders = mutableListOf<Reminder>()
        val currentTime = System.currentTimeMillis()

        reminders.forEach { reminder ->
            // For non-recurring reminders, add them directly
            if (reminder.repeatOption == Reminder.REPEAT_NEVER) {
                processedReminders.add(reminder)
            } else {
                // For recurring reminders, first add the original reminder if it's in the future
                // This ensures the original reminder can be properly edited/deleted
                if (reminder.date >= currentTime) {
                    processedReminders.add(reminder)
                }

                // Then generate and add occurrences
                val occurrences = reminder.generateNextOccurrences()
                if (occurrences.isNotEmpty()) {
                    Log.d(
                        "CalendarFragment",
                        "Generated ${occurrences.size} occurrences for reminder ${reminder.id}"
                    )
                    processedReminders.addAll(occurrences)
                }
            }
        }

        // Sort reminders by date
        val sortedReminders = processedReminders.sortedBy { it.date }

        Log.d("CalendarFragment", "Final processed reminders count: ${sortedReminders.size}")
        return sortedReminders
    }


    private fun setupRecyclerView() {
        reminderAdapter = ReminderAdapter(this)
        binding.reminderRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = reminderAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupCalendar() {
        calendarView = binding.calendarView
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(6)
        val endMonth = currentMonth.plusMonths(6)
        val firstDayOfWeek = DayOfWeek.MONDAY
        val currentDate = LocalDate.now()

        setupMonthHeader()
        setupDayBinder(currentDate)

        calendarView.setup(startMonth, endMonth, firstDayOfWeek)
        calendarView.scrollToMonth(currentMonth)
    }

    private fun setupMonthHeader() {
        calendarView.monthHeaderBinder = object : MonthHeaderFooterBinder<MonthViewContainer> {
            override fun create(view: View) = MonthViewContainer(view)
            override fun bind(container: MonthViewContainer, data: CalendarMonth) {
                container.textView.text = data.yearMonth.format(
                    DateTimeFormatter.ofPattern("MMMM yyyy")
                )
            }
        }
    }

    private fun setupDayBinder(currentDate: LocalDate) {
        calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, data: CalendarDay) {
                bindDayView(container, data, currentDate)
            }
        }
    }

    private fun bindDayView(container: DayViewContainer, day: CalendarDay, currentDate: LocalDate) {
        container.textView.apply {
            text = day.date.dayOfMonth.toString()

            if (day.position == DayPosition.MonthDate) {

                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(
                    com.google.android.material.R.attr.colorOnSurface,
                    typedValue,
                    true
                )
                setTextColor(typedValue.data)

                val isToday = day.date == currentDate
                val isSelected = viewModel.selectedDate.value == day.date
                val hasReminders = remindersMap[day.date]?.isNotEmpty() == true

                setupDayViewStyle(container, isToday, isSelected, hasReminders)
                setOnClickListener {
                    viewModel.setSelectedDate(day.date)
                    updateRemindersForSelectedDate()
                    calendarView.notifyCalendarChanged()
                }
            } else {

                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(
                    com.google.android.material.R.attr.colorOnSurface,
                    typedValue,
                    true
                )
                val color = typedValue.data
                val alpha = (0.6 * 255).toInt()
                setTextColor(
                    Color.argb(
                        alpha,
                        android.graphics.Color.red(color),
                        android.graphics.Color.green(color),
                        android.graphics.Color.blue(color)
                    )
                )

                background = null
                container.eventIndicator.visibility = View.GONE
                setOnClickListener(null)
            }
        }
    }

    private fun setupDayViewStyle(
        container: DayViewContainer,
        isToday: Boolean,
        isSelected: Boolean,
        hasReminders: Boolean
    ) {
        container.textView.apply {
            when {
                isSelected -> setBackgroundResource(R.drawable.selected_bg)
                isToday -> setBackgroundResource(R.drawable.today_bg)
                else -> background = null
            }
        }
        // Show event indicator
        container.eventIndicator.apply {
            visibility = if (hasReminders) View.VISIBLE else View.GONE
            setImageResource(R.drawable.event_bar_indicator)
        }
    }


    private fun updateCalendarWithReminders() {
        calendarView.notifyCalendarChanged()
    }

    private fun updateRemindersForSelectedDate() {
        viewModel.selectedDate.value?.let { date ->
            val reminders = remindersMap[date] ?: emptyList()
            val currentTime = System.currentTimeMillis()

            // Sort reminders by time remaining/expired status
            val sortedReminders = reminders.sortedWith { a, b ->
                val timeRemainingA = a.date - currentTime
                val timeRemainingB = b.date - currentTime

                when {
                    // If both are upcoming or both are expired, sort by time
                    (timeRemainingA > 0 && timeRemainingB > 0) ||
                            (timeRemainingA <= 0 && timeRemainingB <= 0) ->
                        timeRemainingA.compareTo(timeRemainingB)

                    // If one is upcoming and one is expired, upcoming comes first
                    timeRemainingA > 0 -> -1
                    else -> 1
                }
            }

            updatePendingReminderCard(sortedReminders, currentTime)

            Log.d("CalendarFragment", "Updating reminders for date: $date")
            Log.d("CalendarFragment", "Found ${sortedReminders.size} reminders")

            reminderAdapter.updateReminders(sortedReminders)
            updateEmptyState(sortedReminders.isEmpty())

        }
    }

    private fun updatePendingReminderCard(reminders: List<Reminder>, currentTime: Long) {
        val pendingReminders = reminders.count { reminder ->
            val timeRemaining = reminder.date - currentTime
            timeRemaining > 0 && !reminder.isCompleted
        }

        binding.pendingReminderText.apply {
            val displayText = when {
                pendingReminders == 0 -> {
                    setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    "NO PENDING REMINDER"
                }

                pendingReminders == 1 -> {
                    setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                    "1 PENDING REMINDER"
                }

                pendingReminders >= 10 -> {
                    setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                    "10+ PENDING REMINDERS"
                }

                else -> {
                    setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                    "$pendingReminders PENDING REMINDERS"
                }
            }
            text = displayText
        }
    }


    override fun showSearchUI() {
        isSearchActive = true
        binding.calendarView.visibility = View.GONE
        binding.pendingReminderCard.visibility = View.GONE
        binding.searchContent.visibility = View.VISIBLE
    }

    override fun hideSearchUI() {
        isSearchActive = false
        binding.calendarView.visibility = View.VISIBLE
        binding.pendingReminderCard.visibility =
            View.VISIBLE
        binding.searchContent.visibility = View.GONE
        loadReminders()
    }

    override fun searchReminders(query: String) {
        binding.searchLoading.visibility = View.VISIBLE
        val userId = auth.currentUser?.uid ?: return
        reminderRepository.getAllUserReminders(userId) { reminders ->
            val filteredReminders = reminders.filter { reminder ->
                reminder.title.contains(query, ignoreCase = true) ||
                        reminder.description.contains(query, ignoreCase = true)
            }
            binding.searchLoading.visibility = View.GONE
            searchResults.postValue(filteredReminders)
        }
    }

    override fun onReminderDeleted() {
        loadReminders {
            updateCalendarWithReminders()

            updateRemindersForSelectedDate()
        }
    }

    private fun observeSelectedDate() {
        viewModel.selectedDate.observe(viewLifecycleOwner) { date ->
            updateRemindersForSelectedDate()
            calendarView.notifyCalendarChanged()

        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.apply {
            emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
            emptyStateImage.visibility = if (isEmpty) View.VISIBLE else View.GONE
            emptyStateContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE
            reminderRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }


    fun highlightReminder(reminderId: String) {
        Log.d("CalendarFragment", "Attempting to highlight reminder: $reminderId")

        view?.post {
            loadReminders { allReminders ->

                allReminders.find { it.id == reminderId }?.let { reminder ->
                    Log.d("CalendarFragment", "Found reminder to highlight: ${reminder.id}")


                    val reminderDate = Instant.ofEpochMilli(reminder.date)
                        .atZone(ZoneId.of("Asia/Kuala_Lumpur"))
                        .toLocalDate()


                    viewModel.setSelectedDate(reminderDate)

                    view?.postDelayed({

                        val currentTime = System.currentTimeMillis()
                        val dateReminders = remindersMap[reminderDate]?.let { reminders ->
                            reminders.sortedWith { a, b ->
                                val timeRemainingA = a.date - currentTime
                                val timeRemainingB = b.date - currentTime

                                when {
                                    // If both are upcoming or both are expired, sort by time
                                    (timeRemainingA > 0 && timeRemainingB > 0) ||
                                            (timeRemainingA <= 0 && timeRemainingB <= 0) ->
                                        timeRemainingA.compareTo(timeRemainingB)

                                    // If one is upcoming and one is expired, upcoming comes first
                                    timeRemainingA > 0 -> -1
                                    else -> 1
                                }
                            }
                        } ?: emptyList()
                        Log.d("CalendarFragment", "Current reminders for date: ${dateReminders.map { it.id }}")

                        // Update adapter with the current list
                        reminderAdapter.updateReminders(dateReminders)

                        val position = dateReminders.indexOfFirst { it.id == reminderId }
                        Log.d("CalendarFragment", "Reminder position in list: $position")

                        if (position != -1) {
                            // Ensure RecyclerView is laid out
                            binding.reminderRecyclerView.post {
                                // First scroll
                                binding.reminderRecyclerView.scrollToPosition(position)

                                // Then highlight
                                binding.reminderRecyclerView.postDelayed({
                                    reminderAdapter.highlightReminder(reminderId)

                                    // Final scroll to ensure visibility
                                    binding.reminderRecyclerView.smoothScrollToPosition(position)
                                }, 100)
                            }
                        }
                    }, 300)
                }
            }
        }
    }

private fun showPopupMenu(view: View, reminder: Reminder) {
    val popup = PopupMenu(view.context, view)
    popup.menuInflater.inflate(R.menu.reminder_item_menu, popup.menu)

    popup.setOnMenuItemClickListener { menuItem ->
        when (menuItem.itemId) {
            R.id.action_edit -> {
                val intent = Intent(view.context, CreateReminder::class.java).apply {
                    putExtra("REMINDER_ID", reminder.id)
                    putExtra("IS_EDIT", true)
                }
                (activity as? MainActivity)?.let { mainActivity ->
                    mainActivity.launchCreateReminder(intent)
                }
                true
            }
            R.id.action_delete -> {
                // Add delete handling
                true
            }
            else -> false
        }
    }
    popup.show()
}

    private fun LinearLayoutManager.isViewPartiallyVisible(
        position: Int,
        completelyVisible: Boolean,
        acceptEndPointInclusion: Boolean
    ): Boolean {
        val firstPosition = findFirstVisibleItemPosition()
        val lastPosition = findLastVisibleItemPosition()

        return when {
            completelyVisible -> position in firstPosition..lastPosition
            acceptEndPointInclusion -> position >= firstPosition && position <= lastPosition
            else -> position > firstPosition && position < lastPosition
        }
    }
}


class MonthViewContainer(view: View) : ViewContainer(view) {
    val textView: TextView = view.findViewById(R.id.headerTextView)
}

class DayViewContainer(view: View) : ViewContainer(view) {
    val textView: TextView = view.findViewById(R.id.calendarDayText)
    val eventIndicator: ImageView = view.findViewById(R.id.eventIndicator)
}

class SharedViewModel : ViewModel() {
    private val _selectedDate = MutableLiveData<LocalDate>()
    val selectedDate: LiveData<LocalDate> = _selectedDate

    fun setSelectedDate(date: LocalDate) {
        Log.d("SharedViewModel", "Setting selected date: $date")
        _selectedDate.value = date
    }
}