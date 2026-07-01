# Keep kotlinx.serialization generated serializers for our type-safe nav routes.
# Without these, R8 in release could strip the serializers and crash navigation.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep the @Serializable companion serializers used by Navigation routes.
-keepclassmembers class com.devexplorer.** {
    *** Companion;
}
-keepclasseswithmembers class com.devexplorer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.devexplorer.**$$serializer { *; }

# @Serializable enums: keep their synthetic $$serializedName values map so
# enum (de)serialization survives R8 optimization.
-keepclassmembers class com.devexplorer.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# WorkManager instantiates workers by class name via reflection (the default
# WorkerFactory). Without this, R8 could rename/strip CacheCleanupWorker and the
# scheduled cleanup would crash at runtime in a release build. Keep every
# ListenableWorker subclass and its two-arg WorkManager constructor.
-keep public class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Room generates its DAO/database implementations at compile time and references
# entity fields directly, but keep annotated members defensively so R8's full
# mode never strips a column accessor the generated code relies on.
-keepclassmembers class com.devexplorer.** {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
