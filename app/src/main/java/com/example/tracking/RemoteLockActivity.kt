package com.example.tracking

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.app.ProgressDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class RemoteLockActivity : AppCompatActivity() {

    private lateinit var deviceAndroidId: String
    private lateinit var intentAndroidId: String
    private lateinit var progressDialog: ProgressDialog
    private lateinit var dpm: DevicePolicyManager
    private lateinit var deviceAdmin: ComponentName
    private val db = FirebaseFirestore.getInstance()
    private val ADMIN_REQUEST_CODE = 1
    private var lockListener: ListenerRegistration? = null


    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Processing remote lock...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // Initialize Device Policy Manager
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        deviceAdmin = ComponentName(this, MyDeviceAdminReceiver::class.java)

        // Fetch device's Android ID
        deviceAndroidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        // Retrieve androidId from intent
        intentAndroidId = intent.getStringExtra("androidId") ?: ""
        startListeningForRemoteLock()
        // Check if Device Admin is active
        if (dpm.isAdminActive(deviceAdmin)) {
            processRemoteLock()
        } else {
            showAdminPermissionDialog()
        }
    }
    private fun startListeningForRemoteLock() {
        lockListener = db.collection("device")
            .whereEqualTo("androidId", deviceAndroidId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(this, "Error listening for lock updates: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                for (document in snapshots!!.documents) {
                    val remoteLock = document.getBoolean("remoteLock") ?: false
                    if (remoteLock) {
                        lockDeviceAndReset()
                        break
                    }
                }
            }
    }
    // Prompt user to enable Device Admin
    private fun showAdminPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Device Admin")
            .setMessage("This app requires Device Admin permission to lock your device remotely. Please enable it in the next screen.")
            .setPositiveButton("OK") { _, _ -> requestDeviceAdmin() }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "Device Admin permission required!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .show()
    }

    // Request Device Admin Permission
    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdmin)
        intent.putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "This permission is required for remote locking."
        )
        startActivityForResult(intent, ADMIN_REQUEST_CODE)
    }

    // Handle the result of the Device Admin activation request
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADMIN_REQUEST_CODE) {
            if (dpm.isAdminActive(deviceAdmin)) {
                Toast.makeText(this, "Device Admin activated!", Toast.LENGTH_SHORT).show()
                processRemoteLock()
            } else {
                Toast.makeText(this, "Device Admin activation failed!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // Process the remote lock logic
    private fun processRemoteLock() {
        if (deviceAndroidId == intentAndroidId) {
            // 🔥 Directly lock the device since IDs match
            lockDeviceAndReset()
        } else {
            // If IDs do not match, set remoteLock to true for the intent's androidId
            db.collection("device")
                .whereEqualTo("androidId", intentAndroidId)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        for (document in documents) {
                            val docId = document.id
                            db.collection("device").document(docId)
                                .update("remoteLock", true)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Remote lock triggered for target device", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Error updating lock status: ${e.message}", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                        }
                    } else {
                        progressDialog.dismiss()
                        Toast.makeText(this, "Device not found!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error fetching device: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
        }
    }

    // 🔥 Lock the device and reset remoteLock to false
    private fun lockDeviceAndReset() {
        dpm.lockNow()
        Toast.makeText(this, "Device locked successfully!", Toast.LENGTH_SHORT).show()

        // Reset remoteLock to false in Firestore
        db.collection("device")
            .whereEqualTo("androidId", deviceAndroidId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    for (document in documents) {
                        val docId = document.id
                        db.collection("device").document(docId)
                            .update("remoteLock", false)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Lock status reset", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error resetting lock status: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching device for reset: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        finish()
    }
}
