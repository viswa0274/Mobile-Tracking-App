package com.example.tracking

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

class MyDeviceAdminReceiver : DeviceAdminReceiver() {



    // Handle device admin enabled
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("DeviceAdminReceiver", "Device Admin Enabled")
    }

    // Handle device admin disabled
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("DeviceAdminReceiver", "Device Admin Disabled")
    }

    // Method to start IntruderCaptureService

    }
