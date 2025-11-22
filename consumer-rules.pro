# Consumer ProGuard rules for Players Parser library

# Keep public API
-keep public class com.neomovies.playersparser.PlayersParserManager {
    public *;
}

# Keep parser classes
-keep class com.neomovies.playersparser.parsers.** { *; }

# Keep models
-keep class com.neomovies.playersparser.models.** { *; }

# Keep core infrastructure
-keep class com.neomovies.playersparser.core.** { *; }

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep JSON parsing
-keep class org.json.** { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }
