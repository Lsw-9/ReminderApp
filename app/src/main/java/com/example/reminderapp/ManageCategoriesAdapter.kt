package com.example.reminderapp

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class ManageCategoriesAdapter(
    private val onMenuClick: (Category, View) -> Unit,
    private val onOrderChanged: (List<Category>) -> Unit
) : RecyclerView.Adapter<ManageCategoriesAdapter.CategoryViewHolder>() {

    private val categories = mutableListOf<Category>()

    fun updateCategories(newCategories: List<Category>) {
        Log.d("ManageCategoriesAdapter", "Updating categories: ${newCategories.map { it.name }}")
        categories.clear()
        categories.addAll(newCategories)
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= categories.size || toPosition >= categories.size) {
            return
        }

        // Get the full category object with all its properties
        val item = categories[fromPosition]

        // Remove and reinsert to reorder
        categories.removeAt(fromPosition)
        categories.add(toPosition, item)

        // Update only the order property while preserving all other properties
        categories.forEachIndexed { index, category ->

            categories[index] = category.copy(order = index)
        }

        // Notify the listener about the change to persist the new order
        onOrderChanged(categories.toList())
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manage_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.categoryName)
        private val countText: TextView = itemView.findViewById(R.id.categoryCount)
        private val menuButton: ImageButton = itemView.findViewById(R.id.menuButton)
        private val cardView: CardView = itemView as CardView

        fun bind(category: Category) {
            nameText.text = category.name
            countText.text = category.reminderCount.toString()

            cardView.cardElevation = 4f

            menuButton.setOnClickListener { view ->
                onMenuClick(category, view)
            }
        }
    }
}