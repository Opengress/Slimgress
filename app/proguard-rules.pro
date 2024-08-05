# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class net.opengress.slimgress.ActivityAuth$MyJavaScriptInterface {
#   public *;
#}
# there's a crash in ActivityInventoryItem ONLY appearing in minified builds
# TODO: See if I can refine these rules a bit - these rules are what makes it not crash
-keep class net.opengress.slimgress.** { *; }
-keepclassmembers class net.opengress.slimgress.** { *; }
-keep class com.google.common.geometry.** { *; }

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile