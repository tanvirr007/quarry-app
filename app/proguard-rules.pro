# ProGuard & R8 optimization rules for Quarry
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-dontwarn javax.annotation.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class app.quarry.tanvir.info.data.database.** { *; }
-dontwarn androidx.room.paging.**

# Jetpack WorkManager
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class app.quarry.tanvir.info.worker.** { *; }

# DataStore Preferences
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences {
    *;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Jetpack Compose & Material 3
-keepclassmembers class androidx.compose.ui.platform.** {
    public <init>(...);
}
-keepclassmembers class * implements androidx.compose.runtime.State {
    *;
}

# Biometrics
-dontwarn androidx.biometric.**

