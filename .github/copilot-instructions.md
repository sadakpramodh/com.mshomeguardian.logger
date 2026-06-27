# Project Memory — Home Guardian Logger

> Persistent context for AI assistants working in this repo. Update at large milestones.

## Overview

- **App name / package:** "Home Guardian" — `com.mshomeguardian.logger`
- **Type:** Android application (single `:app` module), Kotlin.
- **Purpose:** A background device-monitoring / logging agent. It collects a wide
  range of on-device data (calls, SMS, contacts, location, audio, sensors, usage,
  device/network state), stores it locally in Room, and syncs it to Firebase
  (Firestore + Storage) per authenticated user + device.
- **Auth model:** Firebase Email/Password. Data is keyed in Firestore under
  `users/{sanitizedEmail}/devices/{deviceId}/...`.

## Build & Tooling

- Gradle (AGP `7.4.2`), Kotlin `1.9.20`, `kotlin-kapt`, Google Services plugin.
- `compileSdk 34`, `minSdk 26`, `targetSdk 34`, `multiDexEnabled`.
- Room (`2.6.0`, schema in `app/schemas`), WorkManager (`2.8.1`), Coroutines,
  Lifecycle, Material, OkHttp.
- Firebase BOM `33.0.0`: Firestore, Storage, Auth.
- Vosk (`vosk-android:0.3.47`) + JNA for on-device speech (transcription path).
- Release build: minify + shrink + ABI/density APK splits. Signing via optional
  `keys.properties` at repo root.
- **No automated test suite of note** — only default `junit`/espresso deps. There
  are ad-hoc helper classes (e.g. `SyncTestHelper`, `FirebaseStructureTestHelper`).

## Architecture / Key Packages

`app/src/main/java/com/mshomeguardian/logger/`

- `data/` — Room `AppDatabase` (version 10) + entities & DAOs:
  `LocationEntity`, `CallLogEntity`, `MessageEntity`, `DeviceInfoEntity`,
  `AudioRecordingEntity`, `NetworkUsageEntity`.
- `workers/` — WorkManager `CoroutineWorker`s; scheduled by `WorkerScheduler`
  (adaptive intervals by time of day). Includes: Location, CallLog, Message,
  Contacts, DeviceInfo, Weather, InstalledApps, AppUsage, NetworkUsage,
  BatteryStatus, SystemMetrics, SensorData, DeviceAdmin, Transcription,
  ModelDownload, RecordingCleanup.
- `services/` — Foreground services & receivers: `UnifiedMonitoringService`,
  `MonitoringService`, `AudioRecordingService`, `ScreenContentService`,
  `CommunicationReceiver` (SMS_RECEIVED / PHONE_STATE), `BootReceiver`,
  `ShutdownReceiver`, `DeviceAdminReceiver` + `DeviceAdminService`.
- `ui/` — `MainActivity`, `SignInActivity`, `AdminSetupActivity`,
  `LiveTranscriptionActivity`, fragments (`TranscriptionHistoryFragment`,
  `LanguageModelsFragment`), debug views (`DebugConsoleView`, `SyncStatsView`).
- `transcription/` — `TranscriptionManager`, `AudioRecorder`, `TranscriptionResult`.
- `utils/` — `AuthManager`, `FirebaseServiceHelper`, `DataSyncManager`,
  `DeviceIdentifier` (persistent device id), `WorkerScheduler` helpers,
  `LocationUtils`, `WeatherUtil`, `UpdateManager`, plus a debug/monitoring pack
  (`ConsoleLogger`, `PerformanceMetricsMonitor`, `DebugFeaturesManager`,
  `TripleTapDetector`, `QuickDebugSetup`, `OptimizedLogger`, `CrashPreventionUtils`).
- `widget/HomeGuardianWidget` — home-screen widget with manual sync/update actions.

## Sync model

- Workers write to Room, then upload "not uploaded" rows to Firestore via
  `FirebaseServiceHelper`; rows are marked uploaded with a timestamp.
- Each record carries a persistent `deviceId` (`DeviceIdentifier`).
- Most workers no-op unless: permission granted, user signed in, Firebase available.

## Data currently collected (implemented)

Device info, call logs, SMS/MMS messages, contacts (names/phones/emails),
GPS location (lat/lng/timestamp only), audio recordings + transcription metadata,
app usage stats, installed apps, per-app network usage, battery status, sensors
(accelerometer, gyroscope, light, proximity, step counter, heart rate),
phone/network state, weather (from location), screen content (wallpaper +
screenshot via MediaProjection), plus sync metadata.

## Conventions / Gotchas

- Logging goes through `OptimizedLogger` / `ConsoleLogger`, not raw `Log` (mostly).
- Foreground service types matter (Android 14+): location / microphone /
  mediaProjection are declared in the manifest.
- Emails are sanitized for Firestore paths via
  `FirebaseServiceHelper.sanitizeEmailForFirestore`.
- DB uses `fallbackToDestructiveMigration` + an emergency DB fallback; migrations
  1→10 exist (call_logs schema was reworked several times).
- This is a privacy-sensitive surveillance-style app. Treat all collected data as
  sensitive; never add third-party exfiltration; keep changes within existing
  Firebase-per-user model.

## Root-level docs

`README.md`, `START_HERE.md`, `FEATURES_SUMMARY.md`, `DEBUG_FEATURES_README.md`,
`FILE_STRUCTURE.md`, `INTEGRATION_STEPS.kt` document the debug/monitoring feature
pack (debug console, sync stats, performance metrics, triple-tap). Some of that is
integration-guide material rather than fully wired into `MainActivity`.
