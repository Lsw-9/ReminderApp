package com.example.reminderapp

interface SearchableFragment {
    fun showSearchUI()
    fun hideSearchUI()
    fun searchReminders(query: String)
}