package com.example.tracking

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.messaging.FirebaseMessaging
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class LocationTrackActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private val db = FirebaseFirestore.getInstance()

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("LocationTrackActivity", "onCreate called")
        Configuration.getInstance().load(applicationContext, getPreferences(MODE_PRIVATE))
        setContentView(R.layout.activity_locationtrack)

        // Initialize the MapView
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        // Start foreground service to update location
        startForegroundService()

        // Fetch androidId from Settings.Secure
        val secureAndroidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        // Get the androidId and fcmToken passed from the previous activity
        val intentAndroidId = intent.getStringExtra("androidId")
        val intentFcmToken = intent.getStringExtra("fcmToken")

        if (intentAndroidId.isNullOrEmpty()) {
            Toast.makeText(this, "Missing Android ID", Toast.LENGTH_SHORT).show()
            return
        }

        // Compare secureAndroidId with intentAndroidId
        if (secureAndroidId == intentAndroidId) {
            // If IDs match, update and display the current location
            saveAndDisplayCurrentLocation(intentAndroidId, intentFcmToken)
        } else {
            // If IDs do not match, fetch and display the stored location
            fetchAndDisplayLocation(intentAndroidId)
        }
    }


    private fun startForegroundService() {
        val intent = Intent(this, LocationForegroundService::class.java)
        val intentAndroidId = intent.getStringExtra("androidId")
        val intentFcmToken = intent.getStringExtra("fcmToken")
        // Pass androidId and fcmToken to the foreground service
        intentAndroidId?.let { intent.putExtra("androidId", it) }
        intentFcmToken?.let { intent.putExtra("fcmToken", it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun saveAndDisplayCurrentLocation(intentAndroidId: String, intentFcmToken: String?) {
        // Fetch the FCM Token using FirebaseMessaging if it's not available in the intent
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val fcmToken = intentFcmToken ?: token

            // Get the device's current location
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    val locationData = mapOf(
                        "androidId" to intentAndroidId,
                        "fcmToken" to fcmToken,
                        "location" to geoPoint,
                        "lastUpdated" to System.currentTimeMillis()
                    )

                    // Save the location data to Firestore
                    db.collection("device_locations")
                        .document(intentAndroidId)
                        .set(locationData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Displaying Location", Toast.LENGTH_SHORT).show()
                            updateMap(location.latitude, location.longitude)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error saving location: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    // If location is null, fetch the last stored location
                    fetchAndDisplayLocation(intentAndroidId, showErrorIfNull = true)
                }
            }.addOnFailureListener {
                // If fetching the location fails, fetch the last stored location
                fetchAndDisplayLocation(intentAndroidId, showErrorIfNull = true)
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error fetching FCM Token.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchAndDisplayLocation(intentAndroidId: String, showErrorIfNull: Boolean = false) {
        db.collection("device_locations")
            .document(intentAndroidId)
            .addSnapshotListener { documentSnapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Error fetching location updates: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    val savedLocation = documentSnapshot.getGeoPoint("location")
                    if (savedLocation != null) {
                        // Update the marker on the map
                        updateMap(savedLocation.latitude, savedLocation.longitude)
                        Toast.makeText(this, "Location updated on the map.", Toast.LENGTH_SHORT).show()
                    } else if (showErrorIfNull) {
                        Toast.makeText(this, "No location found for this device.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun updateMap(latitude: Double, longitude: Double) {
        val geoPoint = OsmGeoPoint(latitude, longitude)
        map.controller.setZoom(15.0)
        map.controller.setCenter(geoPoint)

        val marker = Marker(map)
        marker.position = geoPoint
        marker.title = "Device Location"
        map.overlays.clear() // Clear existing markers
        map.overlays.add(marker) // Add new marker
        map.invalidate() // Refresh the map
    }

    override fun onResume() {
        super.onResume()
        Log.d("LocationTrackActivity", "onResume called")
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        Log.d("LocationTrackActivity", "onPause called")
        map.onPause()
    }
}
