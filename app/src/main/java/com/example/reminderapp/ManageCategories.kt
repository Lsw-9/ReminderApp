package com.example.reminderapp

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.PopupMenu
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.reminderapp.databinding.ActivityManageCategoriesBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

class ManageCategories : AppCompatActivity() {
    private lateinit var binding: ActivityManageCategoriesBinding
    private lateinit var categoriesAdapter: ManageCategoriesAdapter
    private val categoryRepository = CategoryRepository()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Categories"

        setupRecyclerView()
        setupCreateNewButton()
        loadCategories()
    }



    private fun setupRecyclerView() {
        categoriesAdapter = ManageCategoriesAdapter(
            onMenuClick = { category, view ->
                showPopupMenu(category, view)
            },
            onOrderChanged = { updatedCategories ->
                // Save the new order to Firebase
                categoryRepository.updateCategoryOrder(updatedCategories) { success ->
                    if (success) {
                        Log.d("ManageCategories", "Order updated successfully, reloading categories")
                        loadCategories()
                    } else {
                        Toast.makeText(
                            this,
                            "Failed to save category order",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )

        binding.categoriesRecyclerView.apply {
            adapter = categoriesAdapter
            layoutManager = LinearLayoutManager(this@ManageCategories)

            // Add ItemTouchHelper for drag and drop
            itemTouchHelper = ItemTouchHelper(CategoryItemTouchHelper(categoriesAdapter))
            itemTouchHelper.attachToRecyclerView(this)
        }

        // Add long press gesture detection
        binding.categoriesRecyclerView.addOnItemTouchListener(
            RecyclerViewTouchListener(this, binding.categoriesRecyclerView, object : ClickListener {
                override fun onLongClick(view: View?, position: Int) {
                    // Start drag on long press
                    itemTouchHelper.startDrag(
                        binding.categoriesRecyclerView.findViewHolderForAdapterPosition(position)!!
                    )
                }

                override fun onClick(view: View?, position: Int) {
                }
            })
        )
    }


    private fun setupCreateNewButton() {
        binding.createNewButton.setOnClickListener {
            showAddCategoryDialog()
        }
    }

    private fun loadCategories() {
        val userId = auth.currentUser?.uid ?: return
        val reminderRepository = ReminderRepository()

        reminderRepository.getAllUserReminders(userId) { allReminders ->
            categoryRepository.getCategoriesWithCount(userId) { categories ->
                // Filter out the "All" category if present
                val filteredCategories = categories.filter { it.id != "all" }
                categoriesAdapter.updateCategories(filteredCategories)
                binding.categoriesRecyclerView.adapter?.notifyDataSetChanged()
            }
        }
    }

    private fun showPopupMenu(category: Category, anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.category_menu, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_rename -> {
                        showRenameCategoryDialog(category)
                        true
                    }
                    R.id.action_delete -> {
                        showDeleteConfirmationDialog(category)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showAddCategoryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.editCategoryName)

        MaterialAlertDialogBuilder(this)
            .setTitle("Create New Category")
            .setView(dialogView)
            .setPositiveButton("Create") { dialog, _ ->
                val categoryName = editText.text.toString().trim()
                if (categoryName.isNotEmpty()) {
                    createCategory(categoryName)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameCategoryDialog(category: Category) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_category, null)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.editCategoryName)
        editText.setText(category.name)

        MaterialAlertDialogBuilder(this)
            .setTitle("Rename Category")
            .setView(dialogView)
            .setPositiveButton("Rename") { dialog, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    renameCategory(category, newName)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(category: Category) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Category")
            .setMessage("Are you sure you want to delete this category?")
            .setPositiveButton("Delete") { dialog, _ ->
                deleteCategory(category)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createCategory(name: String) {
        val userId = auth.currentUser?.uid ?: return
        val newCategory = Category(
            id = UUID.randomUUID().toString(),
            name = name,
            userId = userId
        )

        categoryRepository.addCategory(newCategory) { success ->
            if (success) {
                loadCategories()
            } else {
                Toast.makeText(this, "Failed to create category", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renameCategory(category: Category, newName: String) {
        val updatedCategory = category.copy(name = newName)
        categoryRepository.updateCategory(updatedCategory) { success ->
            if (success) {
                loadCategories()
            } else {
                Toast.makeText(this, "Failed to rename category", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteCategory(category: Category) {
        categoryRepository.deleteCategory(category.id) { success ->
            if (success) {
                loadCategories()
            } else {
                Toast.makeText(this, "Failed to delete category", Toast.LENGTH_SHORT).show()
            }
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
}
