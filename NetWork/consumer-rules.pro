-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Retrofit creates implementations from annotated interfaces at runtime.
-keep,includedescriptorclasses interface com.tji.network.api.** { *; }
-keepclassmembers interface com.tji.network.api.** {
    @retrofit2.http.* <methods>;
}

# Retrofit resolves Kotlin suspend return types from Continuation<T> generic
# signatures at runtime. R8 full mode can otherwise rewrite the last parameter
# to a raw Continuation and Retrofit crashes with ClassCastException.
-keep,includedescriptorclasses class kotlin.coroutines.Continuation { *; }
-keep class retrofit2.** { *; }
-keep class retrofit2.converter.gson.** { *; }
-keep class retrofit2.http.** { *; }

# Gson maps API JSON by field names/annotations. Keep DTO fields stable in app release builds.
-keep class com.tji.network.data.** { *; }

# Release apps consume this library through R8. Keep HiveMQ/Netty intact because MQTT
# connect, subscribe, and message callbacks rely on Netty internals and service metadata.
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn com.google.protobuf.**
-dontwarn com.jcraft.jzlib.**
-dontwarn com.ning.compress.**
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn lzma.sdk.**
-dontwarn net.jpountz.**
-dontwarn org.jboss.marshalling.**
-dontwarn reactor.blockhound.**
-dontwarn sun.security.x509.**
-keep class com.hivemq.** { *; }
-keep class io.netty.** { *; }
-keep class org.jctools.** { *; }
-keep class reactor.blockhound.** { *; }
-keep class org.reactivestreams.** { *; }
-keepnames class io.netty.** { *; }
