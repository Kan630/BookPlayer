# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn com.github.luben.zstd.ZstdInputStream
-dontwarn org.brotli.dec.BrotliInputStream

# ============================================================================
# PHASE 1: Shrinking only - NO obfuscation
# ============================================================================
-dontobfuscate
-dontoptimize

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable
#-renamesourcefileattribute SourceFile # not a good idea, rename all class sourcefile !

# ---------- Common safe rules ----------
# Keep annotations and signatures needed by reflection and Gson
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses

# Keep anything referenced from the AndroidManifest (AGP usually generates this automatically,
# but keeping application class and components is harmless)
-keep class com.driot.bookplayer.MyApp { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.content.ContentProvider { *; }

# ---------- Gson: keep model fields or annotate models ----------
# Option A (recommended): annotate model classes with @SerializedName or @Keep
# Option B: keep model classes package
-keep class com.driot.bookplayer.model.** { *; }
# keep fields by name if you use default field names in JSON
-keepclassmembers class com.driot.bookplayer.model.** {
    <fields>;
}

# ---------- Room (Entities, DAOs, Database) ----------
# Room usually provides rules automatically but keeping entities and DAO interfaces is safe:
-keep class androidx.room.RoomDatabase { *; }
-keep class com.driot.bookplayer.db.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ---------- Glide ----------
# Keep generated Glide API classes and AppGlideModule
-keep public class * implements com.bumptech.glide.module.GlideModule

#-keep public class * extends com.bumptech.glide.AppGlideModule
#-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl { *; }
## If you use Glide v4 annotation processor generated classes:
#-keep class com.driot.bookplayer.GlideApp { *; }


# ---------- OkHttp/Retrofit/Retrofit converters ----------
# Usually OK; keep Retrofit interfaces (optional)
-keep interface com.driot.bookplayer.network.** { *; }

# ---------- ExoPlayer / Media3 ----------
# Keep classes used via reflection (media components)
-keep class com.google.android.exoplayer2.** { *; }
-keep class androidx.media3.** { *; }

# ---------- WorkManager (if workers are discovered by name or reflection) ----------
-keepclassmembers class * {
    @androidx.work.* <methods>;
}
-keep class androidx.work.Worker { *; }
-keep class androidx.work.impl.background.systemjob.SystemJobService { *; }

# ---------- JNI / native methods ----------
# Keep classes/methods called from native code:
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---------- Firebase (analytics / crashlytics) ----------
# Keep the Firebase components (Crashlytics normally adds rules automatically)
-keep class com.google.firebase.crashlytics.** { *; }

# ---------- General keep for classes that mustn't be obfuscated (adjust as needed) ----------
# If you use serialization frameworks or libraries that rely on class names, keep them:
-keepnames class com.driot.bookplayer.** { *; }

# ---------- Logging / debug classes (optional: strip logging in release) ----------
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ----- new stuff because jsoup (1.22) could use better regex now.  (security + speed)
# ------- other possibility would be to actually follow that move that by adding :  implementation "com.google.re2j:re2j:1.7"
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern

# ---------- End ----------
