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
- **Firebase Authentication** integration with email/password
- **Automatic sign-in** with saved credentials
- **Secure data access** with Firebase security rules
- **App Check** integration for additional security

### 📍 **Location Monitoring**
- **Real-time location tracking** with high accuracy
- **Background location monitoring** with foreground service
- **Geofencing capabilities** with distance thresholds
- **Weather integration** for location-based weather data

### 📞 **Communication Logging**
- **Call log synchronization** with contact information
- **SMS/MMS message logging** with sender details
- **Real-time detection** of new calls and messages
- **Contact integration** with names and photos

### 🎙️ **Audio Features**
- **Continuous audio recording** with automatic segmentation
- **Real-time transcription** using Vosk offline speech recognition
- **Multi-language support** (Telugu, English, Hindi, French, Spanish, German)
- **Live transcription interface** for real-time speech-to-text

### 🔄 **Data Synchronization**
- **Automatic cloud sync** to Firebase Firestore
- **File storage** to Firebase Storage
- **Intelligent sync triggers** based on activity thresholds
- **Background workers** for reliable data processing

### 🏠 **Home Screen Widget**
- **Real-time status display** with weather information
- **Quick sync button** for manual synchronization
- **Activity counters** showing recent data collection
- **Last sync timestamp** for monitoring

## 🛠️ Technical Architecture

### **Core Technologies**
- **Kotlin** - Primary development language
- **Android Architecture Components** - MVVM pattern
- **Room Database** - Local data persistence
- **WorkManager** - Background task scheduling
- **Coroutines** - Asynchronous operations
- **Firebase Suite** - Backend services

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
├── 🔥 Firebase Firestore (Cloud)
└── 📁 Firebase Storage (Files)
```

#### **Service Architecture**
```
🔧 Background Services
├── 📍 LocationMonitoringService
├── 🎙️ AudioRecordingService
├── 🔄 WorkManager Jobs
│   ├── CallLogWorker
│   ├── MessageWorker
│   ├── ContactsWorker
│   ├── WeatherWorker
│   └── DeviceInfoWorker
└── 🔐 AuthStateHandler
```

#### **Authentication Flow**
```
🔐 Authentication System
├── AuthManager (Core auth logic)
├── AuthStateHandler (State monitoring)
├── AuthenticationDialog (UI)
└── DataSyncManager (Service control)
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
   - Firebase App Check

3. **Download Configuration**
   - Download `google-services.json`
   - Place in `app/` directory

4. **Configure Security Rules**
   ```javascript
   // Firestore Rules
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /devices/{deviceId}/{document=**} {
         allow read, write: if request.auth != null;
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
```

