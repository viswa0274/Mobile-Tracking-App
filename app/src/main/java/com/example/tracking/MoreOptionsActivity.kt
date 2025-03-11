package com.example.tracking

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.text.InputFilter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

    @SuppressLint("MissingInflatedId")
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
        updateButton = findViewById(R.id.updateButton)

        // Always make edit icons and update button visible
        editDeviceName.visibility = View.VISIBLE
        editDeviceModel.visibility = View.VISIBLE
        editImei.visibility = View.VISIBLE
        editContactNumber.visibility = View.VISIBLE
        updateButton.visibility = View.VISIBLE
        imeiNumberEditText.filters = arrayOf(InputFilter.LengthFilter(15)) // Limit IMEI to 15 chars
        contactNumberEditText.filters = arrayOf(InputFilter.LengthFilter(10)) // Limit Contact to 10 chars

        // Fetch Android ID
        androidId = getAndroidId()
        androidIdEditText.setText(androidId)

        // Start listening for device details
        listenForDeviceUpdates(androidId)

        // Set onClickListeners for edit icons
        editDeviceName.setOnClickListener { showEditDialog("Device Name", "deviceName", deviceNameEditText.text.toString()) }
        editDeviceModel.setOnClickListener { showEditDialog("Device Model", "model", deviceModelEditText.text.toString()) }
        editImei.setOnClickListener { showEditDialog("IMEI Number", "serialNumber", imeiNumberEditText.text.toString()) }
        editContactNumber.setOnClickListener { showEditDialog("Contact Number", "contactNumber", contactNumberEditText.text.toString()) }


        // Set update button action
        updateButton.setOnClickListener {
            val imeiText = imeiNumberEditText.text.toString().trim()
            val contactText = contactNumberEditText.text.toString().trim()

            // Strict validation for IMEI: Must be exactly 15 digits
            if (imeiText.length != 15) {
                Toast.makeText(this, "IMEI must be exactly 15 numeric digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!imeiText.all { it.isDigit() }) {
                Toast.makeText(this, "IMEI must contain only numbers", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Strict validation for Contact Number: Must be exactly 10 digits
            if (contactText.length != 10) {
                Toast.makeText(this, "Contact Number must be exactly 10 numeric digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!contactText.all { it.isDigit() }) {
                Toast.makeText(this, "Contact Number must contain only numbers", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // If both inputs are valid, proceed with update
            updateDeviceInfo()
        }



    }

    private fun showEditDialog(title: String, field: String, currentValue: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Edit $title")

        val input = EditText(this)
        input.setText(currentValue)
        input.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        builder.setView(input)

        builder.setPositiveButton("Update") { _, _ ->
            val newValue = input.text.toString().trim()

            if (newValue.isBlank()) {
                Toast.makeText(this, "$title cannot be empty", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            // IMEI validation
            if (field == "serialNumber" && (newValue.length != 15 || !newValue.all { it.isDigit() })) {
                Toast.makeText(this, "IMEI must be exactly 15 numeric digits", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            // Contact number validation
            if (field == "contactNumber" && (newValue.length != 10 || !newValue.all { it.isDigit() })) {
                Toast.makeText(this, "Contact Number must be exactly 10 numeric digits", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            updateDeviceField(field, newValue) // Proceed only if valid
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        builder.show()
    }

    private fun updateDeviceField(fieldToUpdate: String, newValue: String) {
        if (newValue.isBlank()) {
            Toast.makeText(this, "Value cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        // IMEI validation
        if (fieldToUpdate == "serialNumber" && (newValue.length != 15 || !newValue.all { it.isDigit() })) {
            Toast.makeText(this, "IMEI must be exactly 15 numeric digits", Toast.LENGTH_SHORT).show()
            return
        }

        // Contact number validation
        if (fieldToUpdate == "contactNumber" && (newValue.length != 10 || !newValue.all { it.isDigit() })) {
            Toast.makeText(this, "Contact Number must be exactly 10 numeric digits", Toast.LENGTH_SHORT).show()
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

                val docId = documents.documents[0].id
                db.collection("device").document(docId)
                    .update(fieldToUpdate, newValue)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Updated successfully", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                        refreshUI(fieldToUpdate, newValue) // Update UI
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

    private fun refreshUI(field: String, newValue: String) {
        when (field) {
            "deviceName" -> deviceNameEditText.setText(newValue)
            "model" -> deviceModelEditText.setText(newValue)
            "serialNumber" -> imeiNumberEditText.setText(newValue)
            "contactNumber" -> contactNumberEditText.setText(newValue)
        }
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

    private fun disableEditing() {
        // Disable all fields
        deviceNameEditText.isEnabled = false
        deviceModelEditText.isEnabled = false
        imeiNumberEditText.isEnabled = false
        contactNumberEditText.isEnabled = false

        // Hide the update button when no field is being edited
        updateButton.visibility = View.GONE

        // Reset the active editing field
        editingField = null
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
                        disableEditing()  // Ensure fields become non-editable after update
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
