package com.example.tracking

import android.annotation.SuppressLint
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import android.provider.Settings

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

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

    // Detect failed screen unlock attempts
    @SuppressLint("HardwareIds")
    @Deprecated("Deprecated in Java")
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        Log.d("DeviceAdminReceiver", "Password failed for device: $androidId")

        updateFailedAttempts(androidId, "PIN")
    }

    private fun updateFailedAttempts(androidId: String, method: String) {
        val db = FirebaseFirestore.getInstance()
        val deviceRef = db.collection("device_failed_attempts")
            .whereEqualTo("androidId", androidId)

        deviceRef.get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    for (document in result) {
                        val documentId = document.id
                        val currentCount = document.getLong("failedAttempts") ?: 0

                        db.collection("device_failed_attempts").document(documentId)
                            .update("failedAttempts", currentCount + 1, "lastFailedMethod", method)
                            .addOnSuccessListener {
                                Log.d("Firestore", "$method failed attempt updated for $androidId")
                            }
                            .addOnFailureListener { e ->
                                Log.e("Firestore", "Failed to update failed attempts", e)
                            }
                    }
                } else {
                    // If no record exists, create a new document
                    val newEntry = mapOf(
                        "androidId" to androidId,
                        "failedAttempts" to 1,
                        "lastFailedMethod" to method
                    )
                    db.collection("device_failed_attempts").add(newEntry)
                        .addOnSuccessListener {
                            Log.d("Firestore", "New failed attempt record created for $androidId")
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Failed to create failed attempt record", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error fetching device_failed_attempts", e)
            }
    }
}
