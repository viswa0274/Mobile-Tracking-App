package com.example.tracking

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MoreOptionsActivity : AppCompatActivity() {

    private lateinit var androidIdEditText: EditText
    private lateinit var deviceNameEditText: EditText
    private lateinit var deviceModelEditText: EditText
    private lateinit var imeiNumberEditText: EditText
    private lateinit var contactNumberEditText: EditText
    private lateinit var userIdEditText: EditText
    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var editDeviceName: ImageView
    private lateinit var editDeviceModel: ImageView
    private lateinit var editImei: ImageView
    private lateinit var editContactNumber: ImageView
    private lateinit var updateButton: Button

    private val db = FirebaseFirestore.getInstance()
    private var deviceListener: ListenerRegistration? = null
    private var androidId: String = ""
    private var editingField: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_more_options)

        // Initialize views
        androidIdEditText = findViewById(R.id.androidId)
        deviceNameEditText = findViewById(R.id.deviceName)
        deviceModelEditText = findViewById(R.id.deviceModel)
        imeiNumberEditText = findViewById(R.id.imeiNumber)
        contactNumberEditText = findViewById(R.id.contactNumber)
        userIdEditText = findViewById(R.id.userID)
        progressBar = findViewById(R.id.progressBar)

        editDeviceName = findViewById(R.id.editDeviceName)
        editDeviceModel = findViewById(R.id.editDeviceModel)
        editImei = findViewById(R.id.editImei)
        editContactNumber = findViewById(R.id.editContactNumber)
        updateButton = findViewById(R.id.updateButton)  // Add Update Button

        // Disable editing initially
        disableEditing()

        // Fetch Android ID
        androidId = getAndroidId()
        androidIdEditText.setText(androidId)

        // Start listening for device details
        listenForDeviceUpdates(androidId)

        // Set onClickListeners for edit icons
        editDeviceName.setOnClickListener { enableEditing(deviceNameEditText) }
        editDeviceModel.setOnClickListener { enableEditing(deviceModelEditText) }
        editImei.setOnClickListener { enableEditing(imeiNumberEditText) }
        editContactNumber.setOnClickListener { enableEditing(contactNumberEditText) }

        // Set update button action
        updateButton.setOnClickListener { updateDeviceInfo() }
    }

    @SuppressLint("HardwareIds")
    private fun getAndroidId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    private fun listenForDeviceUpdates(androidId: String) {
        progressBar.visibility = View.VISIBLE

        deviceListener = db.collection("device")
            .whereEqualTo("androidId", androidId)
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE

                if (error != null) {
                    Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    Toast.makeText(this, "Device not found", Toast.LENGTH_SHORT).show()
                } else {
                    val document = snapshot.documents.firstOrNull()
                    document?.let {
                        val deviceName = it.getString("deviceName") ?: ""
                        val deviceModel = it.getString("model") ?: ""
                        val imeiNumber = it.getString("serialNumber") ?: ""
                        val contactNumber = it.getString("contactNumber") ?: ""
                        val userId = it.getString("userId") ?: ""

                        deviceNameEditText.setText(deviceName)
                        deviceModelEditText.setText(deviceModel)
                        imeiNumberEditText.setText(imeiNumber)
                        contactNumberEditText.setText(contactNumber)
                        userIdEditText.setText(userId)
                    }
                }

                // Always show edit icons
                editDeviceName.visibility = View.VISIBLE
                editDeviceModel.visibility = View.VISIBLE
                editImei.visibility = View.VISIBLE
                editContactNumber.visibility = View.VISIBLE
            }
    }

    private fun enableEditing(editText: EditText) {
        // Disable previous editing field if any
        editingField?.isEnabled = false

        // Enable the selected field for editing
        editText.isEnabled = true
        editText.requestFocus()
        editingField = editText

        // Show update button
        updateButton.visibility = View.VISIBLE
    }

    private fun disableEditing() {
        // Disable all fields
        deviceNameEditText.isEnabled = false
        deviceModelEditText.isEnabled = false
        imeiNumberEditText.isEnabled = false
        contactNumberEditText.isEnabled = false

        // Hide update button initially
        updateButton.visibility = View.GONE
    }

    private fun updateDeviceInfo() {
        val fieldToUpdate = when (editingField) {
            deviceNameEditText -> "deviceName"
            deviceModelEditText -> "model"
            imeiNumberEditText -> "serialNumber"
            contactNumberEditText -> "contactNumber"
            else -> null
        }

        if (fieldToUpdate == null || editingField == null) {
            Toast.makeText(this, "No field selected for update", Toast.LENGTH_SHORT).show()
            return
        }

        val newValue = editingField!!.text.toString()

        if (newValue.isBlank()) {
            Toast.makeText(this, "Value cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE

        db.collection("device")
            .whereEqualTo("androidId", androidId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(this, "Device not found", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    return@addOnSuccessListener
                }

                val docId = documents.documents[0].id  // Get document ID
                db.collection("device").document(docId)
                    .update(fieldToUpdate, newValue)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Updated successfully", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                        disableEditing()  // Disable editing after update
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        deviceListener?.remove() // Remove Firestore listener to avoid memory leaks
    }
}
