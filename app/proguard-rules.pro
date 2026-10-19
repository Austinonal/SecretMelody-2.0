# Annotation-only libraries Guava (via Media3) refers to but doesn't need at runtime.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn afu.org.checkerframework.**

# yt-dlp wrapper: loads native libs and maps yt-dlp's JSON onto its classes with Jackson (reflection).
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
# The library unzips python/ffmpeg with commons-compress, which builds zip extra-field classes by reflection
# (Class.newInstance). Without this R8 strips their constructors and the first conversion crashes the app.
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn java.beans.**
