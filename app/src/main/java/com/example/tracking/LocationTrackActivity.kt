package com.example.tracking
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class LocationTrackActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private val db = FirebaseFirestore.getInstance()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, getPreferences(MODE_PRIVATE))
        setContentView(R.layout.activity_locationtrack)

        // Initialize the MapView
        map =  findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK) // Use OpenStreetMap tiles
        map.setMultiTouchControls(true) // Enable multi-touch gestures

        // Get the device IMEI passed from the previous activity
        val imei = intent.getStringExtra("deviceImei")

        if (!imei.isNullOrEmpty()) {
            // Fetch the latest location for the given IMEI
            fetchLatestLocation(imei)
        }
    }

    private fun fetchLatestLocation(imei: String) {
        db.collection("locations")
            .document(imei)
            .collection("location_updates")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Handle no location data
                    return@addOnSuccessListener
                }

                val location = documents.first()
                val geoPoint = location.getGeoPoint("location")
                val latitude = geoPoint?.latitude ?: 0.0
                val longitude = geoPoint?.longitude ?: 0.0

                // Update the map with the location
                updateMap(latitude, longitude)
            }
            .addOnFailureListener {
                // Handle error when fetching location
            }
    }

    private fun updateMap(latitude: Double, longitude: Double) {
        val osmGeoPoint = OsmGeoPoint(latitude, longitude)

        // Add a marker at the device's location
        val marker = Marker(map)
        marker.position = osmGeoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Device Location"
        map.overlays.add(marker)

        // Move and zoom the map to the location
        map.controller.setCenter(osmGeoPoint)
        map.controller.setZoom(15.0) // Adjust zoom level as needed
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
