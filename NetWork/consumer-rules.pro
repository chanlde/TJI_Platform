-keepattributes Signature,*Annotation*

# Retrofit creates implementations from annotated interfaces at runtime.
-keep interface com.tji.network.api.** { *; }
-keepclassmembers interface com.tji.network.api.** {
    @retrofit2.http.* <methods>;
}

# Gson maps API JSON by field names/annotations. Keep DTO fields stable in app release builds.
-keep class com.tji.network.data.** { *; }
