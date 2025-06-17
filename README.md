# 📱 Mobile Tracking App

A full-featured Android application built to provide advanced mobile security and remote tracking capabilities. Designed as an anti-theft solution, it enables real-time location monitoring, remote actions like locking and data wiping, geofencing alerts, and more.

## 🚀 Key Features

### 🔐 Authentication
- **Sign Up / Sign In**: User registration and login flow (`SignUpActivity.kt`, `SignInActivity.kt`)
- **Session Management**: SessionManager to handle user state across the app

### 📍 Real-time Location Tracking
- `LocationForegroundService.kt`, `LocationTrackActivity.kt`, `LocationWorker.kt` handle continuous location updates
- Uses Google Maps and Firebase Firestore for live tracking
- Background service ensures tracking even after reboot (`BootBroadcastReceiver.kt`)

### 🧭 Geofencing
- Configurable safe zone with alerting on exit (`GeofenceHelper.kt`)
- Sends notifications on geofence breach

### 🔒 Remote Device Control
- **Remote Lock**: Lock device remotely with password (`RemoteLockActivity.kt`)
- **Remote Alarm**: Trigger an alarm sound (`AlarmForegroundService.kt`)
- **Wipe Data**: Secure data wipe from remote device (`DataWipeActivity.kt`)
- **Device Admin Receiver**: Controls critical functions like locking (`MyDeviceAdminReceiver.kt`)

### 🧠 Intruder Detection
- Detects multiple failed unlock attempts (`FailedAttemptsActivity.kt`)

### 📊 Dashboard & More
- View detailed device information (`DeviceDetailsActivity.kt`)
- Quick access to all features (`DashboardActivity.kt`, `MoreOptionsActivity.kt`)


## 🛠 Tech Stack

- **Language**: Kotlin
- **Database**: Firebase Firestore
- **Storage**: Firebase Storage
- **Authentication**: Firebase Auth
- **Location**: Google Location Services
- **UI**: Material Design

---

## 📲 Setup Instructions

1. Clone the repository:
   ```bash
   git clone https://github.com/viswa0274/Mobile-Tracking-App.git
   cd Mobile-Tracking-App


