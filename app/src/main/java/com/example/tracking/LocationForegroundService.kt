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
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
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
    private var isNotified = false

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val androidId = intent?.getStringExtra("androidId")
        if (androidId != null) {
            if (!isGeofenceFetched) {
                fetchGeofenceData(androidId)
                isGeofenceFetched = true
            }
            //fetchDeviceDetails(androidId) // Fetch contact number from Firestore
        }
      startForegroundService()
        startLocationUpdates()
        // handler.post(phoneCheckRunnable)
        return START_STICKY
    }
    private fun saveGeofenceState(isInside: Boolean) {
        val prefs = getSharedPreferences("TrackingPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("insideGeofence", isInside).apply()
    }

    private fun wasInsideGeofence(): Boolean {
        val prefs = getSharedPreferences("TrackingPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("insideGeofence", true)
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
                }
            }
            .addOnFailureListener { e ->
                Log.e("LocationService", "Failed to fetch geofence: ${e.message}")
            }
    }

    private fun checkGeofence(location: Location) {
        if (geofenceLocation != null) {
            val geofenceLat = geofenceLocation!!.latitude
            val geofenceLng = geofenceLocation!!.longitude
            val result = FloatArray(1)

            Location.distanceBetween(location.latitude, location.longitude, geofenceLat, geofenceLng, result)

            val distance = result[0]
            Log.d("LocationService", "Distance from geofence: $distance meters")

            if (distance > geofenceRadius && !wasInsideGeofence()) {
                sendGeofenceExitNotification()
                saveGeofenceState(false)
            } else if (distance <= geofenceRadius && !wasInsideGeofence()) {
                sendGeofenceEntryNotification()

                saveGeofenceState(true)
            }
        }
    }

    private fun sendGeofenceEntryNotification() {
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Geofence Entry Alert")
            .setContentText("Your Device has re-entered the geofence area.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2, notification)
    }

    private fun sendGeofenceExitNotification() {
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Geofence Exit Alert")
            .setContentText("Your Device exited the geofence area.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }



    private fun startForegroundService() {
        // Create a NotificationChannel (only for Android 8.0 and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW // Low importance, no sound
            ).apply {
                enableLights(false) // Disable lights
                enableVibration(false) // Disable vibration
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Build the notification with no sound or vibration
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Location Tracking")
            .setContentText("Tracking your location in the background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setDefaults(0)  // Disable sound and vibration
            .setPriority(NotificationCompat.PRIORITY_LOW)  // Optional: Low priority for silent notifications
            .build()

        // Start the service as a foreground service
        startForeground(1, notification)
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Location Tracking", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Geofence and SIM change alerts"
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        //handler.removeCallbacks(phoneCheckRunnable)  // Stop periodic SIM check

        super.onDestroy()
    }
}
