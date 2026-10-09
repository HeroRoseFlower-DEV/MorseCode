# Morse Translator ProGuard rules
# Minification is currently disabled; these rules apply when it is enabled.

-keepattributes Signature, InnerClasses, EnclosingMethod

# DataStore / preferences
-keep class androidx.datastore.** { *; }

# Keep JSON model keys used by SettingsRepository (org.json is reflection-free,
# but keep this as documentation of the on-disk format)
-keepclassmembers class com.morsetranslator.app.data.HistoryItem { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
