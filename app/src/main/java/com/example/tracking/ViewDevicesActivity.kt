package com.example.tracking

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class ViewDevicesActivity : AppCompatActivity() {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_devices)

        // Initialize SessionManager
        sessionManager = SessionManager(this)

        // Fetch the userId using getUserId
        val userId = sessionManager.getUserId()
        if (userId == null) {
            Toast.makeText(this, "User is not logged in.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val devicesLayout: LinearLayout = findViewById(R.id.devicesLayout)

        fetchDevices(userId, devicesLayout)
    }

    @SuppressLint("SetTextI18n", "MissingInflatedId")
    private fun fetchDevices(userId: String, devicesLayout: LinearLayout) {
        // Create a ProgressBar to show while fetching data
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.VISIBLE
        devicesLayout.removeAllViews()

        firestore.collection("device")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                progressBar.visibility = View.GONE

                if (result.isEmpty) {
                    Toast.makeText(this, "No devices found.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Iterate through the fetched devices and create the design
                for (document in result) {
                    val deviceName = document.getString("deviceName") ?: "Unknown Device"
                    val deviceType = document.getString("deviceType") ?: "Unknown Type"
                    val imei = document.getString("serialNumber") ?: ""  // Fetch serialNumber here

                    // Inflate device_item.xml and populate the views
                    val deviceView = layoutInflater.inflate(R.layout.device_item, devicesLayout, false)

                    // Set the device name and type
                    deviceView.findViewById<TextView>(R.id.deviceName).text = "Device Name: $deviceName"
                    //deviceView.findViewById<TextView>(R.id.deviceType).text = "imee: $imei"
                    // Set the device image based on type
                    val deviceImage = deviceView.findViewById<ImageView>(R.id.deviceImage)
                    if (deviceType.equals("smartphone", ignoreCase = true)) {
                        deviceImage.setImageResource(R.drawable.smartphone) // Replace with actual drawable
                    } else {
                        deviceImage.setImageResource(R.drawable.ic_launcher_background)
                    }

                    // Set View button action
                    deviceView.findViewById<Button>(R.id.viewButton).setOnClickListener {
                        if (imei.isEmpty()) {
                            Toast.makeText(this@ViewDevicesActivity, "IMEI not available for $deviceName", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        // Pass the serialNumber (IMEI) to the DeviceDetailsActivity
                        val intent = Intent(this, DeviceDetailsActivity::class.java)
                        intent.putExtra("deviceImei", imei)
                        startActivity(intent)
                    }

                    // Add the populated device view to the layout
                    devicesLayout.addView(deviceView)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error fetching devices: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


}