#### **Firestore Security Rules**
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /devices/{deviceId}/{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

### **Customizable Settings**

#### **Data Collection Intervals**
```kotlin
// WorkerScheduler.kt
private const val LOCATION_UPDATE_INTERVAL = 15 // minutes
private const val SYNC_CHECK_INTERVAL = 15 // minutes
private const val WEATHER_UPDATE_INTERVAL = 30 // minutes
```

#### **Audio Recording Settings**
```kotlin
// AudioRecordingService.kt
private const val RECORDING_DURATION = 30 * 60 * 1000L // 30 minutes
private const val SAMPLING_RATE_IN_HZ = 16000 // 16kHz
```

## 📊 Data Structure

### **Firestore Collections**
```
📁 Firestore Database
└── devices/{deviceId}/
    ├── 📍 locations/          # GPS coordinates
    ├── 📞 call_logs/         # Call history
    ├── 💬 messages/          # SMS/MMS data
    ├── 👥 contacts/          # Contact information
    ├── 🎙️ audio_recordings/  # Audio metadata
    ├── 🌤️ weather/           # Weather data
    └── 📱 phone_state/       # Device status
```

### **Local Database Schema**
```sql
-- Location tracking
CREATE TABLE location_table (
    timestamp INTEGER PRIMARY KEY,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL
);

-- Audio recordings
CREATE TABLE audio_recordings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recordingId TEXT UNIQUE NOT NULL,
    filePath TEXT NOT NULL,
    startTime INTEGER NOT NULL,
    endTime INTEGER NOT NULL,
    transcription TEXT,
    uploadedToCloud INTEGER DEFAULT 0
);
```

## 🎨 User Interface

### **Main Activity Features**
- **Permission management** with step-by-step guidance
- **Service status monitoring** with real-time updates
- **Manual sync triggers** for immediate data upload
- **Authentication management** with sign-in/sign-out
- **Recording controls** for audio capture

### **Live Transcription**
- **Real-time speech recognition** with Vosk
- **Language switching** between supported languages
- **Text output display** with copy/save functionality
- **Audio visualization** with recording status

### **Home Screen Widget**
- **Compact status display** (4x2 grid)
- **Weather information** with location-based data
- **Activity counters** for recent data collection
- **Quick sync button** for manual updates

## 🔒 Security Features

### **Data Protection**
- **End-to-end encryption** via Firebase security
- **Authentication-gated access** to all data
- **Device-specific isolation** with unique identifiers
- **Secure credential storage** with auto-login

### **Privacy Considerations**
- **Local processing** for transcription (offline Vosk)
- **Minimal data collection** - only necessary information
- **User-controlled recording** with manual start/stop
- **Transparent logging** with detailed status updates

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
- [ ] Authentication flow (sign-up, sign-in, sign-out)
- [ ] Permission requests (location, microphone, contacts)
- [ ] Location tracking accuracy
- [ ] Audio recording and transcription
- [ ] Data synchronization to Firebase
- [ ] Widget functionality and updates
- [ ] Background service persistence

## 📚 Dependencies

### **Core Libraries**
```gradle
// Android Architecture
implementation 'androidx.room:room-runtime:2.6.0'
implementation 'androidx.work:work-runtime-ktx:2.8.1'
implementation 'androidx.lifecycle:lifecycle-service:2.6.1'

// Firebase
implementation platform('com.google.firebase:firebase-bom:32.7.0')
implementation 'com.google.firebase:firebase-firestore'
implementation 'com.google.firebase:firebase-auth'
implementation 'com.google.firebase:firebase-storage'

// Speech Recognition
implementation 'com.alphacephei:vosk-android:0.3.47'
implementation 'net.java.dev.jna:jna:5.9.0@aar'

// Location Services
implementation 'com.google.android.gms:play-services-location:21.0.1'
```

## 🚨 Troubleshooting

### **Common Issues**

#### **Firebase Authentication Errors**
```kotlin
// Check google-services.json placement
// Verify Firebase project configuration
// Ensure Auth is enabled in console
```

#### **Permission Denied Errors**
```kotlin
// Grant all required permissions in Android settings
// Check target SDK compatibility
// Verify manifest declarations
```

#### **Background Service Issues**
```kotlin
// Disable battery optimization
// Check Android background restrictions
// Verify foreground service implementation
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
- **Efficient location tracking** with distance thresholds
- **Smart sync triggers** to minimize unnecessary uploads
- **Background service optimization** with proper lifecycle management
- **Wake lock management** for critical operations only

### **Storage Management**
- **Automatic cleanup** of old recordings
- **Compression** for uploaded audio files
- **Local storage monitoring** with size limits
- **Incremental sync** to reduce bandwidth usage

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

### **Version 1.0.0**
- Initial release with core monitoring features
- Firebase integration and authentication
- Offline speech recognition with Vosk
- Background service implementation
- Home screen widget
- Multi-language transcription support

---

**⚠️ Disclaimer**: This application is designed for legitimate monitoring purposes. Users are responsible for complying with applicable laws and regulations regarding privacy and data collection in their jurisdiction.

---

Made with ❤️ for enhanced device monitoring and security.