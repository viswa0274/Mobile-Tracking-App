package com.example.tracking

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore

class SignInActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        // Initialize Firestore and SessionManager
        firestore = FirebaseFirestore.getInstance()
        sessionManager = SessionManager(this)

        val emailEditText: EditText = findViewById(R.id.etEmail)
        val passwordEditText: EditText = findViewById(R.id.etPassword)
        val loginButton: Button = findViewById(R.id.btnLogin)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            } else {
                verifyUser(email, password)
            }
        }
    }

    private fun verifyUser(email: String, enteredPassword: String) {
        firestore.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val userDocument = documents.documents[0]
                    val storedPassword = userDocument.getString("password")

                    if (enteredPassword == storedPassword) {
                        val userId = userDocument.id
                        sessionManager.setUserId(userId)

                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()

                        // Start location service after successful login
                        startLocationService()

                        // Navigate to DashboardActivity
                        val intent = Intent(this, DashboardActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "User not found in database", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to verify user: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startLocationService() {
        // Start the location update service
        val intent = Intent(this, LocationTrackActivity::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
