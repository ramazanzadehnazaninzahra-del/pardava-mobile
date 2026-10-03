# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# kotlinx.serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ir.pardava.mobile.**$$serializer { *; }
-keepclassmembers class ir.pardava.mobile.** {
    *** Companion;
}

# Media3
-dontwarn androidx.media3.**
