package com.example.reminderapp

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat

class CustomCategorySpinnerAdapter(
    context: Context,
    private val items: List<String>
) : ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val text = view.findViewById<TextView>(android.R.id.text1)

        if (items[position] == "Create New") {
            text.apply {
                setTextColor(ContextCompat.getColor(context, R.color.blue_500))
                setTypeface(null, Typeface.BOLD)
            }
        } else {
            text.apply {
                setTextColor(ContextCompat.getColor(context, R.color.black))
                setTypeface(null, Typeface.NORMAL)
            }
        }

        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        val text = view.findViewById<TextView>(android.R.id.text1)

        if (items[position] == "Create New") {
            text.apply {
                setTextColor(ContextCompat.getColor(context, R.color.blue_500))
                setTypeface(null, Typeface.BOLD)
            }
        } else {
            text.apply {
                setTextColor(ContextCompat.getColor(context, R.color.black))
                setTypeface(null, Typeface.NORMAL)
            }
        }

        return view
    }
}