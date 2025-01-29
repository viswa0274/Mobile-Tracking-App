package com.example.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import android.os.Vibrator
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.content.ContextCompat

class DeviceDetailsActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private var mediaPlayer: MediaPlayer? = null

    @SuppressLint("SetTextI18n", "WrongViewCast", "HardwareIds", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_details)

        // Initialize views as per the provided XML layout
        val deviceNameView: MaterialTextView = findViewById(R.id.deviceName)
        val deviceTypeView: MaterialTextView = findViewById(R.id.deviceType)
        val deviceImeiView: MaterialTextView = findViewById(R.id.deviceImei)
        val deviceModelView: MaterialTextView = findViewById(R.id.deviceModel)
        val contactNumberView: MaterialTextView = findViewById(R.id.contactNumber)
        val androidIdView: MaterialTextView = findViewById(R.id.androidId)
        val traceLocationButton: MaterialButton = findViewById(R.id.traceLocationButton)
        val triggerAlarmButton: MaterialButton = findViewById(R.id.triggerAlarmButton)
        val setGeofenceButton: MaterialButton = findViewById(R.id.setGeofenceButton)
        val threeDots: ImageView = findViewById(R.id.threeDots)
        val progressBar: CircularProgressIndicator = findViewById(R.id.progressBar)

        // Retrieve IMEI from the Intent
        val imeiFromPreviousActivity = intent.getStringExtra("deviceImei") ?: "Unknown IMEI"

        // Display the IMEI from the previous activity
        deviceImeiView.text = imeiFromPreviousActivity

        // Fetch Android ID and display it
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        androidIdView.text = androidId

        // Show the progress bar
        progressBar.visibility = View.VISIBLE

        // Fetch the device details from Firestore using the IMEI
        firestore.collection("device")
            .whereEqualTo("serialNumber", imeiFromPreviousActivity)
            .get()
            .addOnSuccessListener { result ->
                progressBar.visibility = View.GONE
                if (result.isEmpty) {
                    Toast.makeText(this, "No device details found.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                // Assuming there is only one result
                val document = result.documents[0]
                val documentId = document.id
                val androidd = document.getString("androidId") ?: "unknown"
                val deviceName = document.getString("deviceName") ?: "Unknown"
                val fcmtoken = document.getString("fcmToken") ?: "Unknown"
                val deviceType = document.getString("deviceType") ?: "Unknown"
                val deviceModel = document.getString("model") ?: "Unknown"
                val contactNumber = document.getString("contactNumber") ?: "Not Available"

                // Populate the views in a tabular format
                deviceNameView.text = deviceName
                deviceTypeView.text = deviceType
                deviceModelView.text = deviceModel
                contactNumberView.text = contactNumber

                // Set the Trace Location button action
                traceLocationButton.setOnClickListener {
                    val intent = Intent(this, LocationTrackActivity::class.java)
                    intent.putExtra("fcmToken", fcmtoken)
                    intent.putExtra("androidId", androidd)
                    startActivity(intent)
                }
                setGeofenceButton.setOnClickListener {
                    val geofenceHelper = GeofenceHelper(this)
                    geofenceHelper.showGeofencePopup(this, androidd, imeiFromPreviousActivity)
                }

                triggerAlarmButton.setOnClickListener {
                    triggerAlarm(fcmtoken)
                    showStopAlarmPopup(fcmtoken)  // Show the stop alarm popup when alarm is triggered
                }

                // Set up the three dots (popup menu)
                threeDots.setOnClickListener { view ->
                    val popupMenu = PopupMenu(this, view)
                    val inflater = popupMenu.menuInflater
                    inflater.inflate(R.menu.device_menu, popupMenu.menu)

                    popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                        when (item.itemId) {
                            R.id.removeDevice -> {
                                removeDevice(fcmtoken)
                                true
                            }

                            else -> false
                        }
                    }

                    popupMenu.show()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error fetching device details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    @SuppressLint("MissingInflatedId")
    //@SuppressLint("InflateParams")
    private fun showStopAlarmPopup(fcmtoken: String) {
        val layoutInflater = layoutInflater
        val view = layoutInflater.inflate(R.layout.dialog_stop_alarm, null)
        val popupWindow = PopupWindow(view, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, true)

        // Set up the stop alarm button within the popup
        val stopButton: MaterialButton = view.findViewById(R.id.stopAlarmButton)
        stopButton.setOnClickListener {
            stopAlarm(fcmtoken)
            popupWindow.dismiss()  // Dismiss the popup when clicked
        }

        // Show the popup at the center of the screen
        popupWindow.showAtLocation(findViewById(android.R.id.content), 0, 0, 0)
    }


    private fun stopAlarm(fcmtoken: String?) {
        if (fcmtoken.isNullOrEmpty()) {
            Toast.makeText(this, "FCM Token not provided.", Toast.LENGTH_SHORT).show()
            return
        }

        val progressBar: CircularProgressIndicator = findViewById(R.id.progressBar)
        progressBar.visibility = View.VISIBLE

        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
                it.release()
            }
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.abandonAudioFocus(null)

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.cancel()

        progressBar.visibility = View.GONE
        Toast.makeText(this, "Alarm stopped", Toast.LENGTH_SHORT).show()

        firestore.collection("alarms")
            .document(fcmtoken!!)
            .set(mapOf("alarm" to false))
            .addOnSuccessListener {}
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to stop alarm: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun triggerAlarm(fcmToken: String) {
        val progressBar: CircularProgressIndicator = findViewById(R.id.progressBar)
        progressBar.visibility = View.VISIBLE

        firestore.collection("device")
            .whereEqualTo("serialNumber", intent.getStringExtra("deviceImei") ?: "Unknown IMEI")
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Device not found in Firestore.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val document = result.documents[0]
                val storedFcmToken = document.getString("fcmToken") ?: ""

                FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            progressBar.visibility = View.GONE
                            Toast.makeText(this, "Failed to get FCM token.", Toast.LENGTH_SHORT).show()
                            return@addOnCompleteListener
                        }

                        val currentFcmToken = task.result

                        if (storedFcmToken == currentFcmToken) {
                            firestore.collection("alarms")
                                .document(fcmToken)
                                .set(mapOf("alarm" to true))
                                .addOnSuccessListener {
                                    progressBar.visibility = View.GONE
                                    Toast.makeText(this, "Alarm triggered successfully!", Toast.LENGTH_SHORT).show()
                                    //playAlarmSound()

                                    // Start the AlarmForegroundService to listen for changes
                                    val intent = Intent(this, AlarmForegroundService::class.java).apply {
                                        putExtra("fcmToken", fcmToken) // Pass FCM token to the service
                                    }
                                    ContextCompat.startForegroundService(this, intent) // Start the service
                                }
                                .addOnFailureListener { e ->
                                    progressBar.visibility = View.GONE
                                    Toast.makeText(this, "Failed to trigger alarm: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            firestore.collection("alarms")
                                .document(storedFcmToken)
                                .set(mapOf("alarm" to true))
                                .addOnSuccessListener {
                                    progressBar.visibility = View.GONE
                                    Toast.makeText(this, "Triggering Alarm", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    progressBar.visibility = View.GONE
                                    Toast.makeText(this, "Failed to set alarm: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error fetching device details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun removeDevice(fcmtoken: String?) {
        if (fcmtoken.isNullOrEmpty()) {
            Toast.makeText(this, "FCM Token not provided.", Toast.LENGTH_SHORT).show()
            return
        }

        val progressBar: CircularProgressIndicator = findViewById(R.id.progressBar)
        progressBar.visibility = View.VISIBLE

        firestore.collection("device")
            .whereEqualTo("fcmToken", fcmtoken)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "No device found to remove.", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    return@addOnSuccessListener
                }

                val document = result.documents[0]
                val documentId = document.id

                firestore.collection("device").document(documentId)
                    .delete()
                    .addOnSuccessListener {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Device removed successfully.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Error removing device: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error removing device: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }



}
