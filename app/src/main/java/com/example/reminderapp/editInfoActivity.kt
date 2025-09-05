package com.example.reminderapp

import android.os.Bundle
import android.util.Patterns
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.reminderapp.databinding.ActivityEditInfoBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.EmailAuthProvider

class editInfoActivity : AppCompatActivity() {


    private lateinit var binding: ActivityEditInfoBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Edit Info"

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        loadUserData()


        binding.saveButton.setOnClickListener {
            val currentPassword = binding.editCurrentPassword.text.toString().trim()
            val newPassword = binding.editNewPassword.text.toString().trim()
            val confirmPassword = binding.editConfirmPassword.text.toString().trim()

            if (newPassword.isNotEmpty() && confirmPassword.isNotEmpty()) {
                if (newPassword == confirmPassword) {
                    verifyAndChangePassword(currentPassword, newPassword)
                } else {
                    Toast.makeText(this, "New password does not match!", Toast.LENGTH_SHORT).show()
                }
            } else {
                updateProfileInfo()
            }
        }
    }


    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        val userRef = firestore.collection("users").document(currentUser.uid)
        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                binding.editUsername.setText(document.getString("username") ?: "")
                binding.editEmail.setText(document.getString("email") ?: "")
            } else {
                Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun verifyAndChangePassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentPassword.isEmpty()) {
            Toast.makeText(this, "Enter your current password", Toast.LENGTH_SHORT).show()
            return
        }

        val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
        user.reauthenticate(credential).addOnCompleteListener { authTask ->
            if (authTask.isSuccessful) {
                user.updatePassword(newPassword).addOnCompleteListener { updateTask ->
                    if (updateTask.isSuccessful) {
                        Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Failed to update password", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Incorrect current password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateProfileInfo() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedUsername = binding.editUsername.text.toString().trim()
        val updatedEmail = binding.editEmail.text.toString().trim()

        val userRef = firestore.collection("users").document(currentUser.uid)
        val updates = hashMapOf(
            "username" to updatedUsername,
            "email" to updatedEmail
        )

        userRef.update(updates as Map<String, Any>)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {  // This is the Toolbar's back button
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

}