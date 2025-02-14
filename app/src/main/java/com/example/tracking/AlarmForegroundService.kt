package com.example.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore

class AlarmForegroundService : Service() {

    private val firestore = FirebaseFirestore.getInstance()
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fcmToken = intent?.getStringExtra("fcmToken") ?: ""

        if (fcmToken.isNotEmpty()) {
            // Start foreground service with a notification
            //startForeground(1, createNotification())
            monitorAlarmState(fcmToken)
        } else {
            Log.e("AlarmService", "No FCM Token provided")
            stopSelf()
        }

        return START_STICKY
    }

    // Monitor the Firestore 'alarms' collection for changes
    private fun monitorAlarmState(fcmToken: String) {
        firestore.collection("alarms")
            .document(fcmToken)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("AlarmService", "Error listening to alarm state: ${e.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val alarm = snapshot.getBoolean("alarm") ?: false
                    if (alarm) {
                        playAlarm()
                    } else {
                        stopAlarm()
                    }
                }
            }

    }

    // Create a notification for the foreground service


    // Play the alarm sound and start vibration
    private fun playAlarm() {
        if (mediaPlayer == null) {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer.create(this, ringtoneUri)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        }

        if (vibrator == null) {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator?.vibrate(longArrayOf(0, 1000, 1000), 0) // Vibrate continuously
        }
    }

    // Stop the alarm sound and vibration
    private fun stopAlarm() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
                it.release()
            }
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
