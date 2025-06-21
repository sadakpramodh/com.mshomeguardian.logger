package com.mshomeguardian.logger.ui

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.utils.AuthManager

/**
 * Custom authentication activity that properly handles sign-in vs sign-up flow
 */
class SignInActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SignInActivity"
    }

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var signInButton: Button
    private lateinit var createAccountButton: Button
    private lateinit var forgotPasswordButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user is already signed in
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "User already signed in: ${currentUser.email}")
            startMainActivity()
            return
        }

        setContentView(R.layout.activity_signin)
        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        emailEditText = findViewById(R.id.etEmail)
        passwordEditText = findViewById(R.id.etPassword)
        signInButton = findViewById(R.id.btnSignIn)
        createAccountButton = findViewById(R.id.btnCreateAccount)
        forgotPasswordButton = findViewById(R.id.btnForgotPassword)
        progressBar = findViewById(R.id.progressBar)
        titleText = findViewById(R.id.tvTitle)
        subtitleText = findViewById(R.id.tvSubtitle)

        // Pre-fill email if saved
        val (savedEmail, _) = AuthManager.getSavedCredentials(this)
        if (savedEmail != null) {
            emailEditText.setText(savedEmail)
        }
    }

    private fun setupClickListeners() {
        signInButton.setOnClickListener {
            handleSignIn()
        }

        createAccountButton.setOnClickListener {
            handleCreateAccount()
        }

        forgotPasswordButton.setOnClickListener {
            handleForgotPassword()
        }
    }

    private fun handleSignIn() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (!validateInput(email, password)) {
            return
        }

        setLoadingState(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setLoadingState(false)

                if (task.isSuccessful) {
                    // Sign in success
                    Log.d(TAG, "signInWithEmail:success")
                    val user = auth.currentUser

                    // Save credentials for future auto sign-in
                    AuthManager.saveCredentials(this, email, password)

                    Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
                    startMainActivity()
                } else {
                    // Sign in failed
                    val exception = task.exception
                    Log.w(TAG, "signInWithEmail:failure", exception)

                    when (exception) {
                        is FirebaseAuthInvalidUserException -> {
                            // User doesn't exist
                            showError("No account found with this email. Please create an account first.")
                            highlightCreateAccountButton()
                        }
                        is FirebaseAuthInvalidCredentialsException -> {
                            // Wrong password
                            showError("Incorrect password. Please try again or reset your password.")
                            passwordEditText.error = "Incorrect password"
                            passwordEditText.requestFocus()
                        }
                        else -> {
                            showError("Sign in failed: ${exception?.message}")
                        }
                    }
                }
            }
    }

    private fun handleCreateAccount() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (!validateInput(email, password)) {
            return
        }

        if (password.length < 6) {
            passwordEditText.error = "Password must be at least 6 characters"
            passwordEditText.requestFocus()
            return
        }

        setLoadingState(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setLoadingState(false)

                if (task.isSuccessful) {
                    // Account creation success
                    Log.d(TAG, "createUserWithEmail:success")
                    val user = auth.currentUser

                    // Save credentials for future auto sign-in
                    AuthManager.saveCredentials(this, email, password)

                    Toast.makeText(this, "Account created successfully! Welcome!", Toast.LENGTH_SHORT).show()
                    startMainActivity()
                } else {
                    // Account creation failed
                    val exception = task.exception
                    Log.w(TAG, "createUserWithEmail:failure", exception)

                    when (exception) {
                        is FirebaseAuthUserCollisionException -> {
                            // Email already exists
                            showError("An account with this email already exists. Please sign in instead.")
                            highlightSignInButton()
                        }
                        is FirebaseAuthWeakPasswordException -> {
                            // Weak password
                            showError("Password is too weak. Please choose a stronger password.")
                            passwordEditText.error = "Password too weak"
                            passwordEditText.requestFocus()
                        }
                        else -> {
                            showError("Account creation failed: ${exception?.message}")
                        }
                    }
                }
            }
    }

    private fun handleForgotPassword() {
        val email = emailEditText.text.toString().trim()

        if (TextUtils.isEmpty(email)) {
            emailEditText.error = "Enter your email address"
            emailEditText.requestFocus()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Enter a valid email address"
            emailEditText.requestFocus()
            return
        }

        setLoadingState(true)

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                setLoadingState(false)

                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Password reset email sent to $email. Check your inbox.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val exception = task.exception
                    Log.w(TAG, "sendPasswordResetEmail:failure", exception)

                    when (exception) {
                        is FirebaseAuthInvalidUserException -> {
                            showError("No account found with this email address.")
                        }
                        else -> {
                            showError("Failed to send reset email: ${exception?.message}")
                        }
                    }
                }
            }
    }

    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true

        if (TextUtils.isEmpty(email)) {
            emailEditText.error = "Email is required"
            emailEditText.requestFocus()
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Enter a valid email address"
            emailEditText.requestFocus()
            isValid = false
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.error = "Password is required"
            if (isValid) passwordEditText.requestFocus()
            isValid = false
        }

        return isValid
    }

    private fun setLoadingState(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        signInButton.isEnabled = !isLoading
        createAccountButton.isEnabled = !isLoading
        forgotPasswordButton.isEnabled = !isLoading
        emailEditText.isEnabled = !isLoading
        passwordEditText.isEnabled = !isLoading

        if (isLoading) {
            signInButton.text = "Please wait..."
            createAccountButton.text = "Please wait..."
        } else {
            signInButton.text = "Sign In"
            createAccountButton.text = "Create Account"
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun highlightSignInButton() {
        // Highlight the sign in button to guide user
        signInButton.requestFocus()
        subtitleText.text = "This email is already registered. Please sign in with your password."
    }

    private fun highlightCreateAccountButton() {
        // Highlight the create account button to guide user
        createAccountButton.requestFocus()
        subtitleText.text = "No account found. Please create a new account with this email."
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // If user presses back, close the app
        finishAffinity()
    }
}