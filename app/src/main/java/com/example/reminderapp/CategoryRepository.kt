package com.example.reminderapp

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CategoryRepository {
    private val db = FirebaseFirestore.getInstance()
    private val categoriesCollection = db.collection("categories")
    private val remindersCollection = db.collection("reminders")
    private val _categories = MutableLiveData<List<Category>>()
    private val _categoriesStateFlow = MutableStateFlow<List<Category>>(emptyList())

    val categories: LiveData<List<Category>> get() = _categories

    init {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            categoriesCollection
                .whereEqualTo("userId", userId)
                .orderBy("order", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e("CategoryRepository", "Listen failed.", e)
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val categories = snapshot.documents.mapNotNull {
                            it.toObject(Category::class.java)
                        }
                        // Remove the sortedBy
                        _categoriesStateFlow.value = categories
                        _categories.postValue(categories)
                    }
                }
        }
    }



    fun addCategory(category: Category, onComplete: (Boolean) -> Unit) {
        // Ensure unique category names for a user
        categoriesCollection
            .whereEqualTo("userId", category.userId)
            .whereEqualTo("name", category.name.lowercase())
            .get()
            .addOnSuccessListener { existingCategories ->
                if (existingCategories.isEmpty) {
                    // Category doesn't exist, proceed with adding
                    categoriesCollection.document(category.id)
                        .set(category.copy(name = category.name.lowercase()))
                        .addOnSuccessListener { onComplete(true) }
                        .addOnFailureListener {
                            Log.e("CategoryRepository", "Failed to add category", it)
                            onComplete(false)
                        }
                } else {
                    // Category already exists
                    onComplete(false)
                }
            }
            .addOnFailureListener {
                Log.e("CategoryRepository", "Error checking existing categories", it)
                onComplete(false)
            }
    }

    fun deleteCategory(categoryId: String, onComplete: (Boolean) -> Unit) {
        categoriesCollection.document(categoryId)
            .delete()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun updateCategory(category: Category, onComplete: (Boolean) -> Unit) {
        categoriesCollection.document(category.id)
            .set(category)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun getCategoriesWithCount(userId: String, onComplete: (List<Category>) -> Unit) {
        remindersCollection
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { reminderSnapshot ->
                // Create a map of category names to their counts
                val categoryCountMap = reminderSnapshot.documents.groupBy { doc ->
                    doc.getString("category")?.trim()?.lowercase() ?: ""
                }.mapValues { it.value.size }

                // Get all categories
                categoriesCollection
                    .whereEqualTo("userId", userId)
                    .orderBy("order", Query.Direction.ASCENDING)
                    .get()
                    .addOnSuccessListener { categorySnapshot ->
                        // Map categories with their individual counts
                        val categoriesWithCount = categorySnapshot.documents.mapNotNull { doc ->
                            doc.toObject(Category::class.java)?.let { category ->
                                // Each category only gets its own count
                                category.copy(
                                    reminderCount = categoryCountMap[category.name.trim().lowercase()] ?: 0,
                                    name = category.name.trim()
                                )
                            }
                        }

                        onComplete(categoriesWithCount)
                    }
                    .addOnFailureListener { exception ->
                        Log.e("CategoryRepository", "Error fetching categories", exception)
                        onComplete(emptyList())
                    }
            }
            .addOnFailureListener { exception ->
                Log.e("CategoryRepository", "Error fetching reminders", exception)
                onComplete(emptyList())
            }
    }

    fun observeCategories(userId: String): Flow<List<Category>> {
        return _categoriesStateFlow.asStateFlow()
    }


    fun updateCategoryOrder(categories: List<Category>, onComplete: (Boolean) -> Unit) {
        val batch = db.batch()

        categories.forEachIndexed { index, category ->
            val updatedCategory = category.copy(order = index)
            val docRef = categoriesCollection.document(category.id)
            batch.set(docRef, updatedCategory)
        }

        batch.commit()
            .addOnSuccessListener {
                _categoriesStateFlow.value = categories
                _categories.postValue(categories)
                onComplete(true)
            }
            .addOnFailureListener { exception ->
                Log.e("CategoryRepository", "Failed to update category order: ${exception.message}")
                onComplete(false)
            }
    }

    fun getCategories(userId: String, onComplete: (List<Category>) -> Unit) {
        categoriesCollection
            .whereEqualTo("userId", userId)
            .orderBy("order", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val categories = documents.mapNotNull {
                    it.toObject(Category::class.java)
                }

                _categoriesStateFlow.value = categories
                _categories.postValue(categories)
                onComplete(categories)
            }
            .addOnFailureListener { exception ->
                Log.e("CategoryRepository", "Error fetching categories", exception)
                onComplete(emptyList())
            }
    }
}



