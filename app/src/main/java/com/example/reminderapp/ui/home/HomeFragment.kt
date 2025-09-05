package com.example.reminderapp.ui.home

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.reminderapp.CreateReminder
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.reminderapp.Category
import com.example.reminderapp.CategoryAdapter
import com.example.reminderapp.MainActivity
import com.example.reminderapp.R
import com.example.reminderapp.ReminderAdapter
import com.example.reminderapp.databinding.FragmentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.reminderapp.CategoryRepository
import com.example.reminderapp.Reminder
import com.example.reminderapp.ReminderRepository
import com.example.reminderapp.SearchableFragment
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import android.os.Handler
import android.os.Looper

class HomeFragment : Fragment(), SearchableFragment, ReminderAdapter.OnReminderActionListener {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private lateinit var viewModel: HomeViewModel
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var soonAdapter: ReminderAdapter
    private lateinit var futureAdapter: ReminderAdapter
    private lateinit var previousAdapter: ReminderAdapter
    private lateinit var searchAdapter: ReminderAdapter

    private val reminderRepository = ReminderRepository()
    private val categoryRepository = CategoryRepository()
    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> get() = _categories
    private var categoriesJob: Job? = null
    private var selectedCategory: String = "All"

    private val searchResults = MutableLiveData<List<Reminder>>()
    private var isSearchActive = false

    private var lastCategoriesLoadTime = 0L
    private val CATEGORIES_REFRESH_INTERVAL = 60000L // 1 minute

    override fun onReminderDeleted() {
        loadCategories()
    }

    private val createReminderLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Get the ID of the newly created or edited reminder
            val reminderId = result.data?.getStringExtra("REMINDER_ID")

            // Reload the reminders to ensure the list is up to date
            viewModel.loadReminders()

