# Proguard / R8 rules for Klipr Pocket & youtubedl-android
-keep class com.yausername.** { *; }
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <methods>;
}
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.io.**
-dontwarn java.nio.file.**
-dontwarn javax.naming.**
