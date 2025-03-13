package com.example.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log

import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioManager


import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import com.google.android.gms.location.*
import com.google.firebase.firestore.*
import com.google.firebase.firestore.GeoPoint

class LocationForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val channelId = "LocationTrackingChannel"
    private val firestore = FirebaseFirestore.getInstance()
    private var geofenceLocation: GeoPoint? = null
    private var geofenceRadius: Double = 0.0
    private var isGeofenceFetched = false
    private lateinit var sessionManager: SessionManager

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        //createNotificationChannel()
        sessionManager = SessionManager(this)
        listenForGeofenceChanges()  // Start listening for geofence violations
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val androidId = intent?.getStringExtra("androidId")
        if (androidId != null && !isGeofenceFetched) {
            fetchGeofenceData(androidId)
            isGeofenceFetched = true
        }

        startForegroundService()
        startLocationUpdates()
        return START_STICKY
    }

    private fun listenForGeofenceChanges() {
        firestore.collection("geofence")
            .whereEqualTo("insideGeofence", false)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("LocationService", "Error listening to geofence updates: ${e.message}")
                    return@addSnapshotListener
                }

                for (document in snapshots!!.documents) {
                    val androidId = document.getString("androidId") ?: continue
                    fetchDeviceUserId(androidId)
                }
            }
    }

    private fun fetchDeviceUserId(androidId: String) {
        firestore.collection("device")
            .whereEqualTo("androidId", androidId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val deviceDoc = documents.documents[0]
                    val userId = deviceDoc.getString("userId") ?: return@addOnSuccessListener
                    val deviceName = deviceDoc.getString("deviceName") ?: "Unknown Device"
                    val model = deviceDoc.getString("model") ?: "Unknown Model"

                     // Get logged-in user ID
                    val sessionUserId = sessionManager.getUserId()
                    if (userId == sessionUserId) {
                        sendGeofenceExitNotification(this, deviceName, model)

                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("LocationService", "Failed to fetch device userId: ${e.message}")
            }
    }

    private fun saveGeofenceState(androidId: String, isInside: Boolean) {
        val geofenceRef = firestore.collection("geofence").whereEqualTo("androidId", androidId)

        geofenceRef.get().addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                val documentId = documents.documents[0].id
                firestore.collection("geofence").document(documentId)
                    .set(mapOf("insideGeofence" to isInside), SetOptions.merge())  // Use `set` instead of `update`
                    .addOnSuccessListener {
                        Log.d("LocationService", "Geofence state updated: $isInside")
                    }
                    .addOnFailureListener { e ->
                        Log.e("LocationService", "Failed to update geofence state: ${e.message}")
                    }
            } else {
                Log.d("LocationService", "No geofence found for androidId: $androidId")
            }
        }.addOnFailureListener { e ->
            Log.e("LocationService", "Failed to query geofence: ${e.message}")
        }
    }



    private fun wasInsideGeofence(androidId: String, callback: (Boolean) -> Unit) {
        val geofenceRef = firestore.collection("geofence")
            .whereEqualTo("androidId", androidId)

        geofenceRef.get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val isInside = documents.documents[0].getBoolean("insideGeofence") ?: true
                    callback(isInside)
                } else {
                    Log.d("LocationService", "No geofence found for androidId: $androidId")
                    callback(true) // Default to true if no record exists
                }
            }
            .addOnFailureListener { e ->
                Log.e("LocationService", "Failed to fetch geofence state: ${e.message}")
                callback(true) // Default to true in case of an error
            }
    }


    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 60000
            fastestInterval = 50000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            @SuppressLint("HardwareIds")
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { location ->
                    // Update location in Firestore
                    updateLocationInFirestore(location)

                    // Fetch device details (e.g., contact number and SIM check) every time location is updated
                    val androidId = android.provider.Settings.Secure.getString(
                        contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    )
                    //fetchDeviceDetails(androidId) // Call inside location update to check SIM number and contact number

                    // Check geofence after updating location
                    checkGeofence(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }


    @SuppressLint("HardwareIds")
    private fun updateLocationInFirestore(location: Location) {
        val androidId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        val geoPoint = GeoPoint(location.latitude, location.longitude)

        val locationData = mapOf(
            "androidId" to androidId,
            "location" to geoPoint,
            "lastUpdated" to System.currentTimeMillis()
        )

        firestore.collection("device_locations")
            .document(androidId)
            .update(locationData)
            .addOnSuccessListener {
                Log.d("LocationService", "Location updated: $geoPoint")
            }
            .addOnFailureListener { e ->
                Log.e("LocationService", "Failed to update location: ${e.message}")
            }
    }





    private fun fetchGeofenceData(androidId: String) {
        firestore.collection("geofence")
            .whereEqualTo("androidId", androidId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.d("LocationService", "No geofence found for $androidId")
                } else {
                    val document = documents.first()
                    geofenceLocation = document.getGeoPoint("location")
                    geofenceRadius = document.getDouble("radius") ?: 0.0

                    Log.d("LocationService", "Geofence Location: $geofenceLocation, Radius: $geofenceRadius")
                }
            }
            .addOnFailureListener { e ->
                Log.e("LocationService", "Failed to fetch geofence: ${e.message}")
            }
    }


    @SuppressLint("HardwareIds")
    private fun checkGeofence(location: Location) {
        if (geofenceLocation != null) {
            val geofenceLat = geofenceLocation!!.latitude
            val geofenceLng = geofenceLocation!!.longitude
            val result = FloatArray(1)

            Location.distanceBetween(location.latitude, location.longitude, geofenceLat, geofenceLng, result)
            val distance = result[0]

            Log.d("LocationService", "Current Distance from Geofence: $distance meters, Geofence Radius: $geofenceRadius meters")

            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

            wasInsideGeofence(androidId) { wasInside ->
                if (distance > geofenceRadius && wasInside) {
                    Log.d("LocationService", "Device exited geofence")

                    saveGeofenceState(androidId, false)
                    fetchDeviceUserId(androidId)
                } else if (distance <= geofenceRadius && !wasInside) {
                    Log.d("LocationService", "Device entered geofence")
                    //sendGeofenceEntryNotification()
                    saveGeofenceState(androidId, true)
                }
            }
        } else {
            Log.d("LocationService", "Geofence location is null")
        }
    }


    /*private fun sendGeofenceEntryNotification() {
        Log.d("LocationService", "Sending geofence entry notification")

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val defaultSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        *//*val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Geofence Entry Alert")
            .setContentText("Your device has re-entered the geofence area.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)  // Allows user to dismiss it
            .setSound(defaultSoundUri)  // Set default notification sound
            .build()*//*
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Geofence Enter Alert")
            .setContentText("Your device entered the geofence area.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        notificationManager.notify(2, notification)  // Unique ID for entry notification
    }*/

    private fun sendGeofenceExitNotification(context: Context, deviceName: String, model: String) {
        Log.d("GeofenceAlert", "Sending geofence exit notification for $deviceName ($model)")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val defaultSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channelId = "geofence_exit_channel"

        // Create Notification Channel for Android 8+ (Oreo and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Geofence Exit Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                setSound(defaultSoundUri, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Build the notification
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Geofence Alert: $deviceName")
            .setContentText("$deviceName ($model) has exited the geofence area!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)  // Allows user to dismiss it
            .setSound(defaultSoundUri)  // Set default notification sound
            .build()

        notificationManager.notify(3, notification)  // Unique ID for geofence exit notification
    }








    private fun startForegroundService() {
        // Create a NotificationChannel (only for Android 8.0 and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW // Low importance, no sound
            ).apply {
                enableLights(false)
                enableVibration(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Create an Intent to stop the service when the notification is dismissed
        val stopIntent = Intent(this, LocationForegroundService::class.java)
        stopIntent.action = "STOP_SERVICE"
        val pendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        // Build the notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Location Tracking")
            .setContentText("Tracking your location in the background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)  // Allows dismissal
            .setOngoing(false)  // Ensures it's not a persistent notification
            .build()


        // Start the service as a foreground service
        startForeground(1, notification)
    }




    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        //handler.removeCallbacks(phoneCheckRunnable)  // Stop periodic SIM check

        super.onDestroy()
    }
}
