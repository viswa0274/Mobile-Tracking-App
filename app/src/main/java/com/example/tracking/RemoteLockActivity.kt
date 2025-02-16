package com.example.tracking

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class RemoteLockActivity : AppCompatActivity() {

    private lateinit var enterPassword: EditText
    private lateinit var confirmPassword: EditText
    private lateinit var btnSetLock: Button
    private lateinit var androidId: String
    private lateinit var dpm: DevicePolicyManager
    private lateinit var deviceAdmin: ComponentName
    private val db = FirebaseFirestore.getInstance()
    private var action: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_lock)

        enterPassword = findViewById(R.id.enterPassword)
        confirmPassword = findViewById(R.id.confirmPassword)
        btnSetLock = findViewById(R.id.btnSetLock)

        androidId = intent.getStringExtra("androidId") ?: ""
        action = intent.getStringExtra("action")

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        deviceAdmin = ComponentName(this, MyDeviceAdminReceiver::class.java)

        // Check if we should hide password UI
        val skipPasswordSetup = intent.getBooleanExtra("skipPasswordSetup", false)
        if (skipPasswordSetup) {
            findViewById<View>(R.id.passwordSetupLayout).visibility = View.GONE
            lockDevice()
            // Navigate to DeviceDetailsActivity after hiding password setup
            val intent = Intent(this, DeviceDetailsActivity::class.java)
            startActivity(intent)
            finish() // Close RemoteLockActivity
        }


        // Request Device Admin permission if not granted
        if (!dpm.isAdminActive(deviceAdmin)) {
            showAdminPermissionDialog()
        }

        // If action is "disable", attempt to disable remote lock
        if (action == "disable") {
            disableRemoteLock()
        }

        btnSetLock.setOnClickListener {
            val password = enterPassword.text.toString().trim()
            val confirmPass = confirmPassword.text.toString().trim()

            if (password.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(this, "Please enter and confirm password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPass) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            enableRemoteLock(password)
        }
    }

    private fun showAdminPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Device Admin")
            .setMessage("This app requires Device Admin permission to lock your phone remotely. Please enable it in the next screen.")
            .setPositiveButton("OK") { _, _ -> requestDeviceAdmin() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdmin)
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "This permission is required for remote lock.")
        startActivity(intent)
    }

    private fun enableRemoteLock(password: String) {
        db.collection("device")
            .whereEqualTo("androidId", androidId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.documents.first()
                    val docId = document.id

                    val updateData = mapOf(
                        "remoteLock" to true,
                        "lockPassword" to password
                    )

                    db.collection("device").document(docId)
                        .update(updateData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Remote Lock Set Successfully", Toast.LENGTH_SHORT).show()
                            lockDevice()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.e("RemoteLock", "Error updating Firestore: ${e.message}")
                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Device not found!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("RemoteLock", "Error fetching document: ${e.message}")
                Toast.makeText(this, "Error fetching document: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun lockDevice() {
        if (!dpm.isAdminActive(deviceAdmin)) {
            Toast.makeText(this, "Device Admin permission not granted!", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("device").whereEqualTo("androidId", androidId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.documents.first()
                    val password = document.getString("lockPassword") ?: ""

                    if (password.isNotEmpty()) {
                        if (dpm.resetPassword(password, 0)) {
                            Toast.makeText(this, "Remote password set successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Existing screen lock detected, using current lock", Toast.LENGTH_SHORT).show()
                        }
                    }

                    dpm.lockNow()
                    Toast.makeText(this, "Device locked", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Device not found in Firestore", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("RemoteLock", "Error fetching password: ${e.message}")
                Toast.makeText(this, "Error fetching password: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun disableRemoteLock() {
        db.collection("device")
            .whereEqualTo("androidId", androidId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.documents.first()
                    val docId = document.id

                    val updateData = mapOf(
                        "remoteLock" to false,
                        "lockPassword" to ""
                    )

                    db.collection("device").document(docId)
                        .update(updateData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Remote Lock Disabled", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.e("RemoteLock", "Error updating Firestore: ${e.message}")
                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Device not found!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("RemoteLock", "Error fetching document: ${e.message}")
                Toast.makeText(this, "Error fetching document: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
