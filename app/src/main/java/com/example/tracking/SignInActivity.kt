package com.example.tracking

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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
        val tvSignUp: TextView = findViewById(R.id.tvSignUp)

        // Set "Sign up" clickable and blue
        setSignUpText(tvSignUp)

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

    private fun setSignUpText(tvSignUp: TextView) {
        val fullText = "Don't have an account? Sign up"
        val spannableString = SpannableString(fullText)

        // Find the "Sign up" part in the text
        val signUpStart = fullText.indexOf("Sign up")
        val signUpEnd = signUpStart + "Sign up".length

        // Change "Sign up" to blue
        spannableString.setSpan(
            ForegroundColorSpan(Color.BLUE),
            signUpStart, signUpEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Make "Sign up" clickable
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                startActivity(Intent(this@SignInActivity, SignUpActivity::class.java))
            }
        }
        spannableString.setSpan(
            clickableSpan,
            signUpStart, signUpEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Apply to TextView
        tvSignUp.text = spannableString
        tvSignUp.movementMethod = LinkMovementMethod.getInstance()
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

                        // Start location service after login
                        startLocationService()

                        // Navigate to DashboardActivity
                        val intent = Intent(this, DashboardActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationTrackActivity::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
