package com.example.tracking
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import android.widget.Toast
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore

class SimChangeWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val prefs: SharedPreferences =
        applicationContext.getSharedPreferences("SimPrefs", Context.MODE_PRIVATE)
    private val channelId = "sim_change_channel"
    private val db = FirebaseFirestore.getInstance()

    override fun doWork(): Result {
        checkSimChange()
        return Result.success()
    }

    @SuppressLint("HardwareIds")
    private fun checkSimChange() {
        val androidId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val currentSimNumber = getSimPhoneNumber()

        val savedSimNumber = prefs.getString("simNumber", null)

        if (savedSimNumber == null) {
            // First-time setup: Store SIM number
            prefs.edit().putString("simNumber", currentSimNumber).apply()
            Log.d("SimChangeWorker", "Stored SIM number for the first time: $currentSimNumber")
        } else if (savedSimNumber != currentSimNumber) {
            // SIM changed
            Log.d("SimChangeWorker", "SIM changed! Old: $savedSimNumber, New: $currentSimNumber")

            // Fetch device details (userId, deviceName, model) from Firestore based on Android ID
            getDeviceDetailsFromFirestore(androidId) { userId, deviceName, deviceModel ->
                if (userId != null && deviceName != null && deviceModel != null) {
                    // Fetch contact numbers from Firestore
                    getContactNumbersFromFirestore(userId) { contactNumbers ->
                        // Send SMS alerts
                        sendSimChangeSMS(contactNumbers, deviceName, deviceModel)
                        sendTestSMS("8428152797")
                    }
                }
            }

            // Send Notification to Current Device
            sendSimChangeNotification()

            // Update stored SIM number
            //prefs.edit().putString("simNumber", currentSimNumber).apply()
        }
    }
    private fun sendTestSMS(phoneNumber: String) {
        val smsManager = SmsManager.getDefault()
        val message = "Test SMS - SIM Change Worker is working correctly!"

        try {
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Toast.makeText(applicationContext, "Test SMS sent to $phoneNumber", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "Failed to send Test SMS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun getSimPhoneNumber(): String? {
        val telephonyManager =
            applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val simState = telephonyManager.simState

        if (simState == TelephonyManager.SIM_STATE_ABSENT || simState == TelephonyManager.SIM_STATE_NOT_READY) {
            return null
        }

        return telephonyManager.line1Number
    }

    private fun sendSimChangeNotification() {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SIM Change Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("SIM Change Alert")
            .setContentText("Your device's SIM card has changed or been removed!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(3, notification)
    }

    private fun sendSimChangeSMS(
        contactNumbers: List<String>,
        deviceName: String,
        deviceModel: String
    ) {
        val smsManager = SmsManager.getDefault()
        val message =
            "ALERT! The SIM card in your device ($deviceName - $deviceModel) has been changed or removed."

        for (number in contactNumbers) {
            try {
                smsManager.sendTextMessage(number, null, message, null, null)
                // Show success message as a Toast
                Toast.makeText(
                    applicationContext,
                    "SMS sent successfully to $number",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                // Show failure message as a Toast
                Toast.makeText(
                    applicationContext,
                    "Failed to send SMS to $number: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getDeviceDetailsFromFirestore(
        androidId: String,
        callback: (String?, String?, String?) -> Unit
    ) {
        db.collection("device")
            .whereEqualTo("androidId", androidId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    for (document in documents) {
                        val userId = document.getString("userId")
                        val deviceName = document.getString("deviceName")
                        val deviceModel = document.getString("model")
                        callback(userId, deviceName, deviceModel)
                        return@addOnSuccessListener
                    }
                } else {
                    // Show error message as a Toast
                    Toast.makeText(
                        applicationContext,
                        "No matching device found for androidId: $androidId",
                        Toast.LENGTH_LONG
                    ).show()
                    callback(null, null, null)
                }
            }
            .addOnFailureListener { exception ->
                // Show error message as a Toast
                Toast.makeText(
                    applicationContext,
                    "Firestore error: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
                callback(null, null, null)
            }
    }

    private fun getContactNumbersFromFirestore(userId: String, callback: (List<String>) -> Unit) {
        val contactNumbers = mutableListOf<String>()

        db.collection("device")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val phoneNumber = document.getString("contactNumber")
                    if (!phoneNumber.isNullOrEmpty()) {
                        contactNumbers.add(phoneNumber)
                    }
                }
                callback(contactNumbers)
            }
            .addOnFailureListener { exception ->
                // Show error message as a Toast
                Toast.makeText(
                    applicationContext,
                    "Firestore error: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
                callback(emptyList())
            }
    }
}