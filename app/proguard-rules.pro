# Add this to your app/proguard-rules.pro file

# Keep JNA classes and prevent R8 from trying to optimize them
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }

# Don't warn about missing AWT classes (not available on Android)
-dontwarn java.awt.**
-dontwarn javax.swing.**

# Keep Vosk classes
-keep class org.vosk.** { *; }

# Keep Firebase classes (if using Firebase)
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Room database classes
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep WorkManager classes
-keep class androidx.work.** { *; }

# Keep OkHttp classes
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Ignore JNA's AWT-related code that's not available on Android
-dontwarn com.sun.jna.Native$AWT
-dontwarn com.sun.jna.platform.win32.**
-dontwarn com.sun.jna.platform.mac.**
-dontwarn com.sun.jna.platform.unix.**

# Keep application classes
-keep class com.mshomeguardian.logger.** { *; }