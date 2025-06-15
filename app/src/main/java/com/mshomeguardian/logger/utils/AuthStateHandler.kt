package com.mshomeguardian.logger.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.mshomeguardian.logger.workers.WorkerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Handles authentication state changes and manages services accordingly
 */
object AuthStateHandler {
    private const val TAG = "AuthStateHandler"

    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isInitialized = false
    private var lastKnownUser: FirebaseUser? = null
    private var appContext: Context? = null

    /**
     * Initialize authentication state monitoring
     */
    fun initialize(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "AuthStateHandler already initialized")
            return
        }

        try {
            appContext = context.applicationContext
            Log.d(TAG, "Initializing AuthStateHandler...")

            val auth = FirebaseAuth.getInstance()

            authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                handleAuthStateChange(user)
            }

            auth.addAuthStateListener(authStateListener!!)
            lastKnownUser = auth.currentUser
            isInitialized = true

            Log.d(TAG, "AuthStateHandler initialized successfully")

            // Perform initial state check
            performInitialStateCheck(context)

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AuthStateHandler", e)
            isInitialized = false
        }
    }

    /**
     * Clean up the auth state listener
     */
    fun cleanup() {
        try {
            Log.d(TAG, "Cleaning up AuthStateHandler...")

            authStateListener?.let { listener ->
                FirebaseAuth.getInstance().removeAuthStateListener(listener)
            }

            authStateListener = null
            lastKnownUser = null
            appContext = null
            isInitialized = false

            Log.d(TAG, "AuthStateHandler cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during AuthStateHandler cleanup", e)
        }
    }

    /**
     * Handle authentication state changes
     */
    private fun handleAuthStateChange(user: FirebaseUser?) {
        try {
            val context = appContext ?: return

            val wasSignedIn = lastKnownUser != null
            val isSignedIn = user != null

            // Check if this is actually a state change
            if (wasSignedIn == isSignedIn && lastKnownUser?.uid == user?.uid) {
                return
            }

            Log.d(TAG, "Authentication state changed: wasSignedIn=$wasSignedIn, isSignedIn=$isSignedIn")

            if (user != null) {
                Log.d(TAG, "User signed in: ${user.email}")
                onUserSignedIn(context, user)
            } else {
                Log.d(TAG, "User signed out")
                onUserSignedOut(context)
            }

            lastKnownUser = user

        } catch (e: Exception) {
            Log.e(TAG, "Error handling auth state change", e)
        }
    }

    /**
     * Called when user successfully signs in
     */
    private fun onUserSignedIn(context: Context, user: FirebaseUser) {
        scope.launch {
            try {
                Log.d(TAG, "Processing user sign-in for: ${user.email}")

                // Small delay to ensure Firebase is ready
                delay(1000)

                // Re-initialize data sync manager
                DataSyncManager.initialize(context)

                // Update user preferences
                updateUserPreferences(context, user)

                Log.d(TAG, "Services restarted after sign-in")

            } catch (e: Exception) {
                Log.e(TAG, "Error processing user sign-in", e)
            }
        }
    }

    /**
     * Called when user signs out
     */
    private fun onUserSignedOut(context: Context) {
        scope.launch {
            try {
                Log.d(TAG, "Processing user sign-out")

                // Stop all background services
                WorkerScheduler.cancelAllWork(context)

                // Stop recording service
                DataSyncManager.toggleRecordingService(context, false)

                // Clear user preferences
                clearUserPreferences(context)

                Log.d(TAG, "Services stopped after sign-out")

            } catch (e: Exception) {
                Log.e(TAG, "Error processing user sign-out", e)
            }
        }
    }

    /**
     * Update user-specific preferences
     */
    private fun updateUserPreferences(context: Context, user: FirebaseUser) {
        try {
            val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_signed_in_user_id", user.uid)
                .putString("last_signed_in_email", user.email)
                .putLong("last_sign_in_time", System.currentTimeMillis())
                .apply()

            Log.d(TAG, "User preferences updated")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user preferences", e)
        }
    }

    /**
     * Clear user-specific preferences
     */
    private fun clearUserPreferences(context: Context) {
        try {
            val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .remove("last_signed_in_user_id")
                .remove("last_signed_in_email")
                .putLong("last_sign_out_time", System.currentTimeMillis())
                .apply()

            // Clear auth credentials
            AuthManager.clearSavedCredentials(context)

            Log.d(TAG, "User preferences cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing user preferences", e)
        }
    }

    /**
     * Perform initial state check
     */
    private fun performInitialStateCheck(context: Context) {
        scope.launch {
            try {
                delay(500)

                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    Log.d(TAG, "Initial state check: User is signed in")
                    DataSyncManager.initialize(context)
                } else {
                    Log.d(TAG, "Initial state check: No user signed in")
                    WorkerScheduler.cancelAllWork(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in initial state check", e)
            }
        }
    }

    /**
     * Check if authentication is required before performing an operation
     */
    fun requireAuthentication(operation: String): Boolean {
        val isAuthenticated = AuthManager.isSignedIn()

        if (!isAuthenticated) {
            Log.w(TAG, "Operation '$operation' requires authentication but user is not signed in")
        }

        return isAuthenticated
    }

    /**
     * Force refresh of authentication state
     */
    fun refreshAuthState() {
        try {
            val user = FirebaseAuth.getInstance().currentUser
            Log.d(TAG, "Forcing auth state refresh")
            handleAuthStateChange(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing auth state", e)
        }
    }

    /**
     * Check if handler is properly initialized
     */
    fun isInitialized(): Boolean {
        return isInitialized && authStateListener != null
    }

    /**
     * Get current authentication status
     */
    fun getAuthStatus(): String {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                "Signed in as: ${user.email}"
            } else {
                "Not signed in"
            }
        } catch (e: Exception) {
            "Error getting status: ${e.message}"
        }
    }
}