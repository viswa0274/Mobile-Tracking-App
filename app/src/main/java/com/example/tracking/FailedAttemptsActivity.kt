package com.example.tracking
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class FailedAttemptsActivity : AppCompatActivity() {

    private lateinit var androidId: String
    private lateinit var failedAttemptsText: TextView
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_failed_attempts)

        androidId = intent.getStringExtra("androidId") ?: ""
        failedAttemptsText = findViewById(R.id.failedAttemptsText)

        fetchFailedAttempts()
    }

    private fun fetchFailedAttempts() {
        firestore.collection("device_failed_attempts")
            .whereEqualTo("androidId", androidId)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    for (document in result) {
                        val failedAttempts = document.getLong("failedAttempts") ?: 0
                        failedAttemptsText.text = "Failed Attempts: $failedAttempts"
                    }
                } else {
                    failedAttemptsText.text = "No failed attempts recorded."
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Failed to fetch failed attempts", e)
            }
    }
}
