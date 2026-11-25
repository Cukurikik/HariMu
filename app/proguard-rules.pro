# Proguard rules for HariMu
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# --- Fase 14 hardening R8 ---
# Room: implementasi DAO/database dibuat saat kompilasi dan dipanggil lewat refleksi.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }

# kotlinx.serialization: serializer() dan Companion pada kelas @Serializable.
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.harimu.**$$serializer { *; }
-keepclassmembers class com.harimu.** { *** Companion; }
-keepclasseswithmembers class com.harimu.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# PDFBox-Android, Tesseract4Android, FFmpegKit, jsoup memakai refleksi/JNI internal.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.googlecode.tesseract.**
-dontwarn com.arthenica.ffmpegkit.**
-dontwarn org.jsoup.**
-dontwarn com.caverock.androidsvg.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# SLF4J 1.x optional binder probed by runtime dependencies (e.g. junrar)
-dontwarn org.slf4j.impl.StaticLoggerBinder

