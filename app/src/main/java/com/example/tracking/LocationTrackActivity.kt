package com.example.tracking
import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.messaging.FirebaseMessaging
import org.osmdroid.config.Configuration
import java.util.concurrent.TimeUnit
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class LocationTrackActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private var fcmToken: String? = null
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, getPreferences(MODE_PRIVATE))
        setContentView(R.layout.activity_locationtrack)

        // Initialize the MapView
        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        // Get the FCM Token passed from the previous activity
        fcmToken = intent.getStringExtra("fcmToken")
        if (fcmToken.isNullOrEmpty()) {
            Toast.makeText(this, "FCM Token not provided", Toast.LENGTH_SHORT).show()
            return
        }

        // Start LocationWorker to update the location periodically (background)
        startLocationWorker()

        // Fetch and compare the FCM token from Firebase
        fetchFCMTokenFromFirebase()
    }

    private fun fetchFCMTokenFromFirebase() {
        // Fetch the FCM token from Firebase
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            // Compare the token from Firebase with the token passed to this activity
            if (fcmToken == token) {
                // Tokens match, proceed with location operations
                checkAndDisplayLocation()
            } else {
                // Tokens don't match, prevent location save and show message
                Toast.makeText(this, "Token mismatch, unable to save location", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to fetch FCM token: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startLocationWorker() {
        // Start the LocationWorker to begin periodic location updates in the background
        val workRequest = OneTimeWorkRequestBuilder<LocationWorker>()
            .setInitialDelay(0, TimeUnit.SECONDS) // Optionally, set a delay before starting
            .build()
        WorkManager.getInstance(this).enqueue(workRequest)
    }

    private fun checkAndDisplayLocation() {
        fcmToken?.let { token ->
            // Fetch the device location from Firestore using the fcmToken
            db.collection("device_locations")
                .document(token)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val location = document.getGeoPoint("location-geostamp")
                        if (location != null) {
                            val latitude = location.latitude
                            val longitude = location.longitude
                            updateMap(latitude, longitude)
                        } else {
                            // Location not found, save and display the location
                            saveAndDisplayCurrentLocation(token)
                        }
                    } else {
                        // No location data found for the device, save and display the location
                        saveAndDisplayCurrentLocation(token)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error fetching location: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    @SuppressLint("MissingPermission")
    private fun saveAndDisplayCurrentLocation(token: String) {
        // Fetch the current device location
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val latitude = it.latitude
                val longitude = it.longitude

                // Save the location to Firestore only if the fcmToken matches
                val locationData = mapOf(
                    "fcmToken" to token,
                    "location-geostamp" to GeoPoint(latitude, longitude),
                    "lastUpdated" to System.currentTimeMillis()
                )

                db.collection("device_locations")
                    .document(token) // Use fcmToken as the document ID
                    .set(locationData)
                    .addOnSuccessListener {
                        // Location saved successfully, update the map
                        updateMap(latitude, longitude)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error saving location: ${e.message}", Toast.LENGTH_SHORT).show()
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
        map.overlays.clear()
        map.overlays.add(marker)
        map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}
