package com.example.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.app.AlertDialog
import android.content.DialogInterface
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
import android.telephony.TelephonyManager
import android.util.Log
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
       val simChangedView: MaterialTextView = findViewById(R.id.simchanged)
        val androidIdView: MaterialTextView = findViewById(R.id.androidId)
        val triggerAlarmLayout: LinearLayout = findViewById(R.id.triggerAlarmLayout)
        val trackLocationLayout: LinearLayout = findViewById(R.id.trackLocationLayout)
        val remoteLockLayout: LinearLayout = findViewById(R.id.remoteLockLayout)
        val setGeofenceLayout: LinearLayout = findViewById(R.id.setGeofenceLayout)
        val failedAttemptsLayout: LinearLayout = findViewById(R.id.FailedAttemptsLayout)
        val dataWipeLayout: LinearLayout = findViewById(R.id.DataWipeLayout)
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
                val simChanged = document.get("simChanged")?.toString()?.toBoolean() ?: false
                val deviceType = document.getString("deviceType") ?: "Unknown"
                val deviceModel = document.getString("model") ?: "Unknown"
                val contactNumber = document.getString("contactNumber") ?: "Not Available"
                //checkSimChange(androidd)
                // Populate the views in a tabular format
                deviceNameView.text = deviceName
                deviceTypeView.text = deviceType
                //simChangedView.text = simchange.toString()
                deviceModelView.text = deviceModel
                contactNumberView.text = contactNumber
                // Color for clarity
                simChangedView.text = if (simChanged) "SIM Changed" else "SIM Unchanged"
                simChangedView.setTextColor(if (simChanged) Color.RED else Color.GREEN)
                // Set the Trace Location button action
                remoteLockLayout.setOnClickListener {
                    val options = arrayOf("Enable Remote Lock")

                    val builder = AlertDialog.Builder(this)
                    builder.setTitle("Select an Option")
                    builder.setItems(options) { dialog, which ->
                        when (which) {
                            0 -> { // Enable Remote Lock
                                if (androidd.isNullOrEmpty()) {
                                    Toast.makeText(this, "Android ID is missing!", Toast.LENGTH_SHORT).show()
                                    return@setItems
                                }

                                val intent = Intent(this, RemoteLockActivity::class.java)
                                intent.putExtra("androidId", androidd)
                                intent.putExtra("action", "enable") // Pass action type
                                startActivity(intent)
                            }

                        }
                    }
                    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                    builder.show()
                }

                dataWipeLayout.setOnClickListener {
                    val intent = Intent(this, DataWipeActivity::class.java)
                    //intent.putExtra("fcmToken", fcmtoken)
                    intent.putExtra("androidId", androidd)
                    startActivity(intent)
                }
                failedAttemptsLayout.setOnClickListener{
                    val intent = Intent(this, FailedAttemptsActivity::class.java)
                    //intent.putExtra("fcmToken", fcmtoken)
                    intent.putExtra("androidId", androidd)
                    startActivity(intent)
                }
                trackLocationLayout.setOnClickListener {
                    val intent = Intent(this, LocationTrackActivity::class.java)
                    intent.putExtra("fcmToken", fcmtoken)
                    intent.putExtra("androidId", androidd)
                    startActivity(intent)
                }
                setGeofenceLayout.setOnClickListener {
                    val geofenceHelper = GeofenceHelper(this)
                    geofenceHelper.showGeofencePopup(this, androidd, imeiFromPreviousActivity)
                }

                triggerAlarmLayout.setOnClickListener {
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
                                showConfirmDeleteDialog(fcmtoken)
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


   /* private fun disableRemoteLock(androidId: String) {
        val db = FirebaseFirestore.getInstance()

        db.collection("device")
            .whereEqualTo("androidId", androidId) // Search by field value
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    document.reference.update(
                        mapOf(
                            "remoteLock" to false,
                            "lockPassword" to "" // Clear password
                        )
                    )
                        .addOnSuccessListener {
                            Toast.makeText(this, "Remote Lock Disabled", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }*/


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

    private fun showConfirmDeleteDialog(fcmtoken: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Remove Device")
        builder.setMessage("Are you sure you want to remove this device?.")

        builder.setPositiveButton("Yes") { dialog, _ ->
            removeDevice(fcmtoken)
            dialog.dismiss()
        }

        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
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
                        val intent = Intent(this, ViewDevicesActivity::class.java)
                        startActivity(intent)
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
