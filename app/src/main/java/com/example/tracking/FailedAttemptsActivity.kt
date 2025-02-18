package com.example.tracking

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.squareup.picasso.Picasso

class FailedAttemptsActivity : AppCompatActivity() {

    private lateinit var attemptsTextView: TextView
    private lateinit var intruderImageView: ImageView
    private lateinit var attemptTimeTextView: TextView

    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_failed_attempts)

        intruderImageView = findViewById(R.id.intruderImageView)
        attemptTimeTextView = findViewById(R.id.attemptTimeTextView)

        // Initialize Device Policy Manager for device admin permissions
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)

        // Retrieve androidId passed from previous activity
        val androidId = intent.getStringExtra("androidId") ?: return

        // Check if device has admin access
        checkAdminStatus()

        // Reference to Firestore document
        val docRef = firestore.collection("device_failed_attempts").document(androidId)

        // Fetch document from Firestore
        docRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val attempts = document.getLong("attempts") ?: 0
                    val timestamp = document.getLong("timestamp") ?: 0L

                    attemptTimeTextView.text = "Last Attempt Time: ${convertTimestampToDate(timestamp)}"

                    // Load the intruder photo if available
                    val photoUrl = document.getString("photoUrl")
                    if (!photoUrl.isNullOrEmpty()) {
                        val storageRef = FirebaseStorage.getInstance().getReference("intruder_photos/$androidId/$photoUrl")
                        storageRef.downloadUrl
                            .addOnSuccessListener { uri -> Picasso.get().load(uri).into(intruderImageView) }
                            .addOnFailureListener {
                                Log.e("FailedAttemptsActivity", "Error loading image: ${it.message}")
                                intruderImageView.setImageResource(R.drawable.ic_menu_gallery) // Set default image on failure
                            }
                    }
                } else {
                    // If no document exists, create a new one
                    createNewFailedAttemptsDocument(docRef)
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Failed to fetch document: ${e.message}")
                Toast.makeText(this, "Error retrieving failed attempts", Toast.LENGTH_LONG).show()
            }

        // Start the fingerprint authentication when the activity is created
        //startFingerprintAuthentication()
    }

    // Check if the app has device admin permission
    private fun checkAdminStatus() {
        if (!devicePolicyManager.isAdminActive(componentName)) {
            showAdminActivationDialog()
        }
    }

    // Show dialog for activating device admin permission
    private fun showAdminActivationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Admin Access Required")
            .setMessage("To track failed attempts, this app needs **Device Admin permissions**. Please activate it to continue.")
            .setCancelable(false)
            .setPositiveButton("Activate Now") { _, _ ->
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "This app requires admin permissions to detect unauthorized access.")
                startActivity(intent)
            }
            .setNegativeButton("Exit App") { _, _ ->
                Toast.makeText(this, "Admin access is required! Exiting app...", Toast.LENGTH_LONG).show()
                finishAffinity() // Closes the entire app
            }
            .show()
    }

    // Create a new Firestore document if no record exists for failed attempts
    private fun createNewFailedAttemptsDocument(docRef: DocumentReference) {
        val newData = hashMapOf(
            "photoUrl" to "",
            "timestamp" to 0L
        )

        docRef.set(newData)
            .addOnSuccessListener {
                Log.d("Firestore", "Created new failed attempts document")
                attemptTimeTextView.text = "Attempt Time: Not Available"
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Failed to create document: ${e.message}")
                Toast.makeText(this, "Error creating record", Toast.LENGTH_LONG).show()
            }
    }

    // Convert timestamp to a readable date format
    private fun convertTimestampToDate(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return format.format(date)
    }


}
