package com.example.tracking

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class DashboardActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val firestore = FirebaseFirestore.getInstance()

    @SuppressLint("SetTextI18n", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SessionManager
        sessionManager = SessionManager(this)

        // Check if user is logged in
        val uid = sessionManager.getUserId()
        if (uid == null) {
            // If session is not valid, redirect to SignInActivity
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        }

        // Set the content view
        setContentView(R.layout.activity_dashboard)

        // Display username
        val usernameTextView: TextView = findViewById(R.id.tvUsername)
        usernameTextView.text = "Welcome"

        // Logout ImageView
        val logoutImageView: ImageView = findViewById(R.id.imgLogout)
        logoutImageView.setOnClickListener {
            sessionManager.logout()
            Toast.makeText(this, "Logged out successfully.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Add Device ImageView
        val addDeviceImageView: ImageView = findViewById(R.id.imgAddDevice)
        addDeviceImageView.setOnClickListener {
            openAddDeviceDialog()
        }

        val viewDevicesCard: LinearLayout = findViewById(R.id.cardb)
        viewDevicesCard.setOnClickListener {
            val intent = Intent(this, ViewDevicesActivity::class.java)
            intent.putExtra("userId", uid) // Pass userId to the next activity
            startActivity(intent)
        }

    }

    @SuppressLint("SetTextI18n", "MissingInflatedId", "HardwareIds")
    private fun openAddDeviceDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_device, null)
        val etDeviceName: EditText = dialogView.findViewById(R.id.etDeviceName)
        val spDeviceType: Spinner = dialogView.findViewById(R.id.spDeviceType)
        val etModel: EditText = dialogView.findViewById(R.id.etModel)
        val etSerialNumber: EditText = dialogView.findViewById(R.id.etSerialNumber)
        val etContactNumber: EditText = dialogView.findViewById(R.id.etContactNumber)
        val spFetchFCMToken: Spinner = dialogView.findViewById(R.id.spCurrentDevice) // Spinner for Yes/No

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val btnCancel: Button = dialogView.findViewById(R.id.btnCancel)
        val btnSubmit: Button = dialogView.findViewById(R.id.btnSubmit)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSubmit.setOnClickListener {
            val deviceName = etDeviceName.text.toString().trim()
            val deviceType = spDeviceType.selectedItem.toString()
            val model = etModel.text.toString().trim()
            val serialNumber = etSerialNumber.text.toString().trim()
            val contactNumber = etContactNumber.text.toString().trim()
            val fetchFCMToken = spFetchFCMToken.selectedItem.toString() // "Yes" or "No"
            val userId = sessionManager.getUserId()

            if (deviceName.isEmpty() || model.isEmpty() || serialNumber.isEmpty() || contactNumber.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
            } else if (serialNumber.length != 15) {
                Toast.makeText(this, "IMEI number must be 15 digits", Toast.LENGTH_SHORT).show()
            } else if (contactNumber.length != 10) {
                Toast.makeText(this, "Contact number must be 10 digits", Toast.LENGTH_SHORT).show()
            } else if (userId == null) {
                Toast.makeText(this, "User is not logged in", Toast.LENGTH_SHORT).show()
            } else {
                val androidId = android.provider.Settings.Secure.getString(
                    contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )

                firestore.collection("device")
                    .whereEqualTo("serialNumber", serialNumber)
                    .get()
                    .addOnSuccessListener { documents ->
                        if (!documents.isEmpty) {
                            Toast.makeText(this, "Device already added", Toast.LENGTH_SHORT).show()
                        } else {
                            val deviceData = hashMapOf(
                                "deviceName" to deviceName,
                                "deviceType" to deviceType,
                                "model" to model,
                                "serialNumber" to serialNumber,
                                "contactNumber" to contactNumber,
                                "userId" to userId,
                                "androidId" to androidId
                            )

                            if (fetchFCMToken == "Yes") {
                                // Fetch FCM token
                                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            val fcmToken = task.result
                                            deviceData["fcmToken"] = fcmToken

                                            // Insert data into Firestore
                                            addDeviceToFirestore(deviceData, dialog)
                                        } else {
                                            Toast.makeText(
                                                this,
                                                "Failed to fetch FCM token: ${task.exception?.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                            } else {
                                // Insert without FCM token
                                addDeviceToFirestore(deviceData, dialog)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Error checking serial number: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }

        dialog.show()
    }

    private fun addDeviceToFirestore(deviceData: HashMap<String, String>, dialog: AlertDialog) {
        firestore.collection("device")
            .add(deviceData)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Device added successfully",
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to add device: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}