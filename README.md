# 🏠 Home Guardian Logger

**A comprehensive Android monitoring application for logging device activities, location tracking, and audio transcription with secure Firebase integration.**

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-orange.svg)](https://firebase.google.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)

## 📱 Overview

Home Guardian Logger is a powerful Android application designed to provide comprehensive monitoring and logging capabilities for security and tracking purposes. The app securely collects and synchronizes various device activities to Firebase, including location data, call logs, SMS messages, contacts, and real-time audio transcription.

## ✨ Key Features

### 🔐 **Authentication & Security**
- **Custom Firebase Authentication** integration with email/password
- **Automatic sign-in** with saved credentials
- **Secure data access** with Firebase security rules
- **User-specific data isolation** with email-based document structure

### 📍 **Location Monitoring**
- **Real-time location tracking** with high accuracy
- **Background location monitoring** with foreground service
- **Intelligent sync triggers** with distance thresholds
- **Weather integration** for location-based weather data
- **Adaptive sync intervals** based on time of day

### 📞 **Communication Logging**
- **Call log synchronization** with contact information
- **SMS/MMS message logging** with sender details
- **Real-time detection** of new calls and messages with automatic sync
- **Contact integration** with names and photos
- **Threshold-based sync** (syncs after 3 new calls or messages)

### 🎙️ **Audio Features**
- **Continuous audio recording** with automatic segmentation (30-minute intervals)
- **Real-time transcription** using Vosk offline speech recognition
- **Multi-language support** (Telugu, English, Hindi, French, Spanish, German)
- **Live transcription interface** for real-time speech-to-text
- **Automatic cleanup** of old recordings to manage storage

### 🔄 **Data Synchronization**
- **Automatic cloud sync** to Firebase Firestore with new structure
- **User-specific data organization**: `users/{email}/devices/{deviceId}/{collections}`
- **File storage** to Firebase Storage
- **Intelligent sync triggers** based on activity thresholds
- **Background workers** with adaptive scheduling for reliable data processing

### 🏠 **Home Screen Widget**
- **Real-time status display** with weather information
- **Quick sync button** for manual synchronization
- **Activity counters** showing recent data collection
- **Last sync timestamp** for monitoring

## 🛠️ Technical Architecture

### **Core Technologies**
- **Kotlin** - Primary development language
- **Android Architecture Components** - MVVM pattern
- **Room Database** - Local data persistence with optimized indices
- **WorkManager** - Background task scheduling with adaptive intervals
- **Coroutines** - Asynchronous operations
- **Firebase Suite** - Backend services with custom authentication

### **Key Components**

#### **Data Layer**
```
📦 Data Layer
├── 🗄️ Room Database (SQLite)
│   ├── LocationEntity
│   ├── CallLogEntity
│   ├── MessageEntity
│   ├── AudioRecordingEntity
│   └── DeviceInfoEntity
├── 🔥 Firebase Firestore (Cloud) - New Structure
│   └── users/{email}/devices/{deviceId}/{collections}
└── 📁 Firebase Storage (Files)
    └── users/{email}/devices/{deviceId}/audio/{filename}
```

#### **Service Architecture**
```
🔧 Background Services
├── 📍 UnifiedMonitoringService (Location + Coordination)
├── 🎙️ AudioRecordingService (30-min segments)
├── 🔄 WorkManager Jobs (Adaptive Scheduling)
│   ├── CallLogWorker (threshold: 3 new calls)
│   ├── MessageWorker (threshold: 3 new messages)
│   ├── ContactsWorker (incremental sync)
│   ├── WeatherWorker (location-based)
│   ├── DeviceInfoWorker (device metadata)
│   └── RecordingCleanupWorker (storage management)
└── 🔐 AuthStateHandler (Custom auth management)
```

#### **Authentication Flow**
```
🔐 Authentication System
├── Custom AuthManager (Email/password auth)
├── AuthStateHandler (Service lifecycle management)
├── SignInActivity (Custom UI, no FirebaseUI)
├── Credential saving (Auto sign-in)
└── DataSyncManager (Authentication-aware sync)
```

## 🚀 Setup & Installation

### **Prerequisites**
- Android Studio Arctic Fox or later
- Android SDK API 26+ (Android 8.0)
- Firebase project with Firestore and Storage enabled
- Google Services JSON configuration file

### **Firebase Setup**

1. **Create Firebase Project**
   ```bash
   # Visit Firebase Console
   https://console.firebase.google.com
   ```

2. **Enable Required Services**
   - Authentication (Email/Password)
   - Firestore Database
   - Firebase Storage

3. **Download Configuration**
   - Download `google-services.json`
   - Place in `app/` directory

4. **Configure Security Rules**
   ```javascript
   // Firestore Rules (Updated for new structure)
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       function getSanitizedEmail(email) {
         return email.replace('.', '_dot_').replace('@', '_at_')
                    .replace('/', '_').replace('[', '_')
                    .replace(']', '_').replace('*', '_').replace('?', '_');
       }
       
       function isOwner(userEmail) {
         return request.auth != null && 
                request.auth.token.email != null && 
                getSanitizedEmail(request.auth.token.email) == userEmail;
       }
       
       match /users/{userEmail} {
         allow read, write: if isOwner(userEmail);
         
         match /devices/{deviceId} {
           allow read, write: if isOwner(userEmail);
           
           match /{collection}/{document} {
             allow read, write: if isOwner(userEmail);
           }
         }
       }
     }
   }
   ```

   ```javascript
   // Storage Rules
   rules_version = '2';
   service firebase.storage {
     match /b/{bucket}/o {
       match /users/{userEmail}/devices/{deviceId}/{allPaths=**} {
         allow read, write: if request.auth != null && 
           request.auth.token.email.replace('.', '_dot_').replace('@', '_at_') == userEmail;
       }
     }
   }
   ```

### **Project Setup**

1. **Clone Repository**
   ```bash
   git clone https://github.com/yourusername/home-guardian-logger.git
   cd home-guardian-logger
   ```

2. **Add Configuration Files**
   ```
   📁 app/
   ├── google-services.json          # Firebase configuration
   └── src/main/assets/
       └── model-te/                 # Telugu Vosk model (optional)
           ├── am/
           ├── conf
           ├── graph
           └── mfcc.conf
   ```

3. **Build Project**
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ```

### **Language Model Setup (Optional)**

For offline transcription, download Vosk models:
```bash
# Telugu model example
wget https://alphacephei.com/vosk/models/vosk-model-small-tel-0.4.zip
unzip vosk-model-small-tel-0.4.zip
mv vosk-model-small-tel-0.4/* app/src/main/assets/model-te/
```

## 📋 Permissions Required

### **Runtime Permissions**
- `ACCESS_FINE_LOCATION` - Location tracking
- `ACCESS_BACKGROUND_LOCATION` - Background location (Android 10+)
- `READ_CALL_LOG` - Call log access
- `READ_SMS` - SMS message access
- `READ_CONTACTS` - Contact information
- `RECORD_AUDIO` - Audio recording and transcription
- `POST_NOTIFICATIONS` - Service notifications (Android 13+)

### **Foreground Service Permissions (Android 14+)**
- `FOREGROUND_SERVICE_LOCATION` - Location service
- `FOREGROUND_SERVICE_MICROPHONE` - Audio service

### **Manifest Permissions**
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

## 🔧 Configuration

### **Key Configuration Files**

#### **gradle.properties**
```properties
android.useAndroidX=true
android.enableJetifier=true
kotlin.incremental=true
org.gradle.jvmargs=-Xmx2048m
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
```

### **Customizable Settings**

#### **Data Collection Intervals (Adaptive)**
```kotlin
// WorkerScheduler.kt - Adaptive based on time of day
// Night mode (22:00-06:00): Reduced frequency
// Work hours (09:00-17:00): Normal frequency  
// Evening/morning: Balanced frequency
```

#### **Audio Recording Settings**
```kotlin
// AudioRecordingService.kt
private const val RECORDING_DURATION = 30 * 60 * 1000L // 30 minutes
private const val SAMPLING_RATE_IN_HZ = 16000 // 16kHz
private const val MAX_BUFFER_SIZE = 5 * 1024 * 1024  // 5MB
```

#### **Sync Thresholds**
```kotlin
// Automatic sync triggers
private const val CALL_COUNT_THRESHOLD = 3 // Sync after 3 new calls
private const val MESSAGE_COUNT_THRESHOLD = 3 // Sync after 3 new messages
```

## 📊 Data Structure

### **New Firestore Collections Structure**
```
📁 Firestore Database
└── users/{sanitized_email}/
    └── devices/{deviceId}/
        ├── 📍 locations/          # GPS coordinates
        ├── 📞 call_logs/         # Call history
        ├── 💬 messages/          # SMS/MMS data
        ├── 👥 contacts/          # Contact information
        ├── 🎙️ audio_recordings/  # Audio metadata
        ├── 🌤️ weather/           # Weather data
        └── 📱 device_info/       # Device metadata
```

### **Local Database Schema**
```sql
-- Optimized with performance indices
CREATE TABLE location_table (
    timestamp INTEGER PRIMARY KEY,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL
);

CREATE INDEX index_location_timestamp ON location_table(timestamp);

-- Audio recordings with transcription status
CREATE TABLE audio_recordings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recordingId TEXT UNIQUE NOT NULL,
    filePath TEXT NOT NULL,
    startTime INTEGER NOT NULL,
    endTime INTEGER NOT NULL,
    transcription TEXT,
    transcriptionStatus TEXT DEFAULT 'PENDING',
    uploadedToCloud INTEGER DEFAULT 0
);
```

## 🎨 User Interface

### **Main Activity Features**
- **Comprehensive permission management** with step-by-step guidance
- **Service status monitoring** with real-time updates
- **Manual sync triggers** for immediate data upload
- **Custom authentication management** with sign-in/sign-out
- **Recording controls** for audio capture
- **Data collection status** showing recent activity

### **Live Transcription**
- **Real-time speech recognition** with Vosk
- **Language switching** between supported languages
- **Text output display** with copy/save functionality
- **Audio visualization** with recording status

### **Custom Sign-In Activity**
- **Email/password authentication** without FirebaseUI dependency
- **Account creation** with password validation
- **Password reset** functionality
- **Automatic credential saving** for seamless sign-in

### **Home Screen Widget**
- **Compact status display** (4x2 grid) with weather
- **Weather information** with location-based data and icons
- **Activity counters** for recent data collection
- **Quick sync button** for manual updates

## 🔒 Security Features

### **Data Protection**
- **User-specific data isolation** with email-based document structure
- **Authentication-gated access** to all data with custom rules
- **Device-specific collections** with unique identifiers
- **Secure credential storage** with auto-login capability

### **Privacy Considerations**
- **Local processing** for transcription (offline Vosk)
- **Minimal data collection** - only necessary information
- **User-controlled recording** with manual start/stop
- **Transparent logging** with detailed status updates
- **Automatic cleanup** of old recordings

## 🧪 Testing

### **Unit Tests**
```bash
./gradlew test
```

### **Instrumentation Tests**
```bash
./gradlew connectedAndroidTest
```

### **Manual Testing Checklist**
- [ ] Custom authentication flow (sign-up, sign-in, sign-out)
- [ ] Permission requests with Android 14+ support
- [ ] Location tracking with adaptive intervals
- [ ] Audio recording with 30-minute segments
- [ ] Data synchronization to new Firebase structure
- [ ] Widget functionality with weather display
- [ ] Background service persistence
- [ ] Threshold-based sync triggers

## 📚 Dependencies

### **Core Libraries**
```gradle
// Android Architecture
implementation 'androidx.room:room-runtime:2.6.0'
implementation 'androidx.work:work-runtime-ktx:2.8.1'
implementation 'androidx.lifecycle:lifecycle-service:2.6.1'

// Firebase (Optimized)
implementation platform('com.google.firebase:firebase-bom:33.0.0')
implementation 'com.google.firebase:firebase-firestore'
implementation 'com.google.firebase:firebase-auth'
implementation 'com.google.firebase:firebase-storage'

// Speech Recognition
implementation 'com.alphacephei:vosk-android:0.3.47'
implementation 'net.java.dev.jna:jna:5.9.0@aar'

// Location Services
implementation 'com.google.android.gms:play-services-location:21.0.1'

// Networking
implementation 'com.squareup.okhttp3:okhttp:4.11.0'
```

## 🚨 Troubleshooting

### **Common Issues**

#### **Firestore Sync Issues**
```kotlin
// Check user authentication
if (!AuthManager.isSignedIn()) {
    // User needs to sign in
}

// Verify Firebase configuration
if (!FirebaseServiceHelper.isFirebaseAvailable()) {
    // Check google-services.json placement
}

// Check security rules
// Ensure rules match the new user-based structure
```

#### **Permission Denied Errors**
```kotlin
// For Android 14+, check foreground service permissions
FOREGROUND_SERVICE_LOCATION
FOREGROUND_SERVICE_MICROPHONE

// Grant all required permissions in Android settings
// Check target SDK compatibility
```

#### **Background Service Issues**
```kotlin
// Disable battery optimization
// Check Android background restrictions
// Verify foreground service implementation with proper types
```

#### **Build Errors**
```bash
# Clean and rebuild
./gradlew clean
./gradlew assembleDebug

# Clear Gradle cache
rm -rf ~/.gradle/caches/
```

## 📈 Performance Optimization

### **Battery Optimization**
- **Adaptive sync intervals** based on time of day (night: 1hr, work: 15min, evening: 20min)
- **Threshold-based sync triggers** to minimize unnecessary uploads
- **Intelligent background service management** with proper lifecycle handling
- **Efficient location tracking** with distance thresholds (1 meter)

### **Storage Management**
- **Automatic cleanup** of old recordings (30 days retention)
- **Storage limit enforcement** (1GB maximum for recordings)
- **Orphaned file cleanup** for better storage efficiency
- **Incremental sync** to reduce bandwidth usage

### **Memory Management**
- **Optimized audio buffering** (5MB max buffer, reduced from 10MB)
- **Efficient database queries** with proper indices
- **Resource cleanup** on service destruction
- **Wake lock management** for critical operations only

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### **Development Setup**
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

### **Code Style**
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add comprehensive documentation for public APIs
- Maintain consistent formatting with provided `.editorconfig`

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 📞 Support

For support and questions:
- **Issues**: [GitHub Issues](https://github.com/yourusername/home-guardian-logger/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/home-guardian-logger/discussions)
- **Email**: support@homeguardian.app

## 🔄 Changelog

### **Version 1.1.0 (Current)**
- **NEW**: Custom authentication system (no FirebaseUI dependency)
- **NEW**: User-specific data structure (`users/{email}/devices/{deviceId}`)
- **IMPROVED**: Adaptive sync intervals based on time of day
- **IMPROVED**: Threshold-based sync triggers (3 calls/messages)
- **IMPROVED**: Memory optimization (reduced buffer sizes)
- **IMPROVED**: Storage management with automatic cleanup
- **IMPROVED**: Android 14+ compatibility with proper permissions
- **FIXED**: Sync issues with proper Firebase structure
- **FIXED**: Background service reliability
- **FIXED**: Widget weather display with icons

### **Version 1.0.0**
- Initial release with core monitoring features
- Firebase integration and authentication
- Offline speech recognition with Vosk
- Background service implementation
- Home screen widget
- Multi-language transcription support

## 🔧 Current Sync Status

### **Working Features**
- ✅ Location tracking and sync
- ✅ Custom authentication system
- ✅ Audio recording with 30-minute segments
- ✅ Live transcription with Vosk
- ✅ Widget with weather display
- ✅ Adaptive background workers

### **Data Sync Status**
- ✅ **Location Data**: Real-time sync with new structure
- ⚠️ **Call Logs**: Threshold-based sync (after 3 new calls)
- ⚠️ **Messages**: Threshold-based sync (after 3 new messages)  
- ⚠️ **Contacts**: Incremental sync for new/changed contacts
- ✅ **Audio Recordings**: Upload to new storage structure
- ✅ **Device Info**: Sync on first run and changes
- ✅ **Weather Data**: Regular updates for widget

### **Known Issues Being Addressed**
- Call logs and messages require manual sync or reaching threshold
- Contact sync optimization for large contact lists
- Background location permission handling on newer Android versions

---

**⚠️ Disclaimer**: This application is designed for legitimate monitoring purposes. Users are responsible for complying with applicable laws and regulations regarding privacy and data collection in their jurisdiction.

---

Made with ❤️ for enhanced device monitoring and security.