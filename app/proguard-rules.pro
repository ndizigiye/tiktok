# R8 rules for Loop release builds. Libraries ship most of their own consumer
# rules; the ones below cover what this app relies on through reflection.

# ---- kotlinx.serialization (DTOs in :shared, navigation routes, session JSON)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-dontnote kotlinx.serialization.SerializationKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Generated serializers and the companion/object accessors that runtime lookup
# (Retrofit converter, navigation's KClass.serializer()) goes through.
-keep,includedescriptorclasses class com.ndizigiye.loop.**$$serializer { *; }
-keepclassmembers class com.ndizigiye.loop.** {
    *** Companion;
}
-keepclasseswithmembers class com.ndizigiye.loop.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.ndizigiye.loop.**
-keepclassmembers class com.ndizigiye.loop.<1> {
    public static com.ndizigiye.loop.<1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Retrofit
# Retrofit reads generic parameters and annotations of service methods.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Suspend service methods carry their real return type in the Continuation's
# generic signature, so Continuation and declared return types must survive.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowobfuscation,allowshrinking class retrofit2.Response

-keep interface com.ndizigiye.loop.data.remote.LoopApi { *; }

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ---- OkHttp / Okio
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
