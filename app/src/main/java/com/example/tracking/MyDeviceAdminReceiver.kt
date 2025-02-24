package com.example.tracking

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    private val db = FirebaseFirestore.getInstance()
    private var listenerRegistration: ListenerRegistration? = null
    private lateinit var adminReceiver: MyDeviceAdminReceiver

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

        updateFailedAttempts(context, androidId, "PIN")
    }

    private fun updateFailedAttempts(context: Context, androidId: String, method: String) {
        val deviceRef = db.collection("device_failed_attempts")
            .whereEqualTo("androidId", androidId)

        // Get current date-time in IST
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        val currentTimeIST = sdf.format(System.currentTimeMillis())

        deviceRef.get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    for (document in result) {
                        val documentId = document.id
                        val currentCount = document.getLong("failedAttempts") ?: 0

                        db.collection("device_failed_attempts").document(documentId)
                            .update(
                                "failedAttempts", currentCount + 1,
                                "lastFailedMethod", method,
                                "lastAttempted", currentTimeIST,  // Update timestamp
                                "attemptDetected", true
                            )
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
                        "lastFailedMethod" to method,
                        "lastAttempted" to currentTimeIST,  // Store timestamp
                        "attemptDetected" to false
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

    fun startListening(context: Context) {
        val sessionManager = SessionManager(context)
        val myUserId = sessionManager.getUserId()

        if (myUserId == null) {
            Log.e("DeviceAdminReceiver", "User ID not found in shared preferences")
            return
        }

        listenerRegistration = db.collection("device_failed_attempts")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("Firestore", "Failed to listen for failed attempts", error)
                    return@addSnapshotListener
                }

                for (doc in snapshots!!.documents) {
                    val androidId = doc.getString("androidId") ?: continue
                    val failedAttempts = doc.getLong("failedAttempts") ?: 0
                    val attemptDetected = doc.getBoolean("attemptDetected") ?: false

                    if (!attemptDetected) continue // Skip if attemptDetected is already false

                    // Fetch userId from devices collection
                    db.collection("device")
                        .whereEqualTo("androidId", androidId)
                        .get()
                        .addOnSuccessListener { deviceResult ->
                            for (deviceDoc in deviceResult) {
                                val userId = deviceDoc.getString("userId") ?: continue
                                val deviceName = deviceDoc.getString("deviceName") ?: "Unknown Device"
                                val model = deviceDoc.getString("model") ?: "Unknown Model"

                                // Check if the userId matches the logged-in user
                                if (userId == myUserId) {
                                    triggerNotification(context, deviceName, model, failedAttempts)

                                    // Reset attemptDetected to false after triggering notification
                                    db.collection("device_failed_attempts").document(doc.id)
                                        .update("attemptDetected", false)
                                        .addOnSuccessListener {
                                            Log.d("Firestore", "attemptDetected reset to false for $androidId")
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("Firestore", "Failed to reset attemptDetected", e)
                                        }
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Failed to fetch device details", e)
                        }
                }
            }
    }


    fun stopListening() {
        listenerRegistration?.remove()
    }

    private fun triggerNotification(context: Context, deviceName: String, model: String, failedAttempts: Long) {
        Log.d("SecurityAlert", "Sending failed attempts notification")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val defaultSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channelId = "failed_attempts_channel"

        // Create Notification Channel for Android 8+ (Oreo and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Failed Unlock Attempts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                setSound(defaultSoundUri, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Build the notification
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Unauthorized Access Attempt!")
            .setContentText("Someone tried to unlock your device ($deviceName - $model).")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)  // Allows user to dismiss it
            .setSound(defaultSoundUri)  // Set default notification sound
            .build()

        notificationManager.notify(2, notification)  // Unique ID for failed attempts notification
    }



}
