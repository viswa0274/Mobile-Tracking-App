package com.example.tracking

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class MoreOptionsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var userIdEditText: EditText
    private lateinit var deviceNameEditText: EditText
    private lateinit var deviceModelEditText: EditText
    private lateinit var contactNumberEditText: EditText
    private lateinit var serialNumberEditText: EditText
    private lateinit var updateButton: Button

    @SuppressLint("HardwareIds", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_more_options)  // Ensure this matches your XML file

        // Initialize Firestore
       // db = FirebaseFirestore.getInstance()

        // Fetch Android ID dynamically



    }


}
