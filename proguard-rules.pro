# ProGuard rules for Players Parser library

# Keep PlayersParserManager public API
-keep public class com.neomovies.playersparser.PlayersParserManager {
    public *;
}

# Keep all parser classes
-keep class com.neomovies.playersparser.parsers.** { *; }
-keepclassmembers class com.neomovies.playersparser.parsers.** { *; }

# Keep all models
-keep class com.neomovies.playersparser.models.** { *; }
-keepclassmembers class com.neomovies.playersparser.models.** { *; }

# Keep core infrastructure
-keep class com.neomovies.playersparser.core.** { *; }
-keepclassmembers class com.neomovies.playersparser.core.** { *; }

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep JSON parsing
-keep class org.json.** { *; }
-dontwarn org.json.**

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Gson configuration
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
