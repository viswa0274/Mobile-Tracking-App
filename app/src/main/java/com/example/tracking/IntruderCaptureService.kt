package com.example.tracking

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.Uri
import android.os.*
import android.Manifest
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream

class IntruderCaptureService : Service() {

    private lateinit var cameraManager: CameraManager
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification()) // Start as foreground service
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val androidId = intent?.getStringExtra("androidId") ?: return START_NOT_STICKY

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        // Ensure camera permission is granted before taking a photo
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            // Camera permission should be granted from the activity
            Log.e("IntruderCaptureService", "Camera permission not granted")
            stopSelf()
        } else {
            takePhoto(androidId)
        }

        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun takePhoto(androidId: String) {
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull {
                val characteristics = cameraManager.getCameraCharacteristics(it)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: cameraManager.cameraIdList[0] // Fall back to the first camera

            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            val streamConfigMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val imageSize = streamConfigMap?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width * it.height }

            val imageReader = ImageReader.newInstance(imageSize!!.width, imageSize.height, ImageFormat.JPEG, 1)

            val outputFile = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "intruder_photo_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(outputFile)

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                outputStream.write(bytes)
                image.close()
                outputStream.close()

                uploadPhotoToFirestore(outputFile, androidId)
            }, Handler(Looper.getMainLooper()))

            val cameraDeviceCallback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(imageReader.surface)
                    }

                    camera.createCaptureSession(listOf(imageReader.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.capture(captureRequest.build(), null, Handler(Looper.getMainLooper()))
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    }, Handler(Looper.getMainLooper()))
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }

            cameraManager.openCamera(cameraId, cameraDeviceCallback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Log.e("IntruderCaptureService", "Error capturing photo: ${e.message}")
        }
    }

    private fun uploadPhotoToFirestore(photoFile: File, androidId: String) {
        val storageRef = storage.reference.child("intruder_photos/$androidId/${photoFile.name}")
        val uri = Uri.fromFile(photoFile)

        storageRef.putFile(uri).addOnSuccessListener {
            Log.d("IntruderCapture", "Photo uploaded successfully")
            updateFirestore(androidId, photoFile.name)
        }.addOnFailureListener { e ->
            Log.e("IntruderCapture", "Error uploading photo: ${e.message}")
        }
    }

    private fun updateFirestore(androidId: String, fileName: String) {
        val attemptsRef = firestore.collection("device_failed_attempts").document(androidId)
        val timestamp = System.currentTimeMillis()

        attemptsRef.update(
            mapOf(
                "photoUrl" to fileName,
                "timestamp" to timestamp
            )
        ).addOnSuccessListener {
            Log.d("IntruderCapture", "Firestore updated")
        }.addOnFailureListener { e ->
            Log.e("IntruderCapture", "Error updating Firestore: ${e.message}")
        }
    }

    private fun createNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, "tracking_channel")
            .setContentTitle("Intruder Capture")
            .setContentText("Taking a photo of the intruder...")
            .setSmallIcon(R.drawable.location)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
