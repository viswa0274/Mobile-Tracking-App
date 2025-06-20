package com.example.tracking


import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import android.Manifest
import android.app.NotificationChannel
import java.util.concurrent.TimeUnit
import androidx.work.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File

class DashboardActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var dataWipeListenerRegistration: ListenerRegistration? = null
    private lateinit var adminReceiver: MyDeviceAdminReceiver
    private val channelId = "sim_change_channel"

    @SuppressLint("SetTextI18n", "MissingInflatedId", "HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //cameraPermissionRequest.launch(Manifest.permission.CAMERA)
       // subscribeToSimChangeTopic()
        adminReceiver = MyDeviceAdminReceiver()
        adminReceiver.startListening(this)
        // Initialize SessionManager
        sessionManager = SessionManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        // Check if user is logged in
        val uid = sessionManager.getUserId()
        if (uid == null) {
            // If session is not valid, redirect to SignInActivity
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        }
        listenForSimChange(uid)
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
        val moreoptions: LinearLayout = findViewById(R.id.moreOptions)
        moreoptions.setOnClickListener {
            moreoptions.alpha = 0.7f  // Add a click effect
            moreoptions.postDelayed({ moreoptions.alpha = 1f }, 150)

            val intent = Intent(this, MoreOptionsActivity::class.java)
            startActivity(intent)
        }


        val viewDevicesCard: LinearLayout = findViewById(R.id.cardb)
        viewDevicesCard.setOnClickListener {
            val intent = Intent(this, ViewDevicesActivity::class.java)
            intent.putExtra("userId", uid) // Pass userId to the next activity
            startActivity(intent)
        }

        listenForDataWipe(androidId)
    }
    override fun onDestroy() {
        super.onDestroy()
        adminReceiver.stopListening()
    }

    private fun listenForSimChange(userId: String) {
        firestore.collection("device")
            .whereEqualTo("simChanged", true)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("SimChangeListener", "Listen failed: ${e.message}")
                    return@addSnapshotListener
                }

                for (doc in snapshots!!) {
                    val docUserId = doc.getString("userId") ?: continue
                    if (docUserId == userId) {
                        val changedDeviceName = doc.getString("deviceName") ?: "Unknown Device"
                        val changedDeviceModel = doc.getString("model") ?: "Unknown Model"

                        sendSimChangeNotification(changedDeviceName, changedDeviceModel)
                    }
                }
            }
    }

    private fun sendSimChangeNotification(deviceName: String, deviceModel: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "SIM Change Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SIM Change Alert")
            .setContentText("$deviceName ($deviceModel)'s SIM card changed or removed!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(3, notification)
    }

    @SuppressLint("SetTextI18n", "MissingInflatedId", "HardwareIds")
    private fun openAddDeviceDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_device, null)
        val etDeviceName: EditText = dialogView.findViewById(R.id.etDeviceName)
        val spDeviceType: Spinner = dialogView.findViewById(R.id.spDeviceType)
        val etModel: EditText = dialogView.findViewById(R.id.etModel)
        val etSerialNumber: EditText = dialogView.findViewById(R.id.etSerialNumber)
        val etContactNumber: EditText = dialogView.findViewById(R.id.etContactNumber)

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

                // Fetch FCM token and proceed
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val fcmToken = task.result
                            checkForDuplicateAndroidIdAndFcmToken(
                                androidId,
                                fcmToken,
                                deviceName,
                                deviceType,
                                model,
                                serialNumber,
                                contactNumber,
                                userId,
                                dialog
                            )
                        } else {
                            Toast.makeText(
                                this,
                                "Failed to fetch FCM token: ${task.exception?.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            }
        }

        dialog.show()
    }

    private fun listenForDataWipe(androidId: String) {
        firestore.collection("device")
            .whereEqualTo("androidId", androidId) // Find the document where androidId matches
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Log.e("DashboardActivity", "No device found with androidId: $androidId")
                    return@addOnSuccessListener
                }

                val documentId = result.documents[0].id // Get the actual document ID

                // Now listen for changes on that document
                dataWipeListenerRegistration = firestore.collection("device")
                    .document(documentId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.e("DashboardActivity", "Error listening for data wipe", e)
                            return@addSnapshotListener
                        }

                        if (snapshot != null && snapshot.exists()) {
                            val shouldWipe = snapshot.getBoolean("dataWipe") ?: false
                            if (shouldWipe) {
                                deleteFolder(androidId) // Automatically delete the folder
                            }
                        }
                    }
            }
            .addOnFailureListener { e ->
                Log.e("DashboardActivity", "Error fetching device document", e)
            }
    }

    private fun deleteFolder(androidId: String) {
        val folder = File(Environment.getExternalStorageDirectory(), "pic")
        if (folder.exists()) {
            val deleted = folder.deleteRecursively() // Deletes the folder and all contents
            if (deleted) {
                Log.d("DashboardActivity", "Folder deleted successfully")
            } else {
                Log.e("DashboardActivity", "Failed to delete folder")
            }
        } else {
            Log.w("DashboardActivity", "Folder not found")
        }

        // Reset the dataWipe field to false in Firestore
        resetDataWipeField(androidId)
    }

    private fun resetDataWipeField(androidId: String) {
        firestore.collection("device")
            .whereEqualTo("androidId", androidId) // Find the document where androidId matches
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Log.e("DashboardActivity", "No device found with androidId: $androidId")
                    return@addOnSuccessListener
                }

                val documentId = result.documents[0].id // Get the actual document ID

                // Now update the "dataWipe" field in the found document
                firestore.collection("device")
                    .document(documentId)
                    .update("dataWipe", false)
                    .addOnSuccessListener {
                        Log.d("DashboardActivity", "Data wipe field reset to false for document: $documentId")
                    }
                    .addOnFailureListener { e ->
                        Log.e("DashboardActivity", "Failed to reset data wipe field", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("DashboardActivity", "Error fetching device document", e)
            }
    }


    private fun checkForDuplicateAndroidIdAndFcmToken(
        androidId: String,
        fcmToken: String?,
        deviceName: String,
        deviceType: String,
        model: String,
        serialNumber: String,
        contactNumber: String,
        userId: String,
        dialog: AlertDialog
    ) {
        firestore.collection("device")
            .whereEqualTo("androidId", androidId)
            .get()
            .addOnSuccessListener { androidIdDocs ->
                if (!androidIdDocs.isEmpty) {
                    Toast.makeText(this, "This Device Already Added", Toast.LENGTH_SHORT).show()
                } else if (fcmToken != null) {
                    firestore.collection("device")
                        .whereEqualTo("fcmToken", fcmToken)
                        .get()
                        .addOnSuccessListener { fcmDocs ->
                            if (!fcmDocs.isEmpty) {
                                Toast.makeText(this, "Device with this FCM token already exists", Toast.LENGTH_SHORT).show()
                            } else {
                                val deviceData = hashMapOf<String, Any>(  // ✅ Change made here
                                    "deviceName" to deviceName,
                                    "deviceType" to deviceType,
                                    "model" to model,
                                    "serialNumber" to serialNumber,
                                    "contactNumber" to contactNumber,
                                    "userId" to userId,

                                    "androidId" to androidId
                                )

                                deviceData["fcmToken"] = fcmToken

                                addDeviceToFirestore(deviceData, serialNumber, dialog, fcmToken)
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                this,
                                "Error checking FCM token: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    val deviceData = hashMapOf<String, Any>(  // ✅ Change made here
                        "deviceName" to deviceName,
                        "deviceType" to deviceType,
                        "model" to model,
                        "serialNumber" to serialNumber,
                        "contactNumber" to contactNumber,
                        "userId" to userId,

                        "androidId" to androidId
                    )

                    addDeviceToFirestore(deviceData, serialNumber, dialog, null)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error checking Android ID: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addDeviceToFirestore(
        deviceData: HashMap<String, Any>,
        serialNumber: String,
        dialog: AlertDialog,
        fcmToken: String?
    ) {
        // Add the device to Firestore
        firestore.collection("device")
            .document() // Auto-generate a document ID
            .set(deviceData)
            .addOnSuccessListener {
                // After adding the device, capture the location
                saveDeviceLocation(serialNumber, fcmToken)
                dialog.dismiss()
                Toast.makeText(this, "Device added successfully", Toast.LENGTH_SHORT).show()
                scheduleSimCheck(this)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error adding device: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    /*fun subscribeToSimChangeTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic("sim_change_alert")
            .addOnCompleteListener { task ->
                var msg = "Subscribed to topic"
                if (!task.isSuccessful) {
                    msg = "Subscription failed"
                }
                Log.d("SimChangeWorker", msg)  // Log subscription status
            }
    }*/
    fun scheduleSimCheck(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<SimChangeWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)  // Only run when connected to the internet
                    .setRequiresBatteryNotLow(false)  // Avoid running when battery is low
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "SimCheckWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
    @SuppressLint("HardwareIds", "MissingPermission")
    private fun saveDeviceLocation(serialNumber: String, fcmToken: String?) {
        if (fcmToken == null) {
            Toast.makeText(this, "FCM Token is missing, cannot save location", Toast.LENGTH_SHORT).show()
            return
        }

        // Fetch the Android ID
        val androidId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )

        // Check for location permission
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // Request permission if not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val locationData = if (location == null) {
                hashMapOf(
                    "fcmToken" to fcmToken,
                    "androidId" to androidId,
                    "location" to null,
                    "lastUpdated" to System.currentTimeMillis()
                )
            } else {
                hashMapOf(
                    "fcmToken" to fcmToken,
                    "androidId" to androidId,
                    "location" to GeoPoint(location.latitude, location.longitude),
                    "lastUpdated" to System.currentTimeMillis()
                )
            }

            firestore.collection("device_locations")
                .document(androidId) // Use androidId as document ID
                .set(locationData)
                .addOnSuccessListener {
                    val message = if (location == null) {
                        "Device location (null) and FCM token saved successfully with Android ID"
                    } else {
                        "Device location and FCM token saved successfully with Android ID"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error saving location: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error retrieving location: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

}


