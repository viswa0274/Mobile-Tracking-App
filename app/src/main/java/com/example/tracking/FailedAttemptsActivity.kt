package com.example.tracking

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class FailedAttemptsActivity : AppCompatActivity() {

    private lateinit var androidId: String
    private lateinit var failedAttemptsText: TextView
    private lateinit var lastAttemptedDateText: TextView
    private lateinit var lastAttemptedTimeText: TextView
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate the custom layout for AlertDialog
        val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_failed_attempts, null)

        // Initialize Views
        failedAttemptsText = dialogView.findViewById(R.id.failedAttemptsText)
        lastAttemptedDateText = dialogView.findViewById(R.id.lastAttemptedDateText)
        lastAttemptedTimeText = dialogView.findViewById(R.id.lastAttemptedTimeText)

        // Fetch androidId from intent
        androidId = intent.getStringExtra("androidId") ?: ""

        // Fetch failed attempts data from Firestore
        fetchFailedAttempts()

        // Create and Show AlertDialog
        AlertDialog.Builder(this)
            .setTitle("Failed Attempts Details")
            .setView(dialogView)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss() // Close dialog when OK is clicked
                finish()  // Close activity after dialog is dismissed
            }
            .setCancelable(false)  // Prevent closing the dialog by tapping outside
            .show()
    }

    private fun fetchFailedAttempts() {
        firestore.collection("device_failed_attempts")
            .whereEqualTo("androidId", androidId)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    for (document in result) {
                        val failedAttempts = document.getLong("failedAttempts") ?: 0
                        val lastAttempted = document.getString("lastAttempted") ?: "--"

                        failedAttemptsText.text = "Total Attempts: $failedAttempts"

                        // Format Last Attempted Date and Time
                        val formattedDateTime = formatDateTime(lastAttempted)
                        lastAttemptedDateText.text = formattedDateTime.first
                        lastAttemptedTimeText.text = formattedDateTime.second
                    }
                } else {
                    failedAttemptsText.text = "No failed attempts recorded."
                    lastAttemptedDateText.text = "--"
                    lastAttemptedTimeText.text = "--"
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Failed to fetch failed attempts", e)
            }
    }

    private fun formatDateTime(timestamp: String): Pair<String, String> {
        return try {
            // Convert Firestore timestamp string to Date object
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = sdf.parse(timestamp)

            if (date != null) {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

                val formattedDate = dateFormat.format(date)
                val formattedTime = timeFormat.format(date)

                Pair(formattedDate, formattedTime)
            } else {
                Pair("--", "--")
            }
        } catch (e: Exception) {
            Log.e("DateTimeError", "Error parsing date-time: $timestamp", e)
            Pair("--", "--")
        }
    }
}
