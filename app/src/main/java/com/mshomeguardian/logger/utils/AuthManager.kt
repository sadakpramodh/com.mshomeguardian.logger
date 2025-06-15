package com.mshomeguardian.logger.utils

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Manages Firebase Authentication for the Home Guardian app
 */
object AuthManager {
    private const val TAG = "AuthManager"

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    /**
     * Get the current authenticated user
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    /**
     * Check if user is currently signed in
     */
    fun isSignedIn(): Boolean {
        return getCurrentUser() != null
    }

    /**
     * Get the current user's UID (for Firestore security rules)
     */
    fun getCurrentUserId(): String? {
        return getCurrentUser()?.uid
    }

    /**
     * Sign in with email and password
     */
    suspend fun signInWithEmailAndPassword(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            if (result.user != null) {
                Log.d(TAG, "Sign in successful for user: ${result.user?.email}")
                AuthResult.Success(result.user!!)
            } else {
                Log.e(TAG, "Sign in failed: No user returned")
                AuthResult.Error("Authentication failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed", e)
            AuthResult.Error(e.message ?: "Authentication failed")
        }
    }

    /**
     * Create a new user account
     */
    suspend fun createUserWithEmailAndPassword(email: String, password: String): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            if (result.user != null) {
                Log.d(TAG, "Account creation successful for user: ${result.user?.email}")
                AuthResult.Success(result.user!!)
            } else {
                Log.e(TAG, "Account creation failed: No user returned")
                AuthResult.Error("Account creation failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Account creation failed", e)
            AuthResult.Error(e.message ?: "Account creation failed")
        }
    }

    /**
     * Sign in anonymously (for testing or guest access)
     */
    suspend fun signInAnonymously(): AuthResult {
        return try {
            val result = auth.signInAnonymously().await()
            if (result.user != null) {
                Log.d(TAG, "Anonymous sign in successful")
                AuthResult.Success(result.user!!)
            } else {
                Log.e(TAG, "Anonymous sign in failed: No user returned")
                AuthResult.Error("Anonymous authentication failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous sign in failed", e)
            AuthResult.Error(e.message ?: "Anonymous authentication failed")
        }
    }

    /**
     * Sign out the current user
     */
    fun signOut() {
        try {
            auth.signOut()
            Log.d(TAG, "User signed out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out", e)
        }
    }

    /**
     * Save authentication credentials for automatic sign-in
     */
    fun saveCredentials(context: Context, email: String, password: String) {
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("email", email)
            .putString("password", password)
            .apply()
        Log.d(TAG, "Credentials saved for automatic sign-in")
    }

    /**
     * Get saved credentials for automatic sign-in
     */
    fun getSavedCredentials(context: Context): Pair<String?, String?> {
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val email = prefs.getString("email", null)
        val password = prefs.getString("password", null)
        return Pair(email, password)
    }

    /**
     * Clear saved credentials
     */
    fun clearSavedCredentials(context: Context) {
        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "Saved credentials cleared")
    }

    /**
     * Attempt automatic sign-in using saved credentials
     */
    suspend fun attemptAutoSignIn(context: Context): AuthResult {
        val (email, password) = getSavedCredentials(context)

        return if (email != null && password != null) {
            Log.d(TAG, "Attempting automatic sign-in with saved credentials")
            signInWithEmailAndPassword(email, password)
        } else {
            Log.d(TAG, "No saved credentials found")
            AuthResult.Error("No saved credentials")
        }
    }

    /**
     * Send password reset email
     */
    suspend fun sendPasswordResetEmail(email: String): AuthResult {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Log.d(TAG, "Password reset email sent to: $email")
            AuthResult.Success(null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send password reset email", e)
            AuthResult.Error(e.message ?: "Failed to send password reset email")
        }
    }
}

/**
 * Sealed class to represent authentication results
 */
sealed class AuthResult {
    data class Success(val user: FirebaseUser?) : AuthResult()
    data class Error(val message: String) : AuthResult()
}