package com.example.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.Manifest
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.messaging.FirebaseMessaging

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val locationRequest = LocationRequest.create().apply {
        interval = 60000 // Update every 10 seconds
        fastestInterval = 50000 // Fastest update every 5 seconds
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }
    private var fcmToken: String? = null // To store the FCM token

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Start the service as a foreground service
        startForegroundService()

        // Fetch the FCM token
        fetchFCMToken()

        // Initialize the location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { location ->
                    saveLocationToFirestore(location)
                }
            }
        }

        // Request location updates
        requestLocationUpdates()
    }

    private fun fetchFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                fcmToken = task.result
            } else {
                fcmToken = null
            }
        }
    }


    private fun startForegroundService() {
        val channelId = "location_service_channel"
        val channelName = "Location Service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Location Tracking")
            .setContentText("Tracking location in the background")
            .setSmallIcon(R.drawable.location) // Use your app's icon
            .build()

        startForeground(1, notification)
    }

    private fun saveLocationToFirestore(location: android.location.Location) {
        val db = FirebaseFirestore.getInstance()
        val geoPoint = GeoPoint(location.latitude, location.longitude)

        if (fcmToken != null) {
            val locationData = mapOf(
                "fcmToken" to fcmToken,
                "location-geostamp" to geoPoint,
                "lastUpdated" to System.currentTimeMillis()
            )

            db.collection("device_locations")
                .document(fcmToken!!)
                .set(locationData)
                .addOnSuccessListener {
                    //Location saved successfully
                }
                .addOnFailureListener { e ->
                    // Log the error
                }
        }
    }
    // One definition should suffice
    private fun requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}