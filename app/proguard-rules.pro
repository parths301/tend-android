# Tend release keeps.
#
# isMinifyEnabled is currently false (see app/build.gradle.kts). These rules are
# here so turning R8 on is a one-line change plus a device smoke test, not an
# archaeology exercise.

# --- Room ---------------------------------------------------------------
-keep class com.tend.app.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# --- Backup format ------------------------------------------------------
# The entity classes above are the on-disk schema; keep the backup layer whole
# so a shrunk build still reads files written by an unshrunk one.
-keep class com.tend.app.data.backup.** { *; }

# --- WorkManager --------------------------------------------------------
# Workers are instantiated by class name from the WorkManager database.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# --- Anthropic Java SDK (Jackson-backed models) -------------------------
-keep class com.anthropic.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keepnames class com.fasterxml.jackson.** { *; }
-keep class com.fasterxml.jackson.databind.** { *; }
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.slf4j.**

# --- OkHttp / Okio (SDK transport) --------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# --- Kotlin coroutines --------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# --- Compose ------------------------------------------------------------
-dontwarn androidx.compose.**
