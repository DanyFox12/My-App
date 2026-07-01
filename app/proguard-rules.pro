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
