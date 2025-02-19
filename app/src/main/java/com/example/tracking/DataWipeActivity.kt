package com.example.tracking

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.Executor

class DataWipeActivity : AppCompatActivity() {

    private lateinit var androidId: String // Current device's Android ID
    private lateinit var targetAndroidId: String // Target Android ID (Device B)
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var executor: Executor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        androidId = getAndroidId() // Get current device's Android ID
        targetAndroidId = intent.getStringExtra("androidId") ?: ""

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

        // Check if device admin is active
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            promptDeviceAdminActivation()
        } else {
            // If admin is active, proceed with data wipe confirmation
            showDataWipeConfirmation(targetAndroidId)
        }
    }

    private fun promptDeviceAdminActivation() {
        AlertDialog.Builder(this)
            .setTitle("Device Admin Required")
            .setMessage("This app requires device admin privileges to perform a remote wipe. Enable it now?")
            .setPositiveButton("Enable") { _, _ ->
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "This permission is required for remote data wipe.")
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDataWipeConfirmation(targetAndroidId: String) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Data Wipe")
            .setMessage("Are you sure you want to delete the folder?")
            .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                authenticateUser(targetAndroidId) // Authenticate before proceeding
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun authenticateUser(targetAndroidId: String) {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d("BiometricAuth", "Authentication successful")

                // Set data wipe flag in Firestore
                setDataWipeFlag(targetAndroidId, true)

                // Notify the previous activity that the data has changed
                setResult(RESULT_OK)

                finish() // Close activity
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.e("BiometricAuth", "Authentication failed")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.e("BiometricAuth", "Authentication error: $errString")
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate")
            .setSubtitle("Use fingerprint to confirm data wipe")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }


    private fun setDataWipeFlag(androidId: String, value: Boolean) {
        val db = FirebaseFirestore.getInstance()

        db.collection("device")
            .whereEqualTo("androidId", androidId) // Find the document where androidId matches
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Log.e("DataWipe", "No device found with androidId: $androidId")
                    return@addOnSuccessListener
                }

                for (document in result) {
                    val documentId = document.id // Get the actual document ID

                    // Set the "dataWipe" field (this will add if it doesn’t exist)
                    db.collection("device").document(documentId)
                        .set(mapOf("dataWipe" to value), com.google.firebase.firestore.SetOptions.merge()) // Merge ensures the field is set
                        .addOnSuccessListener {
                            Log.d("DataWipe", "DataWipe flag successfully set to $value for document: $documentId")
                        }
                        .addOnFailureListener { e ->
                            Log.e("DataWipe", "Failed to set dataWipe field", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("DataWipe", "Error fetching device document", e)
            }
    }



    @SuppressLint("HardwareIds")
    private fun getAndroidId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }
}
