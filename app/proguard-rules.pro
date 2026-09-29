-keep class com.pti.worker.data.db.entities.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
