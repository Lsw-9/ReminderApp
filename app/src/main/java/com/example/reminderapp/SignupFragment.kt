package com.example.reminderapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseApp
import android.os.Handler
import android.os.Looper

class SignupFragment : Fragment(R.layout.fragment_signup) {
    private val viewModel: AuthViewModel by viewModels()
    private lateinit var googleSignInClient: GoogleSignInClient



    companion object {
        private const val RC_SIGN_IN = 9001
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleSignInResult(task)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Google sign in failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(requireContext(), "Google sign in cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)

            account?.idToken?.let { token ->
                viewModel.signInWithGoogle(token)
            }
        } catch (e: ApiException) {
            Log.w("SignIn", "signInResult:failed code=${e.statusCode}")
            Toast.makeText(requireContext(), "Sign in failed: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize existing views
        val emailInput = view.findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.passwordInput)
        val usernameInput = view.findViewById<TextInputEditText>(R.id.usernameInput)
        val confirmPasswordInput = view.findViewById<TextInputEditText>(R.id.confirmPasswordInput)
        val signupButton = view.findViewById<MaterialButton>(R.id.signupButton)
        val googleSignInButton = view.findViewById<ImageButton>(R.id.googleSignInButton)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val passwordInputLayout = view.findViewById<TextInputLayout>(R.id.passwordInputLayout)
        val confirmPasswordInputLayout =
            view.findViewById<TextInputLayout>(R.id.confirmPasswordInputLayout)


        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        googleSignInClient.signOut()

        googleSignInButton.setOnClickListener {
            try {
                progressBar.visibility = View.VISIBLE
                signInLauncher.launch(googleSignInClient.signInIntent)
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Error launching sign in: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        passwordInputLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        passwordInputLayout.setEndIconOnClickListener {
            val isPasswordVisible = passwordInput.transformationMethod == null
            passwordInput.transformationMethod = if (isPasswordVisible)
                PasswordTransformationMethod.getInstance()
            else
                null
        }

        // Password visibility toggle for confirm password field
        confirmPasswordInputLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        confirmPasswordInputLayout.setEndIconOnClickListener {
            val isPasswordVisible = confirmPasswordInput.transformationMethod == null
            confirmPasswordInput.transformationMethod = if (isPasswordVisible)
                PasswordTransformationMethod.getInstance()
            else
                null
        }


        // Observe auth state
        viewModel.authState.observe(viewLifecycleOwner) { state ->
            Log.d("SignupFragment", "Auth state changed to: $state")

            when (state) {
                is AuthViewModel.AuthState.Loading -> {
                    Log.d("SignupFragment", "Showing loading state")
                    progressBar.visibility = View.VISIBLE
                    signupButton.isEnabled = false
                    googleSignInButton.isEnabled = false
                }

                is AuthViewModel.AuthState.Success -> {
                    Log.d("SignupFragment", "Showing success state: ${state.message}")
                    progressBar.visibility = View.GONE
                    signupButton.isEnabled = true
                    googleSignInButton.isEnabled = true

                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Account created successfully! Please log in.", Toast.LENGTH_LONG).show()

                        // Navigate to login tab after a short delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                // Switch to login tab (position 0)
                                (activity as? AuthActivity)?.let { authActivity ->
                                    authActivity.viewPager.currentItem = 0
                                }
                            } catch (e: Exception) {
                                Log.e("SignupFragment", "Navigation error: ${e.message}", e)
                            }
                        }, 500)
                    }
                }

                is AuthViewModel.AuthState.Error -> {
                    Log.d("SignupFragment", "Showing error state: ${state.message}")
                    progressBar.visibility = View.GONE
                    signupButton.isEnabled = true
                    googleSignInButton.isEnabled = true

                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    }


                    // Add specific handling for email already in use
                    if (state.message.contains("Email address is already in use")) {
                        Toast.makeText(
                            requireContext(),
                            "This email is already registered. Try logging in.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Email/Password signup
        signupButton.setOnClickListener {
            Log.d("SignupFragment", "Signup button clicked")
            val username = usernameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val confirmPassword = confirmPasswordInput.text.toString()

            Log.d("SignupFragment", "Attempting signup with email: $email")
            viewModel.signup(username, email, password, confirmPassword)
        }

        // Google Sign In
        googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        try {
            Log.d("GoogleSignIn", "Starting sign in process")
            val signInIntent = googleSignInClient.signInIntent
            Log.d("GoogleSignIn", "Created sign in intent")
            signInLauncher.launch(signInIntent)
            Log.d("GoogleSignIn", "Launched sign in intent")
        } catch (e: Exception) {
            Log.e("GoogleSignIn", "Error launching sign in: ${e.message}")
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
