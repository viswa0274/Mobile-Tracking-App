package com.example.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.firebase.firestore.GeoPoint
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore

class GeofenceHelper(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    fun showGeofencePopup(activity: Activity, androidId: String, serialNumber: String) {
        val layoutInflater = activity.layoutInflater
        val view = layoutInflater.inflate(R.layout.dialog_set_geofence, null)
        val popupWindow = PopupWindow(view, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, true)

        val geofenceNameInput: TextInputEditText = view.findViewById(R.id.geofenceNameInput)
        val geofenceRadiusInput: TextInputEditText = view.findViewById(R.id.geofenceRadiusInput)
        val setGeofenceButton: MaterialButton = view.findViewById(R.id.setGeofenceButton)

        setGeofenceButton.setOnClickListener {
            val geofenceName = geofenceNameInput.text.toString().trim()
            val geofenceRadius = geofenceRadiusInput.text.toString().trim()

            if (geofenceName.isEmpty() || geofenceRadius.isEmpty()) {
                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if location is enabled and fetch the current location
            checkLocationEnabledAndFetch(activity, geofenceName, geofenceRadius.toFloat(), androidId, serialNumber, popupWindow)
        }

        popupWindow.showAtLocation(activity.findViewById(android.R.id.content), 0, 0, 0)
    }

    @SuppressLint("MissingPermission")
    private fun checkLocationEnabledAndFetch(
        activity: Activity,
        geofenceName: String,
        radius: Float,
        androidId: String,
        serialNumber: String,
        popupWindow: PopupWindow
    ) {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

        // Check if location permission is granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        // Request for location updates
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 2000
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                fusedLocationProviderClient.removeLocationUpdates(this)
                val location: Location = locationResult.lastLocation ?: return

                // Save geofence details to Firestore after receiving location
                saveGeofenceToFirestore(
                    geofenceName,
                    radius.toDouble(),
                    location.latitude,
                    location.longitude,
                    androidId,
                    serialNumber,
                    popupWindow
                )
            }
        }

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    @SuppressLint("HardwareIds")
    private fun saveGeofenceToFirestore(
        geofenceName: String,
        radius: Double,
        latitude: Double,
        longitude: Double,
        androidId: String,
        serialNumber: String,
        popupWindow: PopupWindow
    ) {
        val currentAndroidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        // Check if the dynamic Android ID matches the passed androidId
        if (currentAndroidId != androidId) {
            // Show message and prevent setting/updating the geofence for another device
            Toast.makeText(context, "You can only update the geofence for your device.", Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
            return
        }

        val geofenceRef = firestore.collection("geofence")

        // Check if the geofence already exists for the current device
        geofenceRef.whereEqualTo("androidId", androidId)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    // If geofence exists, update it
                    val documentId = result.documents[0].id
                    val storedAndroidId = result.documents[0].getString("androidId")

                    if (storedAndroidId == androidId) {
                        // Update geofence document in Firestore
                        geofenceRef.document(documentId)
                            .update(
                                mapOf(
                                    "geofenceName" to geofenceName,
                                    "radius" to radius,
                                    "location" to GeoPoint(latitude, longitude), // Using GeoPoint
                                    "serialNumber" to serialNumber
                                )
                            )
                            .addOnSuccessListener {
                                Toast.makeText(context, "Geofence updated successfully!", Toast.LENGTH_SHORT).show()
                                popupWindow.dismiss()

                                // Start LocationForegroundService to track location
                                startLocationService(androidId, latitude, longitude, radius)
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Failed to update geofence: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        // Prevent update for a different device
                        Toast.makeText(context, "You can only update the geofence for your device.", Toast.LENGTH_SHORT).show()
                        popupWindow.dismiss()
                    }
                } else {
                    // If no geofence exists, create a new one
                    val geofenceId = firestore.collection("geofence").document().id
                    val newGeofence = mapOf(
                        "geofenceId" to geofenceId,
                        "geofenceName" to geofenceName,
                        "radius" to radius,
                        "location" to GeoPoint(latitude, longitude), // Using GeoPoint
                        "androidId" to androidId,
                        "serialNumber" to serialNumber
                    )

                    geofenceRef.add(newGeofence)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Geofence set successfully!", Toast.LENGTH_SHORT).show()
                            popupWindow.dismiss()

                            // Start LocationForegroundService to track location
                            startLocationService(androidId, latitude, longitude, radius)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Failed to set geofence: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error checking geofence: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startLocationService(androidId: String, latitude: Double, longitude: Double, radius: Double) {
        val intent = Intent(context, LocationForegroundService::class.java).apply {
            putExtra("androidId", androidId)
            putExtra("latitude", latitude)
            putExtra("longitude", longitude)
            putExtra("radius", radius)
        }

        // Start the foreground service for location tracking
        ContextCompat.startForegroundService(context, intent)
    }
}
