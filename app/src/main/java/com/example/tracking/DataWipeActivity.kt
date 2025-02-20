package com.example.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.Executor
import java.util.function.Consumer

class DataWipeActivity : AppCompatActivity() {
    private var androidId: String? = null // Current device's Android ID
    private var targetAndroidId: String? = null // Target Android ID (Device B)
    private var devicePolicyManager: DevicePolicyManager? = null
    private var adminComponent: ComponentName? = null
    private var biometricPrompt: BiometricPrompt? = null
    private var promptInfo: PromptInfo? = null
    private var executor: Executor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        androidId = getAndroidId() // Get current device's Android ID
        targetAndroidId = intent.getStringExtra("androidId")

        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

        if (!hasStoragePermission()) {
            requestStoragePermission()
        } else {
            checkDeviceAdminAndProceed()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AlertDialog.Builder(this)
                .setTitle("Storage Access Required")
                .setMessage("This app needs access to your files to perform data wipe. Grant access now?")
                .setPositiveButton("Allow") { dialog: DialogInterface?, which: Int ->
                    val intent =
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
                .setNegativeButton(
                    "Cancel"
                ) { dialog: DialogInterface?, which: Int -> finish() }
                .setCancelable(false)
                .show()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 100
            )
        }
    }

    private fun checkDeviceAdminAndProceed() {
        if (!devicePolicyManager!!.isAdminActive(adminComponent!!)) {
            promptDeviceAdminActivation()
        } else {
            showDataWipeConfirmation(targetAndroidId)
        }
    }

    private fun promptDeviceAdminActivation() {
        AlertDialog.Builder(this)
            .setTitle("Device Admin Required")
            .setMessage("This app requires device admin privileges to perform a remote wipe. Enable it now?")
            .setPositiveButton("Enable") { dialog: DialogInterface?, which: Int ->
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "This permission is required for remote data wipe."
                )
                startActivity(intent)
            }
            .setNegativeButton(
                "Cancel"
            ) { dialog: DialogInterface?, which: Int -> finish() }
            .show()
    }

    private fun showDataWipeConfirmation(targetAndroidId: String?) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Data Wipe")
            .setMessage("Are you sure you want to delete the folder?")
            .setPositiveButton(
                "Yes"
            ) { dialog: DialogInterface?, which: Int -> authenticateUser(targetAndroidId) }
            .setNegativeButton(
                "No"
            ) { dialog: DialogInterface?, which: Int -> finish() }
            .show()
    }

    private fun authenticateUser(targetAndroidId: String?) {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt =
            BiometricPrompt(this, executor!!, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d("BiometricAuth", "Authentication successful")
                    setDataWipeFlag(targetAndroidId, true)
                    Toast.makeText(
                        this@DataWipeActivity,
                        "Data wipe initiated successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
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

        promptInfo = PromptInfo.Builder()
            .setTitle("Authenticate")
            .setSubtitle("Use fingerprint to confirm data wipe")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt!!.authenticate(promptInfo!!)
    }

    private fun setDataWipeFlag(androidId: String?, value: Boolean) {
        val db = FirebaseFirestore.getInstance()
        db.collection("device")
            .whereEqualTo("androidId", androidId)
            .get()
            .addOnSuccessListener { result: QuerySnapshot ->
                if (result.isEmpty) {
                    Log.e("DataWipe", "No device found with androidId: $androidId")
                    return@addOnSuccessListener
                }
                result.forEach(Consumer { document: QueryDocumentSnapshot ->
                    val documentId = document.id
                    db.collection("device").document(documentId)
                        .set(
                            object : HashMap<String?, Any?>() {
                                init {
                                    put("dataWipe", value)
                                }
                            },
                            SetOptions.merge()
                        )
                        .addOnSuccessListener { aVoid: Void? ->
                            Log.d(
                                "DataWipe",
                                "DataWipe flag set to $value for document: $documentId"
                            )
                        }
                        .addOnFailureListener { e: Exception? ->
                            Log.e(
                                "DataWipe",
                                "Failed to set dataWipe field",
                                e
                            )
                        }
                })
            }
            .addOnFailureListener { e: Exception? ->
                Log.e(
                    "DataWipe",
                    "Error fetching device document",
                    e
                )
            }
    }

    @SuppressLint("HardwareIds")
    private fun getAndroidId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }
}