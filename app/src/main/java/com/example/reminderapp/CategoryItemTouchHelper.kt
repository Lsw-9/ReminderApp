package com.example.reminderapp

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class CategoryItemTouchHelper(
    private val adapter: ManageCategoriesAdapter
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    0
) {
    private var dragFrom: Int = -1
    private var dragTo: Int = -1

    // Store the original elevation to restore it later
    private var originalElevation: Float = 0f

    override fun onMove(
        recyclerView: RecyclerView,
        source: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = source.absoluteAdapterPosition
        val toPosition = target.absoluteAdapterPosition

        if (dragFrom == -1) {
            dragFrom = fromPosition
            // Save the original elevation when drag starts
            originalElevation = source.itemView.elevation
        }
        dragTo = toPosition

        adapter.notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        when (actionState) {
            ItemTouchHelper.ACTION_STATE_DRAG -> {
                viewHolder?.itemView?.apply {
                    alpha = 0.7f
                    // Store the original elevation before changing it
                    originalElevation = elevation
                    elevation = 16f
                }
            }
            ItemTouchHelper.ACTION_STATE_IDLE -> {
                if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                    adapter.moveItem(dragFrom, dragTo)
                }
                dragFrom = -1
                dragTo = -1
            }
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.apply {
            alpha = 1.0f
            elevation = 4f
        }

        recyclerView.post {
            if (viewHolder.adapterPosition != RecyclerView.NO_POSITION) {
                adapter.notifyItemChanged(viewHolder.adapterPosition)
            }
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

    }
}