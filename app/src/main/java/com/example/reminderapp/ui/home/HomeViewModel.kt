package com.example.reminderapp.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.reminderapp.Category
import com.example.reminderapp.CategoryRepository
import com.example.reminderapp.Reminder
import com.example.reminderapp.ReminderRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.withContext
class HomeViewModel : ViewModel() {
    private val reminderRepository = ReminderRepository()
    private val auth = FirebaseAuth.getInstance()

    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> = _categories

    private val _soonReminders = MutableLiveData<List<Reminder>>()
    val soonReminders: LiveData<List<Reminder>> = _soonReminders

    private val _futureReminders = MutableLiveData<List<Reminder>>()
    val futureReminders: LiveData<List<Reminder>> = _futureReminders

    private val _previousReminders = MutableLiveData<List<Reminder>>()
    val previousReminders: LiveData<List<Reminder>> = _previousReminders

    private var selectedCategory: Category? = null

    private val categoryRepository = CategoryRepository()
    // Add last processed data cache
    private var lastCategoryName: String? = null
    private var lastProcessedReminders: List<Reminder>? = null

    init {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            categoryRepository.observeCategories(userId)
                .collect { categories ->
                    val defaultCategories = listOf(
                        Category(id = "all", name = "ALL", userId = userId, isSelected = true, order = -1)
                    )

                    val combinedCategories = (defaultCategories + categories)
                        .distinctBy { it.name.lowercase() }

                    _categories.postValue(combinedCategories)

                    selectedCategory?.let { current ->
                        selectedCategory = combinedCategories.find { it.id == current.id }
                    } ?: run {
                        selectedCategory = combinedCategories.first { it.id == "all" }
                    }

                    loadReminders()
                }
        }
        loadCategories()
    }



    private fun loadCategories() {
        val userId = auth.currentUser?.uid ?: return
        val defaultCategories = listOf(
            Category(id = "all", name = "ALL", userId = userId, isSelected = true), // Default "All" category
        )
        _categories.value = defaultCategories

        // Fetch user's custom categories
        categoryRepository.getCategories(userId) { userCategories ->
            val combinedCategories = (defaultCategories + userCategories)
                .distinctBy { it.name.lowercase() }


            _categories.postValue(combinedCategories)

            // Default to "ALL" category
            selectedCategory = combinedCategories.first { it.id == "all" }
            loadReminders()
        }
    }


    fun loadReminders() {
        val userId = auth.currentUser?.uid ?: return

        // Ensure selectedCategory is not null
        if (selectedCategory == null) {
            selectedCategory = _categories.value?.first { it.id == "all" }
        }

        reminderRepository.getAllUserReminders(userId) { reminders ->
            processReminders(reminders)
        }
    }

    fun processReminders(reminders: List<Reminder>) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                val cleanSelectedCategory = selectedCategory?.name?.trim()?.lowercase() ?: "all"

                // Skip processing if category and data are the same
                if (cleanSelectedCategory == lastCategoryName && reminders == lastProcessedReminders) {
                    Log.d("HomeViewModel", "Skipping processing - no changes")
                    return@withContext
                }

                Log.d("HomeViewModel", "Selected category: ${selectedCategory?.name}}")
                Log.d("HomeViewModel", "Total reminders: ${reminders.size}")

                val filteredReminders = if (cleanSelectedCategory == "all") {
                    reminders
                } else {
                    reminders.filter { it.category?.trim()?.lowercase() == cleanSelectedCategory }
                }

                Log.d("HomeViewModel", "Filtered reminders: ${filteredReminders.size}")

                val now = Instant.now()
                val soon = filteredReminders.filter { reminder ->
                    val reminderInstant = Instant.ofEpochMilli(reminder.date)
                    ChronoUnit.HOURS.between(now, reminderInstant) <= 24 && reminderInstant.isAfter(now)
                }

                val future = filteredReminders.filter { reminder ->
                    val reminderInstant = Instant.ofEpochMilli(reminder.date)
                    ChronoUnit.HOURS.between(now, reminderInstant) > 24
                }

                val previous = filteredReminders.filter { reminder ->
                    Instant.ofEpochMilli(reminder.date).isBefore(now)
                }

                lastCategoryName = cleanSelectedCategory
                lastProcessedReminders = reminders

                withContext(Dispatchers.Main) {
                    _soonReminders.value = soon.sortedBy { it.date }
                    _futureReminders.value = future.sortedBy { it.date }
                    _previousReminders.value = previous.sortedByDescending { it.date }
                }
            }
        }
    }

    fun setSelectedCategory(category: Category) {
        selectedCategory = category
        loadReminders()
    }

    fun addCategories(newCategories: List<Category>) {
        _categories.value = newCategories
    }
}