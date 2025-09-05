package com.example.reminderapp

import android.util.Log
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    // Function for Google Sign In
    fun signInWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(credential).await()

                // Save user data to Firestore
                authResult.user?.let { user ->
                    val userData = UserData(
                        username = user.displayName ?: "",
                        email = user.email ?: ""
                    )

                    try {
                        firestore.collection("users")
                            .document(user.uid)
                            .set(userData)
                            .await()

                        _authState.value = AuthState.Success("Successfully signed in with Google")
                    } catch (e: Exception) {
                        Log.e("Firestore", "Error saving user data: ${e.message}")
                        // Still consider it a success if auth worked but Firestore failed
                        _authState.value = AuthState.Success("Signed in successfully")
                    }
                } ?: run {
                    _authState.value = AuthState.Error("Failed to get user information")
                }
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "Error: ${e.message}")
                _authState.value = AuthState.Error(e.message ?: "Google sign in failed")
            }
        }
    }



    sealed class AuthState {
        object Loading : AuthState()
        data class Success(val message: String) : AuthState()
        data class Error(val message: String) : AuthState()
    }

    data class UserData(
        val username: String = "",
        val email: String = ""
    )

    fun login(username: String, email: String, password: String) {
        if (!validateLoginInput(username, email, password)) return

        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                _authState.value = AuthState.Success("Login successful")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun signup(username: String, email: String, password: String, confirmPassword: String) {
        if (!validateSignupInput(username, email, password, confirmPassword)) return

        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                // Create authentication account
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                Log.d("Signup", "Auth account created with UID: ${authResult.user?.uid}")

                // Store additional user data in Firestore
                val userData = UserData(username, email)
                authResult.user?.let { user ->
                    try {
                        firestore.collection("users")
                            .document(user.uid)
                            .set(userData)
                            .await()
                        Log.d("Signup", "Firestore data stored successfully")
                        _authState.postValue(AuthState.Success("Account created successfully!"))
                    } catch (e: Exception) {
                        Log.e("Signup", "Firestore error: ${e.message}", e)
                        _authState.postValue(AuthState.Success("Account created! Some profile data may be incomplete."))
                    }
                } ?: run {
                    _authState.postValue(AuthState.Error("Failed to get user information"))
                }
            } catch (e: Exception) {
                Log.e("Signup", "Auth error: ${e.message}", e)
                _authState.postValue(AuthState.Error(e.message ?: "Signup failed"))
            }
        }
    }


    private fun validateLoginInput(username: String, email: String, password: String): Boolean {
        when {
            username.isBlank() -> {
                _authState.value = AuthState.Error("Username cannot be empty")
                return false
            }
            email.isBlank() -> {
                _authState.value = AuthState.Error("Email cannot be empty")
                return false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                _authState.value = AuthState.Error("Invalid email format")
                return false
            }
            password.length < 6 -> {
                _authState.value = AuthState.Error("Password must be at least 6 characters")
                return false
            }
        }
        return true
    }

    private fun validateSignupInput(
        username: String,
        email: String,
        password: String,
        confirmPassword: String

    ): Boolean {
        println("Email being validated: '$email'")
        println("Email pattern match result: ${android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()}")
        when {
            username.isBlank() -> {
                _authState.value = AuthState.Error("Username cannot be empty")
                return false
            }
            email.trim().isBlank() -> {
                _authState.value = AuthState.Error("Email cannot be empty")
                return false

            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                _authState.value = AuthState.Error("Invalid email format")
                return false
            }
            password.length < 6 -> {
                _authState.value = AuthState.Error("Password must be at least 6 characters")
                return false
            }
            password != confirmPassword -> {
                _authState.value = AuthState.Error("Passwords do not match")
                return false

            }

        }
        return true
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _authState.value = AuthState.Error("Email cannot be empty")
            return
        }

        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                _authState.value = AuthState.Success("Password reset email sent")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Password reset failed")
            }
        }


    }
}