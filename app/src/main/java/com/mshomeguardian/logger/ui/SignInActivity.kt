package com.mshomeguardian.logger.ui

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.ActionCodeSettings

/**
 * Activity that launches FirebaseUI sign-in flow with email/password,
 * phone, and passwordless email link providers.
 */
class SignInActivity : AppCompatActivity() {

    private val signInLauncher =
        registerForActivityResult(FirebaseAuthUIActivityResultContract()) { res ->
            onSignInResult(res)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchSignIn()
    }

    private fun launchSignIn() {
        val actionCodeSettings = ActionCodeSettings.newBuilder()
            .setAndroidPackageName(packageName, true, null)
            .setHandleCodeInApp(true)
            .setUrl("https://example.page.link")
            .build()

        val providers = arrayListOf(
            // Passwordless email link
            AuthUI.IdpConfig.EmailBuilder()
                .enableEmailLinkSignIn()
                .setActionCodeSettings(actionCodeSettings)
                .build(),
            // Phone authentication
            AuthUI.IdpConfig.PhoneBuilder().build(),
            // Regular email/password
            AuthUI.IdpConfig.EmailBuilder().build()
        )

        val intent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setIsSmartLockEnabled(false)
            .build()

        signInLauncher.launch(intent)
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            // User successfully signed in
            finish()
        } else {
            // Sign in failed or cancelled
            finish()
        }
    }
}
