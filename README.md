# 🏠 Home Guardian Logger

**A comprehensive Android monitoring application for logging device activities, location tracking, and audio transcription with secure Firebase integration.**

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-orange.svg)](https://firebase.google.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)

## 📱 Overview

Home Guardian Logger is a powerful Android application designed to provide comprehensive monitoring and logging capabilities for security and tracking purposes. The app securely collects and synchronizes various device activities to Firebase, including location data, call logs, SMS messages, contacts, and real-time audio transcription with offline speech recognition.

## ✨ Key Features

### 🔐 **Custom Authentication System**
- **Custom Firebase Authentication** with email/password (no FirebaseUI dependency)
- **Automatic credential saving** and sign-in for seamless user experience
- **Secure user isolation** with email-based Firebase document structure
- **Password reset functionality** with validation and error handling

### 📍 **Intelligent Location Monitoring**
- **Real-time location tracking** with high accuracy GPS
- **Adaptive sync intervals** based on time of day (15min-1hr)
- **Distance-based triggers** (1-meter threshold for meaningful updates)
- **Background location service** with proper Android 14+ foreground service types
- **Weather integration** for location-based weather data display

### 📞 **Smart Communication Logging**
- **Threshold-based synchronization** (syncs after 3 new calls/messages)
- **Real-time call/SMS detection** with automatic triggers
- **Contact integration** with names, phone numbers, and photos
- **Comprehensive metadata** including call duration, message body, timestamps
- **Incremental sync** to avoid duplicate entries

### 🎙️ **Advanced Audio Features**
- **30-minute audio recordings** with automatic file management
- **Offline speech recognition** using Vosk (Telugu, English, Hindi, French, Spanish, German)
- **Live transcription interface** for real-time speech-to-text
- **Intelligent storage management** with automatic cleanup of old recordings
- **Firebase Storage integration** for secure cloud backup

### 🔄 **Optimized Data Synchronization**
- **User-specific Firebase structure**: `users/{email}/devices/{deviceId}/{collections}`
- **Adaptive WorkManager scheduling** with time-based frequency adjustments
- **Battery-optimized background processing** with exponential backoff
- **Crash prevention utilities** for robust operation
- **Comprehensive error handling** and recovery mechanisms

### 🏠 **Enhanced Home Screen Widget**
- **Real-time weather display** with location-based data and weather icons
- **Activity counters** showing recent data collection statistics
- **Manual sync trigger** for immediate data synchronization
- **Compact 4x2 layout** with comprehensive status information

### 🛡️ **Robust Architecture & Security**
- **MVVM Architecture** with Kotlin Coroutines and Room Database
- **Crash prevention system** with safe operation utilities
- **Memory optimization** with reduced buffer sizes and efficient cleanup
- **Android 14+ compatibility** with proper foreground service permissions
- **Secure Firestore rules** ensuring user data isolation

## 🛠️ Technical Architecture

### **Core Technologies**
- **Kotlin** - Primary development language with Coroutines
- **Android Architecture Components** - MVVM pattern with Lifecycle-aware components
- **Room Database** - Local data persistence with optimized indices
- **WorkManager** - Intelligent background task scheduling with adaptive intervals
- **Firebase Suite** - Custom authentication, Firestore, and Storage
- **Vosk** - Offline speech recognition for multiple languages

### **Enhanced Data Layer**
```
📦 Optimized Data Layer
├── 🗄️ Room Database (SQLite with indices)
│   ├── LocationEntity (with timestamp indexing)
│   ├── CallLogEntity (with phone number and timestamp indices)
│   ├── MessageEntity (with comprehensive SMS/MMS data)
│   ├── AudioRecordingEntity (with transcription status tracking)
│   ├── DeviceInfoEntity (comprehensive device metadata)
│   └── Migration support (v2 → v3 with performance optimizations)
├── 🔥 Firebase Firestore (User-based structure)
│   └── users/{sanitized_email}/devices/{deviceId}/{collections}
│       ├── locations/ (GPS coordinates with weather data)
│       ├── call_logs/ (Call history with contact info)
│       ├── messages/ (SMS/MMS with contact resolution)
│       ├── contacts/ (Contact information with phone/email)
│       ├── audio_recordings/ (Audio metadata with transcription)
│       ├── weather/ (Weather data for widget)
│       └── device_info/ (Device metadata and registration)
└── 📁 Firebase Storage (User-based file storage)
    └── users/{sanitized_email}/devices/{deviceId}/audio/{filename}
```

### **Intelligent Service Architecture**
```
🔧 Background Services (Android 14+ Compatible)
├── 📍 LocationMonitoringService (FOREGROUND_SERVICE_LOCATION)
│   ├── Distance-based triggering (1m threshold)
│   ├── High-accuracy GPS with network fallback
│   ├── Adaptive update intervals (5min-1hr)
│   └── Weather data integration
├── 🎙️ AudioRecordingService (FOREGROUND_SERVICE_MICROPHONE)
│   ├── 30-minute recording segments
│   ├── Automatic file management and cleanup
│   ├── Memory-optimized audio buffering (5MB max)
│   └── Crash prevention with retry logic
├── 🔄 Adaptive WorkManager Jobs
│   ├── CallLogWorker (threshold: 3 new calls)
│   ├── MessageWorker (threshold: 3 new messages)
│   ├── ContactsWorker (incremental sync, 50 contacts/batch)
│   ├── WeatherWorker (location-based weather updates)
│   ├── DeviceInfoWorker (device metadata sync)
│   ├── RecordingCleanupWorker (storage management)
│   └── TranscriptionWorker (audio processing and upload)
└── 🔐 Enhanced Authentication System
    ├── Custom AuthManager (no FirebaseUI dependency)
    ├── AuthStateHandler (service lifecycle management)
    ├── Credential management (secure storage and auto-signin)
    └── DataSyncManager (authentication-aware operations)
```

### **Adaptive Scheduling System**
```
⏰ Time-Based Adaptive Intervals
├── 🌙 Night Mode (22:00-06:00): Reduced frequency
│   ├── Location: 60 minutes
│   ├── Communication: 60 minutes
│   ├── Contacts: 6 hours
│   └── Weather: 2 hours
├── 🏢 Work Hours (09:00-17:00): Normal frequency
│   ├── Location: 30 minutes
│   ├── Communication: 15 minutes
│   ├── Contacts: 2 hours
│   └── Weather: 1 hour
└── 🌅 Evening/Morning: Balanced frequency
    ├── Location: 20 minutes
    ├── Communication: 20 minutes
    ├── Contacts: 3 hours
    └── Weather: 1.5 hours
```

## 🚀 Setup & Installation

### **Prerequisites**
- Android Studio Arctic Fox or later
- Android SDK API 26+ (Android 8.0)
- Firebase project with Authentication, Firestore, and Storage
- Google Services JSON configuration file

### **Enhanced Firebase Setup**

1. **Create Firebase Project**
   ```bash
   # Visit Firebase Console
   https://console.firebase.google.com
   ```

2. **Enable Required Services**
   - Authentication (Email/Password provider)
   - Firestore Database (Native mode)
   - Firebase Storage

3. **Download Configuration**
   ```bash
   # Download google-services.json from Firebase Console
   # Place in app/ directory (already in .gitignore)
   ```

4. **Configure Enhanced Security Rules**
   
   **Firestore Rules** (supports new user-based structure):
   ```javascript
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       
       function isAuthenticated() {
         return request.auth != null && request.auth.uid != null && request.auth.token.email != null;
       }
       
       function getSanitizedEmail(email) {
         return email.replace('.', '_dot_').replace('@', '_at_')
                     .replace('/', '_').replace('[', '_')
                     .replace(']', '_').replace('*', '_').replace('?', '_');
       }
       
       function isEmailOwner(sanitizedEmail) {
         return isAuthenticated() && 
                getSanitizedEmail(request.auth.token.email) == sanitizedEmail;
       }
       
       match /users/{sanitizedEmail} {
         allow read, write: if isEmailOwner(sanitizedEmail);
         
         match /devices/{deviceId} {
           allow read, write: if isEmailOwner(sanitizedEmail);
           
           match /{collection}/{document} {
             allow read, write: if isEmailOwner(sanitizedEmail);
           }
         }
       }
       
       match /{document=**} {
         allow read, write: if false;
       }
     }
   }
   ```

   **Storage Rules**:
   ```javascript
   rules_version = '2';
   service firebase.storage {
     match /b/{bucket}/o {
       match /users/{sanitizedEmail}/devices/{deviceId}/{allPaths=**} {
         allow read, write: if request.auth != null && 
           request.auth.token.email.replace('.', '_dot_').replace('@', '_at_') == sanitizedEmail;
       }
     }
   }
   ```

### **Project Setup**

1. **Clone Repository**
   ```bash
   git clone <repository-url>
   cd home-guardian-logger
   ```

2. **Add Configuration Files**
   ```
   📁 app/
   ├── google-services.json          # Firebase configuration (required)
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

### **Vosk Language Models (Optional)**

For offline transcription, download Vosk models:
```bash
# Example: Telugu model
wget https://alphacephei.com/vosk/models/vosk-model-small-tel-0.4.zip
unzip vosk-model-small-tel-0.4.zip
mv vosk-model-small-tel-0.4/* app/src/main/assets/model-te/

# Supported languages: Telugu (te), English (en), Hindi (hi), 
# French (fr), Spanish (es), German (de)
```

## 📋 Comprehensive Permissions

### **Runtime Permissions (Android 6.0+)**
- `ACCESS_FINE_LOCATION` - High-accuracy location tracking
- `ACCESS_COARSE_LOCATION` - Network-based location
- `ACCESS_BACKGROUND_LOCATION` - Background location (Android 10+)
- `READ_CALL_LOG` - Call log access and monitoring
- `READ_SMS` - SMS message reading
- `READ_PHONE_STATE` - Phone state and carrier information
- `RECEIVE_SMS` - Real-time SMS reception
- `READ_CONTACTS` - Contact information with phone numbers
- `RECORD_AUDIO` - Audio recording and transcription
- `POST_NOTIFICATIONS` - Service notifications (Android 13+)

### **Foreground Service Permissions (Android 14+)**
- `FOREGROUND_SERVICE_LOCATION` - Location monitoring service
- `FOREGROUND_SERVICE_MICROPHONE` - Audio recording service

### **Manifest Permissions**
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

## 🔧 Advanced Configuration

### **Customizable Settings**

#### **Adaptive Sync Intervals**
```kotlin
// WorkerScheduler.kt - Time-based adaptive scheduling
// Night mode (22:00-06:00): 1-hour intervals
// Work hours (09:00-17:00): 15-30 minute intervals  
// Evening/morning: 20-minute intervals
```

#### **Audio Recording Configuration**
```kotlin
// AudioRecordingService.kt - Optimized settings
private const val RECORDING_DURATION = 30 * 60 * 1000L // 30 minutes
private const val SAMPLING_RATE_IN_HZ = 16000 // 16kHz for Vosk
private const val MAX_BUFFER_SIZE = 5 * 1024 * 1024  // 5MB (optimized)
private const val MAX_RETRY_COUNT = 5 // Reduced retry attempts
```

#### **Sync Thresholds**
```kotlin
// Threshold-based automatic sync triggers
private const val CALL_COUNT_THRESHOLD = 3 // Sync after 3 new calls
private const val MESSAGE_COUNT_THRESHOLD = 3 // Sync after 3 new messages
private const val CONTACTS_SYNC_LIMIT = 50 // Process 50 contacts per batch
```

#### **Storage Management**
```kotlin
// RecordingCleanupWorker.kt - Automatic cleanup
private const val MAX_RECORDING_AGE_DAYS = 30L  // Keep recordings for 30 days
private const val MAX_STORAGE_USAGE_BYTES = 1024L * 1024L * 1024L  // 1GB maximum
```

## 📊 Enhanced Data Structure

### **Firestore Collections Structure** (New User-Based)
```
📁 Firestore Database
└── users/{sanitized_email}/
    └── devices/{deviceId}/
        ├── 📍 locations/          # GPS coordinates with weather
        ├── 📞 call_logs/         # Call history with contact info
        ├── 💬 messages/          # SMS/MMS with contact resolution
        ├── 👥 contacts/          # Contact info with phone/email
        ├── 🎙️ audio_recordings/  # Audio metadata with transcription
        ├── 🌤️ weather/           # Weather data for widget
        └── 📱 device_info/       # Device registration and metadata
```

### **Optimized Local Database Schema**
```sql
-- Location table with performance index
CREATE TABLE location_table (
    timestamp INTEGER PRIMARY KEY,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL
);
CREATE INDEX index_location_timestamp ON location_table(timestamp);

-- Audio recordings with transcription tracking
CREATE TABLE audio_recordings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recordingId TEXT UNIQUE NOT NULL,
    filePath TEXT NOT NULL,
    fileName TEXT NOT NULL,
    startTime INTEGER NOT NULL,
    endTime INTEGER NOT NULL,
    duration INTEGER NOT NULL,
    fileSize INTEGER NOT NULL,
    transcriptionStatus TEXT DEFAULT 'PENDING',
    transcription TEXT,
    transcriptionLanguage TEXT DEFAULT 'te-IN',
    uploadedToCloud INTEGER DEFAULT 0,
    deviceId TEXT NOT NULL
);

-- Call logs with comprehensive metadata
CREATE INDEX index_call_logs_timestamp_uploaded ON call_logs(timestamp, uploadedToCloud);
CREATE INDEX index_messages_timestamp_uploaded ON message_logs(timestamp, uploadedToCloud);
```

## 🎨 Enhanced User Interface

### **MainActivity Features**
- **Custom authentication management** with email/password validation
- **Comprehensive permission handling** for Android 14+ compatibility
- **Real-time service status monitoring** with detailed activity counters
- **Manual sync triggers** with progress feedback
- **Battery optimization guidance** for reliable background operation

### **Live Transcription Activity**
- **Multi-language support** with offline Vosk models
- **Real-time speech recognition** with status indicators
- **Language switching** between supported languages
- **Text output management** with copy/save functionality

### **Custom Sign-In Activity**
- **Email/password authentication** without FirebaseUI dependency
- **Account creation** with comprehensive validation
- **Password reset** with error handling
- **Automatic credential saving** for seamless experience

### **Enhanced Home Screen Widget**
- **Weather display** with location-based data and weather icons
- **Activity counters** for recent data collection (last 24 hours)
- **Manual sync button** for immediate synchronization
- **Compact 4x2 grid layout** with comprehensive status information

## 🔒 Advanced Security Features

### **Data Protection**
- **User-specific data isolation** with email-based Firebase structure
- **Authentication-gated access** with custom security rules
- **Device-specific collections** with unique persistent identifiers
- **Secure credential storage** with automatic sign-in capability

### **Privacy Considerations**
- **Offline processing** for transcription (no cloud dependencies)
- **Minimal data collection** - only necessary information
- **User-controlled recording** with manual start/stop controls
- **Transparent status reporting** with detailed activity logs
- **Automatic cleanup** of old recordings with configurable retention

### **Crash Prevention System**
- **Comprehensive error handling** with safe operation utilities
- **Service recovery mechanisms** with exponential backoff
- **Memory management** with optimized buffer sizes
- **Permission validation** before critical operations

## 🧪 Testing & Validation

### **Automated Testing**
```bash
# Unit tests
./gradlew test

# Instrumentation tests
./gradlew connectedAndroidTest
```

### **Manual Testing Checklist**
- [ ] Custom authentication flow (sign-up, sign-in, sign-out, password reset)
- [ ] Permission handling with Android 14+ foreground service permissions
- [ ] Location tracking with adaptive intervals and distance thresholds
- [ ] Audio recording with 30-minute segments and automatic cleanup
- [ ] Data synchronization with threshold-based triggers
- [ ] Widget functionality with weather display and activity counters
- [ ] Background service persistence and crash recovery
- [ ] Firebase structure validation with user-based collections

### **Sync Testing Tools**
```kotlin
// Built-in testing utilities
SyncTestHelper.runSyncTest(context) // Comprehensive sync validation
SyncTestHelper.testFirebaseUpload(context) // Firebase connectivity test
DataSyncManager.testSync(context, "calls") // Test specific sync types
FirebaseStructureTestHelper.testExactStructure(context) // Structure validation
```

## 📚 Dependencies

### **Core Libraries** (Optimized)
```gradle
// Android Architecture (Latest stable versions)
implementation 'androidx.room:room-runtime:2.6.0'
implementation 'androidx.work:work-runtime-ktx:2.8.1'
implementation 'androidx.lifecycle:lifecycle-service:2.6.1'

// Firebase (Optimized BOM approach)
implementation platform('com.google.firebase:firebase-bom:33.0.0')
implementation 'com.google.firebase:firebase-firestore'
implementation 'com.google.firebase:firebase-auth'
implementation 'com.google.firebase:firebase-storage'

// Offline Speech Recognition
implementation 'com.alphacephei:vosk-android:0.3.47'
implementation 'net.java.dev.jna:jna:5.9.0@aar'

// Location Services
implementation 'com.google.android.gms:play-services-location:21.0.1'

// Networking (Weather API)
implementation 'com.squareup.okhttp3:okhttp:4.11.0'
```

## 🚨 Troubleshooting Guide

### **Common Issues**

#### **Authentication Issues**
```kotlin
// Check custom authentication status
AuthManager.isSignedIn() // Verify sign-in state
AuthManager.getCurrentUser()?.email // Get current user email

// Clear saved credentials if needed
AuthManager.clearSavedCredentials(context)
```

#### **Firebase Sync Issues**
```kotlin
// Verify Firebase availability
FirebaseServiceHelper.isFirebaseAvailable()

// Check user-based structure
val sanitizedEmail = FirebaseServiceHelper.sanitizeEmailForFirestore(userEmail)
// Expected path: users/{sanitized_email}/devices/{deviceId}/{collection}
```

#### **Permission Issues (Android 14+)**
```kotlin
// Verify foreground service permissions
FOREGROUND_SERVICE_LOCATION // Required for location service
FOREGROUND_SERVICE_MICROPHONE // Required for audio service

// Check permission status
CrashPreventionUtils.PermissionStatus.generateReport(context)
```

#### **Background Service Issues**
```kotlin
// Verify battery optimization settings
// Settings > Apps > Home Guardian > Battery > Unrestricted

// Check adaptive sync intervals
WorkerScheduler.getAdaptiveIntervals() // Time-based frequency
```

### **Debug Tools**
```kotlin
// Comprehensive sync testing
SyncTestHelper.runSyncTest(context)
SyncTestHelper.getBasicSyncInfo(context)

// Performance monitoring
OptimizedLogger.d(TAG, "Performance metrics") // Debug logging
DataSyncManager.getSyncStatistics(context) // Sync statistics
```

## 📈 Performance Optimizations

### **Battery Optimization**
- **Adaptive sync intervals** with time-based frequency adjustment
- **Threshold-based triggers** to minimize unnecessary network calls
- **Battery-aware scheduling** with WorkManager constraints
- **Optimized location tracking** with distance-based updates
- **Intelligent background service management** with proper lifecycle handling

### **Memory Management**
- **Reduced audio buffer sizes** (5MB maximum, down from 10MB)
- **Efficient database queries** with performance indices
- **Automatic resource cleanup** with proper lifecycle management
- **Crash prevention utilities** for robust operation

### **Storage Management**
- **Automatic cleanup** with configurable retention (30 days)
- **Storage limit enforcement** (1GB maximum for recordings)
- **Incremental sync** to reduce bandwidth usage
- **Orphaned file cleanup** for efficient storage usage

## 🤝 Contributing

We welcome contributions! Please follow these guidelines:

### **Development Setup**
1. Fork the repository
2. Create a feature branch with descriptive name
3. Follow Kotlin coding conventions
4. Add comprehensive tests for new functionality
5. Update documentation for API changes
6. Submit pull request with detailed description

### **Code Style Guidelines**
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add KDoc documentation for public APIs
- Maintain consistent formatting with provided `.editorconfig`
- Use OptimizedLogger for all logging operations

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 📞 Support & Contact

For support and questions:
- **Issues**: [GitHub Issues](https://github.com/yourusername/home-guardian-logger/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/home-guardian-logger/discussions)
- **Email**: support@homeguardian.app

## 🔄 Changelog

### **Version 2.0.0 (Current)**
- **NEW**: Custom authentication system (removed FirebaseUI dependency)
- **NEW**: User-specific Firebase structure (`users/{email}/devices/{deviceId}`)
- **NEW**: Crash prevention utilities with comprehensive error handling
- **NEW**: Enhanced weather widget with location-based data and icons
- **IMPROVED**: Adaptive sync intervals based on time of day (15min-1hr)
- **IMPROVED**: Threshold-based sync triggers (3 calls/messages) for efficiency
- **IMPROVED**: Memory optimization with reduced buffer sizes and cleanup
- **IMPROVED**: Android 14+ compatibility with proper foreground service permissions
- **IMPROVED**: Storage management with automatic cleanup and size limits
- **IMPROVED**: Comprehensive permission handling with step-by-step guidance
- **FIXED**: Background service reliability with proper lifecycle management
- **FIXED**: Firebase structure consistency with user isolation
- **OPTIMIZED**: Database queries with performance indices
- **OPTIMIZED**: WorkManager scheduling with battery-aware constraints

### **Version 1.1.0**
- Custom authentication system implementation
- User-specific data structure migration
- Adaptive sync intervals
- Memory optimization improvements

### **Version 1.0.0**
- Initial release with core monitoring features
- Firebase integration and authentication
- Offline speech recognition with Vosk
- Background service implementation

## 🔧 Current System Status

### **Fully Operational Features**
- ✅ **Custom Authentication**: Email/password with credential saving
- ✅ **Location Tracking**: Real-time GPS with adaptive intervals
- ✅ **Audio Recording**: 30-minute segments with automatic cleanup
- ✅ **Live Transcription**: Offline Vosk with multi-language support
- ✅ **Weather Widget**: Location-based data with weather icons
- ✅ **Background Services**: Android 14+ compatible with proper permissions
- ✅ **Crash Prevention**: Comprehensive error handling and recovery

### **Data Synchronization Status**
- ✅ **Location Data**: Real-time sync with weather integration
- ✅ **Device Information**: Automatic registration and metadata sync
- ✅ **Audio Recordings**: Upload with transcription metadata
- ⚡ **Call Logs**: Threshold-based sync (3 new calls trigger)
- ⚡ **Messages**: Threshold-based sync (3 new messages trigger)
- ⚡ **Contacts**: Incremental sync (50 contacts per batch)
- ✅ **Weather Data**: Regular updates for widget display

### **Performance Metrics**
- 🔋 **Battery Usage**: Optimized with adaptive scheduling
- 💾 **Storage**: Auto-cleanup with 1GB limit and 30-day retention
- 📡 **Network**: Threshold-based sync reduces unnecessary calls
- 🏃 **Performance**: Memory-optimized with 5MB audio buffers

---

**⚠️ Important Notice**: This application is designed for legitimate monitoring purposes. Users are responsible for complying with applicable laws and regulations regarding privacy and data collection in their jurisdiction. Ensure proper consent and legal compliance before deployment.

---

**Made with ❤️ for comprehensive device monitoring and enhanced security.**