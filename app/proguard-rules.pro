# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# HiveMQ pulls Netty, whose optional BlockHound integration is discovered by service metadata.
# The app does not ship BlockHound at runtime, so R8 can safely ignore this optional hook.
-dontwarn reactor.blockhound.integration.BlockHoundIntegration

# HiveMQ includes optional Netty transports/features that are not used by the app's plain
# MQTT-over-TCP connection path: Linux epoll, websocket, proxy support, native OpenSSL,
# and alternative logging bridges.
-dontwarn io.netty.channel.epoll.**
-dontwarn io.netty.handler.codec.http.**
-dontwarn io.netty.handler.proxy.**
-dontwarn io.netty.internal.tcnative.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.eclipse.jetty.alpn.**
-dontwarn org.eclipse.jetty.npn.**
-dontwarn org.slf4j.**

# Release builds strip Android Log calls to reduce account, MQTT, and device
# diagnostics exposure in production APKs.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int println(int, java.lang.String, java.lang.String);
}
