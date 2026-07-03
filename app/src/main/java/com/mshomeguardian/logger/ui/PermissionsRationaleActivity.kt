package com.mshomeguardian.logger.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PermissionsRationaleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AlertDialog.Builder(this)
            .setTitle("Health Data Privacy")
            .setMessage(
                "Home Guardian accesses only the Health Connect metrics you allow. " +
                    "Data is synced to your configured account/device workspace and can be revoked anytime in Health Connect settings."
            )
            .setPositiveButton("Open App") { _, _ ->
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
                finish()
            }
            .setNegativeButton("Close") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}

