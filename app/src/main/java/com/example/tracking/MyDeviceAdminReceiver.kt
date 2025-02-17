package com.example.tracking

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    private val maxFailedAttempts = 3
    private val firestore = FirebaseFirestore.getInstance()

    // Handle PIN failure
    @Deprecated("Deprecated in Java")
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)

        // Retrieve androidId from SharedPreferences or another secure method
        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val androidId = sharedPrefs.getString("androidId", null) ?: return
        Log.d("DeviceAdminReceiver", "Failed attempt detected for $androidId")

        // Update failed attempts in Firestore
        val attemptsRef = firestore.collection("device_failed_attempts").document(androidId)
        attemptsRef.get().addOnSuccessListener { document ->
            Log.d("DeviceAdminReceiver", "Fetched document: $document")

            val failedAttempts = (document.getLong("attempts") ?: 0) + 1
            Log.d("DeviceAdminReceiver", "Failed attempts: $failedAttempts")

            // Check if max failed attempts reached
            if (failedAttempts >= maxFailedAttempts) {
                Log.d("DeviceAdminReceiver", "Max failed attempts reached. Starting service.")
                startIntruderCaptureService(context)
            }

            // Update Firestore with new failed attempts count and timestamp
            attemptsRef.update(
                "attempts", failedAttempts,
                "timestamp", System.currentTimeMillis()
            ).addOnSuccessListener {
                Log.d("DeviceAdminReceiver", "Document updated successfully.")
            }.addOnFailureListener { e ->
                Log.e("DeviceAdminReceiver", "Error updating document: ${e.message}")
            }
        }
    }

    // Handle device admin enabled
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("DeviceAdminReceiver", "Device Admin Enabled")
    }

    // Handle device admin disabled
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("DeviceAdminReceiver", "Device Admin Disabled")
    }

    // Method to start IntruderCaptureService
    private fun startIntruderCaptureService(context: Context) {
        val serviceIntent = Intent(context, IntruderCaptureService::class.java)
        context.startService(serviceIntent)
    }

    // Handle Fingerprint failures (using BiometricPrompt)
    fun handleFingerprintFailure(context: Context) {
        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val failedFingerprintAttempts = sharedPrefs.getInt("failed_fingerprint_attempts", 0) + 1
        sharedPrefs.edit().putInt("failed_fingerprint_attempts", failedFingerprintAttempts).apply()

        Log.d("DeviceAdminReceiver", "Fingerprint failed attempts: $failedFingerprintAttempts")

        if (failedFingerprintAttempts >= maxFailedAttempts) {
            Log.d("DeviceAdminReceiver", "Max failed fingerprint attempts reached. Starting service.")
            startIntruderCaptureService(context)
        }
    }
}
