package com.example.tracking

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
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

        val signUpStart = fullText.indexOf("Sign up")
        val signUpEnd = signUpStart + "Sign up".length

        spannableString.setSpan(
            ForegroundColorSpan(Color.BLUE),
            signUpStart, signUpEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

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

                        // Show User Agreement Dialog
                        showUserAgreementDialog()

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
    private fun showUserAgreementDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("User Agreement")
        builder.setMessage(
            "By using this app, you agree to grant access to:\n\n" +
                    "✅ Location tracking\n" +
                    "✅ Data wipe & remote lock\n" +
                    "✅ Geofence alerts\n\n" +
                    "Do you agree to these terms?"
        )

        builder.setPositiveButton("Agree") { _, _ ->
            // Start location service
            startLocationService()

            // Check if device admin is activated
            if (!isDeviceAdminActive()) {
                promptDeviceAdminActivation()
            } else {
                navigateToDashboard()
            }
        }

        builder.setNegativeButton("Disagree") { _, _ ->
            // Log out and return to login screen
            //sessionManager.clearSession()
            val intent = Intent(this, SignInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        builder.setCancelable(false) // Prevent dismissing without a choice
        builder.show()
    }

    private fun startLocationService() {
        val intent = Intent(this, LocationTrackActivity::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    // ✅ Check if Device Admin is Active
    private fun isDeviceAdminActive(): Boolean {
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    // ✅ Prompt the User to Enable Device Admin
    private fun promptDeviceAdminActivation() {
        val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "This app requires admin permissions to enhance security features.")
        startActivityForResult(intent, REQUEST_CODE_DEVICE_ADMIN)
    }

    // ✅ Handle the Device Admin Activation Result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_DEVICE_ADMIN) {
            if (isDeviceAdminActive()) {
                Toast.makeText(this, "Device Admin Activated", Toast.LENGTH_SHORT).show()
                navigateToDashboard()
            } else {
                Toast.makeText(this, "Device Admin Activation Required", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ✅ Navigate to Dashboard
    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val REQUEST_CODE_DEVICE_ADMIN = 1001
    }
}
