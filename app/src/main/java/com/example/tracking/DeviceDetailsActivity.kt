package com.example.tracking

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textview.MaterialTextView

class DeviceDetailsActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()

    @SuppressLint("SetTextI18n", "WrongViewCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_details)

        // Initialize views as per the provided XML layout
        val deviceNameView: MaterialTextView = findViewById(R.id.deviceName)
        val deviceTypeView: MaterialTextView = findViewById(R.id.deviceType)
        val deviceImeiView: MaterialTextView = findViewById(R.id.deviceImei)
        val deviceModelView: MaterialTextView = findViewById(R.id.deviceModel)
        val contactNumberView: MaterialTextView = findViewById(R.id.contactNumber)
        val traceLocationButton: MaterialButton = findViewById(R.id.traceLocationButton)
        val progressBar: CircularProgressIndicator = findViewById(R.id.progressBar)

        // Get the device IMEI from the intent
        val deviceImei = intent.getStringExtra("deviceImei")
        if (deviceImei.isNullOrEmpty()) {
            Toast.makeText(this, "Device IMEI not provided.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Show the progress bar
        progressBar.visibility = View.VISIBLE

        // Fetch the device details from Firestore
        firestore.collection("device")
            .whereEqualTo("serialNumber", deviceImei)
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
                val deviceName = document.getString("deviceName") ?: "Unknown"
                val deviceType = document.getString("deviceType") ?: "Unknown"
                val deviceModel = document.getString("model") ?: "Unknown"
                val contactNumber = document.getString("contactNumber") ?: "Not Available"

                // Populate the views in a tabular format
                deviceNameView.text = deviceName
                deviceTypeView.text = deviceType
                deviceImeiView.text = deviceImei
                deviceModelView.text = deviceModel
                contactNumberView.text = contactNumber

                // Set the Trace Location button action
                traceLocationButton.setOnClickListener {
                    val intent = Intent(this, LocationTrackActivity::class.java)
                    intent.putExtra("deviceImei", deviceImei)  // Pass IMEI as extra
                    startActivity(intent)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Error fetching device details: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}

