package com.example.tracking
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore

class SimChangeWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val firestore = FirebaseFirestore.getInstance()
    private val sharedPreferences: SharedPreferences = applicationContext.getSharedPreferences("sim_prefs", Context.MODE_PRIVATE)
    private val channelId = "sim_change_channel"

    override fun doWork(): Result {
        checkSimChange()
        return Result.success()
    }

    @SuppressLint("HardwareIds")
    private fun checkSimChange() {
        val androidId = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
        fetchDeviceDetails(androidId)
    }





    private fun fetchDeviceDetails(androidId: String) {
        firestore.collection("device")
            .whereEqualTo("androidId", androidId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.d("SimChangeWorker", "No device found with androidId $androidId")
                } else {
                    for (document in documents) {
                        val userId = document.getString("userId") ?: return@addOnSuccessListener
                        val registeredNumber = document.getString("contactNumber")
                        val deviceName = document.getString("deviceName") ?: "Unknown Device"
                        val deviceModel = document.getString("model") ?: "Unknown Model"
                        val currentSimNumber = getSimPhoneNumber()
                      //  listenForRemoteLock(document.id)
                        // Save registeredNumber offline
                        saveRegisteredNumber(registeredNumber)

                        // Check SIM change
                        if (isInternetAvailable()) {
                            listenForSimChange(userId)
                            if (registeredNumber != null && (currentSimNumber == null || registeredNumber != currentSimNumber)) {
                                updateSimChangeStatus(document.id, userId, deviceName, deviceModel)
                            } else {
                                // If SIM has not changed, update simChanged to false
                                firestore.collection("device")
                                    .whereEqualTo("androidId", androidId)
                                    .get()
                                    .addOnSuccessListener { documents ->
                                        for (document in documents) {
                                            firestore.collection("device").document(document.id)
                                                .update("simChanged", false)
                                                .addOnSuccessListener {
                                                    Log.d("SimChangeWorker", "Updated simChanged to false for device: ${document.id}")
                                                }
                                                .addOnFailureListener { e ->
                                                    Log.e("SimChangeWorker", "Failed to update simChanged: ${e.message}")
                                                }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("SimChangeWorker", "Failed to fetch device details: ${e.message}")
                                    }

                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("SimChangeWorker", "Failed to fetch device details: ${e.message}")
            }
    }

    private fun updateSimChangeStatus(deviceId: String, userId: String, deviceName: String, deviceModel: String) {
        val currentSimNumber = getSimPhoneNumber()

        firestore.collection("device").document(deviceId)
            .update("simChanged", currentSimNumber == null)
            .addOnSuccessListener {
                Log.d("SimChangeWorker", "Updated simChanged for device: $deviceId")
                sendSimChangeNotification(deviceName, deviceModel)
            }
            .addOnFailureListener { e ->
                Log.e("SimChangeWorker", "Failed to update simChanged: ${e.message}")
            }
    }

    private fun listenForSimChange(userId: String) {
        firestore.collection("device")
            .whereEqualTo("simChanged", true)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("SimChangeListener", "Listen failed: ${e.message}")
                    return@addSnapshotListener
                }

                for (doc in snapshots!!) {
                    val docUserId = doc.getString("userId") ?: continue
                    if (docUserId == userId) {
                        val changedDeviceName = doc.getString("deviceName") ?: "Unknown Device"
                        val changedDeviceModel = doc.getString("model") ?: "Unknown Model"

                        sendSimChangeNotification(changedDeviceName, changedDeviceModel)
                    }
                }
            }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun getSimPhoneNumber(): String? {
        val telephonyManager = applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return if (telephonyManager.simState == TelephonyManager.SIM_STATE_READY) {
            telephonyManager.line1Number
        } else {
            null
        }
    }

    private fun sendSimChangeNotification(deviceName: String, deviceModel: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "SIM Change Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("SIM Change Alert")
            .setContentText("$deviceName ($deviceModel)'s SIM card changed or removed!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(3, notification)
    }

    private fun saveRegisteredNumber(contactNumber: String?) {
        sharedPreferences.edit().putString("registered_number", contactNumber).apply()
    }

    private fun getStoredRegisteredNumber(): String? {
        return sharedPreferences.getString("registered_number", null)
    }





    private fun isInternetAvailable(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val networkInfo = cm.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }
}
