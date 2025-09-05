package com.example.reminderapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.reminderapp.R
import com.google.android.material.card.MaterialCardView
import java.util.UUID

data class Category(
    val id: String = UUID.randomUUID().toString(), // Auto-generate ID if not provided
    val name: String,
    val userId: String,
    var isSelected: Boolean = false,
    var reminderCount: Int = 0,
    val order: Int = 0
){

    constructor() : this("", "", "",false, 0,0)
}

class CategoryAdapter(
    private val onCategorySelected: (Category) -> Unit,
    private val onAddCategory: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val categories = mutableListOf<Category>()
    private var selectedPosition = 0

    companion object {
        private const val VIEW_TYPE_CATEGORY = 0
        private const val VIEW_TYPE_ADD = 1
    }

    fun updateCategories(newCategories: List<Category>) {
        categories.clear()
        categories.addAll(newCategories.map {
            it.copy(name = it.name.trim().lowercase())
        })
        selectedPosition = 0
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == categories.size) VIEW_TYPE_ADD else VIEW_TYPE_CATEGORY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_CATEGORY -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_category, parent, false)
                CategoryViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_add_category, parent, false)
                AddCategoryViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CategoryViewHolder -> {
                val category = categories[position]
                holder.bind(category)
                holder.itemView.setOnClickListener {
                    val previousSelected = selectedPosition
                    selectedPosition = holder.adapterPosition
                    notifyItemChanged(previousSelected)
                    notifyItemChanged(selectedPosition)
                    onCategorySelected(category) // Pass the selected category
                }
            }
            is AddCategoryViewHolder -> {
                holder.itemView.setOnClickListener { onAddCategory() }
            }
        }
    }

    override fun getItemCount(): Int = categories.size + 1

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: LinearLayout= itemView.findViewById(R.id.categoryCard)
        private val nameText: TextView = itemView.findViewById(R.id.categoryName)
        private val countText: TextView = itemView.findViewById(R.id.categoryCount)

        fun bind(category: Category) {
            nameText.text = category.name
            countText.text = category.reminderCount.toString()

            val isSelected = adapterPosition == selectedPosition
            val context = itemView.context
            val backgroundColor = ContextCompat.getColor(context, if (isSelected) R.color.blue_500 else R.color.white)
            val textColor = ContextCompat.getColor(context, if (isSelected) R.color.white else R.color.black)

            cardView.setBackgroundColor(backgroundColor)
            nameText.setTextColor(textColor)
            countText.setTextColor(textColor)
        }
    }

    inner class AddCategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}