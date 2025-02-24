package com.example.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore

class SimChangeListenerActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private val channelId = "sim_change_channel"
    private lateinit var sessionManager: SessionManager // Initialize session manager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_sim_change_listener) // Create a layout if needed

        sessionManager = SessionManager(this)
        val userId = sessionManager.getUserId() // Fetch userId from session

        if (userId != null) {
            listenForSimChange(userId)
        } else {
            Log.e("SimChangeListener", "User ID is null, cannot start listening")
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

    private fun sendSimChangeNotification(deviceName: String, deviceModel: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "SIM Change Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SIM Change Alert")
            .setContentText("$deviceName ($deviceModel)'s SIM card changed or removed!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(3, notification)
    }
}
