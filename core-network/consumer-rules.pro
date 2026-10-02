# core-network consumer R8 rules - merged into any app that depends on this module.
# kotlinx-serialization DTOs and Retrofit service interfaces must survive shrinking.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers @kotlinx.serialization.Serializable class com.ridevibe.core.network.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers,allowshrinking,allowobfuscation interface com.ridevibe.core.network.api.* {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response
