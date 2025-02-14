package com.example.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.messaging.FirebaseMessaging

class LocationWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    override fun doWork(): Result {
        val intent = Intent(applicationContext, LocationForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }
        return Result.success()
    }


    @SuppressLint("HardwareIds", "MissingPermission")
    private fun fetchLocationAndSendToFirestore() {
        // Retrieve the Android ID of the device
        val androidId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        // Get FCM token (Device ID)
        getFCMToken { fcmToken ->
            if (fcmToken == null) {
                Log.e("LocationWorker", "FCM Token is null. Cannot fetch location.")
                return@getFCMToken
            }

            // Create LocationRequest for periodic location updates
            val locationRequest = LocationRequest.create().apply {
                interval = 10000 // Update every 10 seconds
                fastestInterval = 5000 // Fastest update every 5 seconds
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }

            // Create a callback to receive location updates
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    locationResult.lastLocation?.let { location ->
                        val geoPoint = GeoPoint(location.latitude, location.longitude)
                        // Update Firestore with Android ID as the document ID
                        updateLocationInFirestore(androidId, fcmToken, geoPoint)
                    }
                }
            }

            // Request location updates
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun getFCMToken(callback: (String?) -> Unit) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                callback(task.result)
            } else {
                Log.e("LocationWorker", "Failed to get FCM token: ${task.exception?.message}")
                callback(null)
            }
        }
    }

    private fun updateLocationInFirestore(androidId: String, fcmToken: String, geoPoint: GeoPoint) {
        val db = FirebaseFirestore.getInstance()

        // Prepare location data
        val locationData = mapOf(
            "androidId" to androidId,
            "fcmToken" to fcmToken,
            "location" to geoPoint,
            "lastUpdated" to System.currentTimeMillis()
        )

        // Use Android ID as the document ID
        db.collection("device_locations")
            .document(androidId)
            .set(locationData)
            .addOnSuccessListener {
                Log.d("LocationWorker", "Location updated for Android ID: $androidId")
            }
            .addOnFailureListener { e ->
                Log.e("LocationWorker", "Error saving location: ${e.message}")
            }
    }
}