            // Highlight the new/edited reminder if we have its ID
            reminderId?.let { id ->
                // Add a slight delay to ensure the list is updated before highlighting
                Handler(Looper.getMainLooper()).postDelayed({
                    highlightReminder(id)
                }, 300)
            }
        }
    }

    private fun highlightReminder(reminderId: String) {
        Log.d("HomeFragment", "Attempting to highlight reminder: $reminderId")

        // Tell the adapters to highlight this reminder
        soonAdapter.highlightReminder(reminderId)
        futureAdapter.highlightReminder(reminderId)
        previousAdapter.highlightReminder(reminderId)

        // Find which adapter contains the reminder
        val adapters = listOf(
            Triple(soonAdapter, binding.soonRecyclerView, binding.soonExpandIcon),
            Triple(futureAdapter, binding.futureRecyclerView, binding.futureExpandIcon),
            Triple(previousAdapter, binding.previousRecyclerView, binding.previousExpandIcon)
        )

        // Try to find and scroll to the highlighted reminder
        for ((adapter, recyclerView, expandIcon) in adapters) {
            if (adapter.getHighlightedReminderId() == reminderId) {
                Log.d("HomeFragment", "Found reminder in adapter: ${adapter.javaClass.simpleName}")

                // Make sure this section is expanded
                if (recyclerView.visibility != View.VISIBLE) {
                    Log.d("HomeFragment", "Expanding section for highlighted reminder")
                    recyclerView.visibility = View.VISIBLE

                    // Hide empty state if it was visible
                    when (recyclerView) {
                        binding.soonRecyclerView -> binding.soonEmptyState.visibility = View.GONE
                        binding.futureRecyclerView -> binding.futureEmptyState.visibility = View.GONE
                        binding.previousRecyclerView -> binding.previousEmptyState.visibility = View.GONE
                    }

                    // Update expand icon
                    expandIcon.setImageResource(R.drawable.ic_arrow_drop_up)
                }

                // Use post to ensure RecyclerView is laid out
                recyclerView.post {

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager

                    // Find the position of the highlighted reminder in this adapter
                    for (i in 0 until adapter.itemCount) {
                        // For non-empty lists, scroll to the highlighted reminder
                        recyclerView.smoothScrollToPosition(i)
                        break
                    }

                    // Ensure this section is visible on screen (scroll the main ScrollView if needed)
                    binding.mainContent.post {
                        binding.mainContent.smoothScrollTo(0, recyclerView.top - binding.categoryRecyclerView.height)
                    }
                }

                break
            }
        }
    }


    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "REFRESH_REMINDERS") {
                loadReminders()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(HomeViewModel::class.java)


        setupAdapters()
        setupCategoryRecyclerView()
        setupReminderSections()
        setupObservers()
        initializeEmptyStates()

        // Load categories and reminders
        loadCategories()
        loadReminders()



        searchResults.observe(viewLifecycleOwner) { reminders ->
            if (isSearchActive) {
                searchAdapter.updateReminders(reminders)
                binding.searchLoading.visibility = View.GONE
            }
        }
    }

    private fun initializeEmptyStates() {
        // Initialize empty states with gone visibility
        binding.soonEmptyState.visibility = View.GONE
        binding.futureEmptyState.visibility = View.GONE
        binding.previousEmptyState.visibility = View.GONE
        binding.mainProgressBar.visibility = View.GONE
    }



    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null

        // Clean up listeners when view is destroyed
        reminderRepository.removeListener()
    }


    private fun setupAdapters() {
        soonAdapter = ReminderAdapter(this)
        futureAdapter = ReminderAdapter(this)
        previousAdapter = ReminderAdapter(this)
        searchAdapter = ReminderAdapter(this)

        // Configure RecyclerViews
        binding.soonRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = soonAdapter
            setHasFixedSize(true)
        }

        binding.futureRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = futureAdapter
            setHasFixedSize(true)
        }

        binding.previousRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = previousAdapter
            setHasFixedSize(true)
        }

        binding.searchRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
            setHasFixedSize(true)
        }

        categoryAdapter = CategoryAdapter(
            onCategorySelected = { category ->
                selectedCategory = category.name
                viewModel.setSelectedCategory(category)
                loadReminders()
            },
            onAddCategory = {
                // Show dialog to add new category
                showAddCategoryDialog()
            })

    }

    private fun setupCategoryRecyclerView() {
        // Initialize RecyclerView here
        binding.categoryRecyclerView.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }

        // Observe categories from ViewModel
        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            categoryAdapter.updateCategories(categories)

            // Scroll to the "All" category (position 0)
            binding.categoryRecyclerView.scrollToPosition(0)
        }
    }

    private fun setupReminderSections() {
        // Initialize sections in collapsed state with expanded icons
        binding.soonRecyclerView.visibility = View.GONE
        binding.futureRecyclerView.visibility = View.GONE
        binding.previousRecyclerView.visibility = View.GONE
        binding.soonEmptyState.visibility = View.GONE
        binding.futureEmptyState.visibility = View.GONE
        binding.previousEmptyState.visibility = View.GONE

        // Make sure expand icons show correct state (downward arrow when collapsed)
        binding.soonExpandIcon.setImageResource(R.drawable.ic_arrow_drop_down)
        binding.futureExpandIcon.setImageResource(R.drawable.ic_arrow_drop_down)
        binding.previousExpandIcon.setImageResource(R.drawable.ic_arrow_drop_down)

        // Set click listeners for section headers
        binding.soonSection.setOnClickListener {
            toggleSectionVisibility(binding.soonRecyclerView, binding.soonExpandIcon)
        }

        binding.futureSection.setOnClickListener {
            toggleSectionVisibility(binding.futureRecyclerView, binding.futureExpandIcon)
        }

        binding.previousSection.setOnClickListener {
            toggleSectionVisibility(binding.previousRecyclerView, binding.previousExpandIcon)
        }
    }

    private fun toggleSectionVisibility(recyclerView: RecyclerView, expandIcon: ImageView) {

        val parentLayout = recyclerView.parent as? ViewGroup
        val emptyStateView = when (recyclerView.id) {
            R.id.soonRecyclerView -> binding.soonEmptyState
            R.id.futureRecyclerView -> binding.futureEmptyState
            R.id.previousRecyclerView -> binding.previousEmptyState
            else -> null
        }

        val isExpanded = recyclerView.isVisible || (emptyStateView?.isVisible == true)

        if (isExpanded) {
            // Collapse
            recyclerView.visibility = View.GONE
            emptyStateView?.visibility = View.GONE
            expandIcon.setImageResource(R.drawable.ic_arrow_drop_down)
        } else {
            // Expand
            if (recyclerView.adapter?.itemCount == 0) {
                // Show empty state if no items
                emptyStateView?.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                // Show recycler view if has items
                emptyStateView?.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
            expandIcon.setImageResource(R.drawable.ic_arrow_drop_up)
        }
    }


    private fun setupObservers() {
        // Observe LiveData from ViewModel
        viewModel.soonReminders.observe(viewLifecycleOwner) { reminders ->
            soonAdapter.updateReminders(reminders)
            // Only auto-expand if have reminders and the loading is complete
            val shouldAutoExpand = reminders.isNotEmpty() && binding.mainProgressBar.visibility != View.VISIBLE

            if (shouldAutoExpand && !binding.soonRecyclerView.isVisible && !binding.soonEmptyState.isVisible) {
                // Auto-expand sections with reminders
                binding.soonRecyclerView.visibility = View.VISIBLE
                binding.soonEmptyState.visibility = View.GONE
                binding.soonExpandIcon.setImageResource(R.drawable.ic_arrow_drop_up)
            } else {
                // Keep the section closed but update the empty state visibility
                updateEmptyState(binding.soonEmptyState, binding.soonRecyclerView, reminders.isEmpty(), false)
            }
        }

        viewModel.futureReminders.observe(viewLifecycleOwner) { reminders ->
            futureAdapter.updateReminders(reminders)
            // Only auto-expand if have reminders and the loading is complete
            val shouldAutoExpand = reminders.isNotEmpty() && binding.mainProgressBar.visibility != View.VISIBLE

            if (shouldAutoExpand && !binding.futureRecyclerView.isVisible && !binding.futureEmptyState.isVisible) {
                // Auto-expand sections with reminders
                binding.futureRecyclerView.visibility = View.VISIBLE
                binding.futureEmptyState.visibility = View.GONE
                binding.futureExpandIcon.setImageResource(R.drawable.ic_arrow_drop_up)
            } else {
                // Keep the section closed but update the empty state visibility
                updateEmptyState(binding.futureEmptyState, binding.futureRecyclerView, reminders.isEmpty(), false)
            }
        }

        viewModel.previousReminders.observe(viewLifecycleOwner) { reminders ->
            previousAdapter.updateReminders(reminders)
            // Only auto-expand if we have reminders and the loading is complete
            val shouldAutoExpand = reminders.isNotEmpty() && binding.mainProgressBar.visibility != View.VISIBLE

            if (shouldAutoExpand && !binding.previousRecyclerView.isVisible && !binding.previousEmptyState.isVisible) {
                // Auto-expand sections with reminders
                binding.previousRecyclerView.visibility = View.VISIBLE
                binding.previousEmptyState.visibility = View.GONE
                binding.previousExpandIcon.setImageResource(R.drawable.ic_arrow_drop_up)
            } else {
                // Keep the section closed but update the empty state visibility
                updateEmptyState(binding.previousEmptyState, binding.previousRecyclerView, reminders.isEmpty(), false)
            }
        }
    }

    private fun updateEmptyState(emptyStateView: View, recyclerView: RecyclerView, isEmpty: Boolean, shouldToggleVisibility: Boolean = true) {

        if (!shouldToggleVisibility) {
            return
        }
        if (isEmpty) {
            emptyStateView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyStateView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showAddCategoryDialog() {
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_category, null)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.editCategoryName)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Category")
            .setView(dialogView)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val categoryName = editText.text.toString().trim()

            if (categoryName.isEmpty()) {
                editText.error = "Category name cannot be empty"
                return@setOnClickListener
            }

            val userId = auth.currentUser?.uid ?: return@setOnClickListener
            val newCategory = Category(
                id = UUID.randomUUID().toString(),
                name = categoryName,
                userId = userId
            )

            categoryRepository.addCategory(newCategory) { success ->
                if (success) {
                    loadCategories()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Failed to add category", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        editText.requestFocus()
        editText.postDelayed({
            val imm =
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }


    private fun loadCategories() {
        val userId = auth.currentUser?.uid ?: return


        reminderRepository.getAllUserReminders(userId) { allReminders ->
            val totalCount = allReminders.size  // This is for ALL category

            Log.d("HomeFragment", "All reminders count: $totalCount")

            categoryRepository.getCategoriesWithCount(userId) { categories ->
                val allCategory = Category("all", "ALL", userId)

                // Create default categories list with correct count for ALL
                val defaultCategories = listOf(
                    allCategory.copy(reminderCount = totalCount)  // ALL category gets total count
                )

                // Combine with other categories (which have their own individual counts)
                val finalCategories = defaultCategories + categories

                // Log counts for debugging
                Log.d(
                    "HomeFragment",
                    "All category count: $totalCount, Total categories: ${categories.size}"
                )

                // Update the ViewModel's categories
                viewModel.addCategories(finalCategories)
                // Update the adapter directly as well
                categoryAdapter.updateCategories(finalCategories)
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()

        if (System.currentTimeMillis() - lastCategoriesLoadTime > CATEGORIES_REFRESH_INTERVAL) {
            loadCategories()
            lastCategoriesLoadTime = System.currentTimeMillis()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(
                refreshReceiver,
                IntentFilter("REFRESH_REMINDERS"),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            requireContext().registerReceiver(
                refreshReceiver,
                IntentFilter("REFRESH_REMINDERS")
            )
        }
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(refreshReceiver)
    }

    override fun showSearchUI() {
        isSearchActive = true
        binding.categoryRecyclerView.visibility = View.GONE
        binding.mainContent.visibility = View.GONE
        binding.mainProgressBar.visibility = View.GONE
        binding.searchContent.visibility = View.VISIBLE
    }

    override fun hideSearchUI() {
        isSearchActive = false
        binding.mainContent.visibility = View.VISIBLE
        binding.searchContent.visibility = View.GONE

        binding.categoryRecyclerView.visibility = View.VISIBLE

        if (binding.mainProgressBar.visibility == View.VISIBLE) {
            binding.soonSection.visibility = View.GONE
            binding.futureSection.visibility = View.GONE
            binding.previousSection.visibility = View.GONE
        } else {
            binding.soonSection.visibility = View.VISIBLE
            binding.futureSection.visibility = View.VISIBLE
            binding.previousSection.visibility = View.VISIBLE
        }

        loadReminders()
    }

    override fun searchReminders(query: String) {
        binding.searchLoading.visibility = View.VISIBLE
        val userId = auth.currentUser?.uid ?: return
        reminderRepository.getAllUserReminders(userId) { reminders ->
            val filteredReminders = reminders.filter { reminder ->
                (reminder.title.contains(query, ignoreCase = true) ||
                        reminder.description.contains(query, ignoreCase = true)) &&
                        (selectedCategory == "All" || reminder.category == selectedCategory)
            }

            searchResults.postValue(filteredReminders)
        }
    }



    private fun loadReminders() {
        // Only show loading if we have no data
        val itemCounts = listOf(
            binding.soonRecyclerView.adapter?.itemCount,
            binding.futureRecyclerView.adapter?.itemCount,
            binding.previousRecyclerView.adapter?.itemCount
        )
        val hasExistingData = !itemCounts.all { count -> count == 0 || count == null }

        if (!hasExistingData) {
            showLoading(true)
        }

        val userId = auth.currentUser?.uid ?: return

        // Use optimized one-time fetch if just initializing
        if (!hasExistingData) {
            reminderRepository.getRemindersOnce(userId) { reminders ->
                viewModel.processReminders(reminders)
                showLoading(false)
            }
        } else {
            // Otherwise use the normal listener method for real-time updates
            viewModel.loadReminders()
            showLoading(false)
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.mainProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

        // Hide sections while loading
        val sectionsVisibility = if (isLoading) View.GONE else View.VISIBLE
        binding.soonSection.visibility = sectionsVisibility
        binding.futureSection.visibility = sectionsVisibility
        binding.previousSection.visibility = sectionsVisibility
    }

    companion object {
        private const val CATEGORIES_REFRESH_INTERVAL = 60000L // 1 minute
    }
}




