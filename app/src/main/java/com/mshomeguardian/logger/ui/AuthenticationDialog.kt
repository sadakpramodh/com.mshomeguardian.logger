package com.mshomeguardian.logger.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.mshomeguardian.logger.R
import com.mshomeguardian.logger.utils.AuthManager
import com.mshomeguardian.logger.utils.AuthResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog for user authentication (sign in or create account)
 */
class AuthenticationDialog(
    private val context: Context,
    private val onAuthSuccess: () -> Unit,
    private val onAuthFailure: (String) -> Unit
) {

    private lateinit var dialog: AlertDialog
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var signInButton: Button
    private lateinit var createAccountButton: Button
    private lateinit var forgotPasswordButton: Button

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_authentication, null)

        // Initialize views
        emailEditText = view.findViewById(R.id.etEmail)
        passwordEditText = view.findViewById(R.id.etPassword)
        signInButton = view.findViewById(R.id.btnSignIn)
        createAccountButton = view.findViewById(R.id.btnCreateAccount)
        forgotPasswordButton = view.findViewById(R.id.btnForgotPassword)

        // Set up click listeners
        signInButton.setOnClickListener { handleSignIn() }
        createAccountButton.setOnClickListener { handleCreateAccount() }
        forgotPasswordButton.setOnClickListener { handleForgotPassword() }

        // Pre-fill email if saved
        val (savedEmail, _) = AuthManager.getSavedCredentials(context)
        if (savedEmail != null) {
            emailEditText.setText(savedEmail)
        }

        // Create and show dialog
        dialog = AlertDialog.Builder(context)
            .setTitle("Home Guardian Authentication")
            .setView(view)
            .setCancelable(false)
            .create()

        dialog.show()
    }

    private fun handleSignIn() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (email.isEmpty()) {
            emailEditText.error = "Email is required"
            return
        }

        if (password.isEmpty()) {
            passwordEditText.error = "Password is required"
            return
        }

        setButtonsEnabled(false)

        CoroutineScope(Dispatchers.Main).launch {
            when (val result = AuthManager.signInWithEmailAndPassword(email, password)) {
                is AuthResult.Success -> {
                    // Save credentials for future automatic sign-in
                    AuthManager.saveCredentials(context, email, password)

                    Toast.makeText(context, "Sign in successful!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    onAuthSuccess()
                }
                is AuthResult.Error -> {
                    Toast.makeText(context, "Sign in failed: ${result.message}", Toast.LENGTH_LONG).show()
                    setButtonsEnabled(true)
                    onAuthFailure(result.message)
                }
            }
        }
    }

    private fun handleCreateAccount() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (email.isEmpty()) {
            emailEditText.error = "Email is required"
            return
        }

        if (password.isEmpty()) {
            passwordEditText.error = "Password is required"
            return
        }

        if (password.length < 6) {
            passwordEditText.error = "Password must be at least 6 characters"
            return
        }

        setButtonsEnabled(false)

        CoroutineScope(Dispatchers.Main).launch {
            when (val result = AuthManager.createUserWithEmailAndPassword(email, password)) {
                is AuthResult.Success -> {
                    // Save credentials for future automatic sign-in
                    AuthManager.saveCredentials(context, email, password)

                    Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    onAuthSuccess()
                }
                is AuthResult.Error -> {
                    Toast.makeText(context, "Account creation failed: ${result.message}", Toast.LENGTH_LONG).show()
                    setButtonsEnabled(true)
                    onAuthFailure(result.message)
                }
            }
        }
    }

    private fun handleForgotPassword() {
        val email = emailEditText.text.toString().trim()

        if (email.isEmpty()) {
            emailEditText.error = "Enter your email address"
            return
        }

        setButtonsEnabled(false)

        CoroutineScope(Dispatchers.Main).launch {
            when (val result = AuthManager.sendPasswordResetEmail(email)) {
                is AuthResult.Success -> {
                    Toast.makeText(context, "Password reset email sent! Check your inbox.", Toast.LENGTH_LONG).show()
                    setButtonsEnabled(true)
                }
                is AuthResult.Error -> {
                    Toast.makeText(context, "Failed to send reset email: ${result.message}", Toast.LENGTH_LONG).show()
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        signInButton.isEnabled = enabled
        createAccountButton.isEnabled = enabled
        forgotPasswordButton.isEnabled = enabled
        emailEditText.isEnabled = enabled
        passwordEditText.isEnabled = enabled
    }
}