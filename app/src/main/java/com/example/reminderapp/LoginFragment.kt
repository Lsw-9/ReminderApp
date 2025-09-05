package com.example.reminderapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.button.MaterialButton
import android.app.Activity
import android.util.Log

class LoginFragment : Fragment(R.layout.fragment_login) {
    private val viewModel: AuthViewModel by viewModels()
    private lateinit var usernameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var rememberMeCheckbox: CheckBox
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleLoginButton: ImageButton

    private val PREFS_NAME = "LoginPrefs"
    private val KEY_USERNAME = "username"
    private val KEY_EMAIL = "email"
    private val KEY_PASSWORD = "password"
    private val KEY_REMEMBER_ME = "rememberMe"

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                Log.d("GoogleSignIn", "Received sign in result")
                val account = task.getResult(ApiException::class.java)
                account.idToken?.let { token ->
                    Log.d("GoogleSignIn", "Got ID token, attempting sign in")
                    viewModel.signInWithGoogle(token)
                } ?: run {
                    Log.e("GoogleSignIn", "No ID token received")
                    Toast.makeText(requireContext(), "Failed to get Google account info", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                Log.e("GoogleSignIn", "Sign in failed: ${e.message}")
                Toast.makeText(requireContext(), "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d("GoogleSignIn", "Sign in cancelled by user")
            Toast.makeText(requireContext(), "Google sign in cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        usernameInput = view.findViewById(R.id.usernameInput)
        emailInput = view.findViewById(R.id.emailInput)
        passwordInput = view.findViewById(R.id.passwordInput)
        val loginButton = view.findViewById<MaterialButton>(R.id.loginButton)
        val forgotPasswordText = view.findViewById<TextView>(R.id.forgotPassword)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        rememberMeCheckbox = view.findViewById(R.id.rememberMeCheckbox)
        passwordInputLayout = view.findViewById(R.id.passwordInputLayout)
        googleLoginButton = view.findViewById(R.id.googleLogin)

        // Configure password visibility
        passwordInputLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        passwordInputLayout.setEndIconOnClickListener {
            val isPasswordVisible = passwordInput.transformationMethod == null
            passwordInput.transformationMethod = if (isPasswordVisible)
                PasswordTransformationMethod.getInstance()
            else
                null
        }

        // Configure Google Sign In
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

            googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Google Sign-In initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // Load saved credentials
        loadSavedCredentials()

        // Observe auth state
        viewModel.authState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthViewModel.AuthState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    loginButton.isEnabled = false
                    googleLoginButton.isEnabled = false
                }

                is AuthViewModel.AuthState.Success -> {
                    progressBar.visibility = View.GONE
                    loginButton.isEnabled = true
                    googleLoginButton.isEnabled = true

                    if (state.message.contains("Password reset email sent")) {

                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    } else {

                        if (rememberMeCheckbox.isChecked) {
                            saveCredentials(
                                usernameInput.text.toString().trim(),
                                emailInput.text.toString().trim(),
                                passwordInput.text.toString()
                            )
                        } else {
                            clearSavedCredentials()
                        }

                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    startActivity(Intent(requireContext(), MainActivity::class.java))
                    requireActivity().finish()
                }
            }
                is AuthViewModel.AuthState.Error -> {
                    progressBar.visibility = View.GONE
                    loginButton.isEnabled = true
                    googleLoginButton.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Login button click listener
        loginButton.setOnClickListener {
            val name = usernameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            viewModel.login(name, email, password)
        }

        // Forgot password click listener
        forgotPasswordText.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isBlank()) {
                Toast.makeText(requireContext(), "Please enter your email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.resetPassword(email)
        }

        // Google Sign In button click listener
        googleLoginButton.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun saveCredentials(username: String, email: String, password: String) {
        val sharedPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putString(KEY_USERNAME, username)
            putString(KEY_EMAIL, email)
            putString(KEY_PASSWORD, password)
            putBoolean(KEY_REMEMBER_ME, true)
            apply()
        }
    }

    private fun loadSavedCredentials() {
        val sharedPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isRemembered = sharedPrefs.getBoolean(KEY_REMEMBER_ME, false)

        if (isRemembered) {
            usernameInput.setText(sharedPrefs.getString(KEY_USERNAME, ""))
            emailInput.setText(sharedPrefs.getString(KEY_EMAIL, ""))
            passwordInput.setText(sharedPrefs.getString(KEY_PASSWORD, ""))
            rememberMeCheckbox.isChecked = true
        }
    }

    private fun clearSavedCredentials() {
        val sharedPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            remove(KEY_USERNAME)
            remove(KEY_EMAIL)
            remove(KEY_PASSWORD)
            remove(KEY_REMEMBER_ME)
            apply()
        }
    }
}

