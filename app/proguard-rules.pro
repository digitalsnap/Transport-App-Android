# RideVibe release (R8) keep rules.
# The release build has isMinifyEnabled=true; without these rules R8 strips
# kotlinx-serialization serializers and Retrofit generic signatures, which
# crashes at runtime only in release builds.

# ── Crashlytics ──────────────────────────────────────────────────────────────
# Keep line numbers and remap source file names so uploaded mapping files
# produce readable beta stack traces instead of "SourceFile:0".
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── kotlinx-serialization ────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep generated serializers for our DTOs
-keepclassmembers @kotlinx.serialization.Serializable class com.ridevibe.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Retrofit / OkHttp ────────────────────────────────────────────────────────
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Retrofit reflects on generic parameters of suspend Continuation
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ── ZXing ────────────────────────────────────────────────────────────────────
-dontwarn com.google.zxing.**

# ── ML Kit (rules mostly ship with the AAR; silence transitive warnings) ─────
-dontwarn com.google.mlkit.**

# ── Facebook SDK ─────────────────────────────────────────────────────────────
-keep class com.facebook.** { *; }
-dontwarn com.facebook.**
