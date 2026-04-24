# Preserve generic signatures and runtime annotations used by Retrofit and
# kotlinx.serialization at runtime. Without these, release builds can fail
# with ClassCastException when reflective type inspection expects a
# ParameterizedType but only sees a raw Class.
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Retrofit service interfaces and method annotations.
-keep class retrofit2.** { *; }
-keep class kotlin.coroutines.Continuation
-keep interface com.bbttvv.app.core.network.** { *; }

# Keep serializable models and generated serializers used by kotlinx.serialization.
-keep @kotlinx.serialization.Serializable class com.bbttvv.app.** { *; }
-keepclassmembers class com.bbttvv.app.** {
    *** Companion;
}
-keepclassmembers class com.bbttvv.app.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.bbttvv.app.**$$serializer { *; }

# Keep serialization runtime metadata.
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Keep Kotlin metadata used by serializers and reflective type resolution.
-keep class kotlin.Metadata { *; }
