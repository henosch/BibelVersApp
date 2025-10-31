# ProGuard/R8 rules for BibelVers
# Keep application and activity classes referenced from the manifest
-keep class de.henosch.bibelvers.** { *; }

# Keep Material components to avoid reflective access stripping
-keep class com.google.android.material.** { *; }

# Keep AndroidX lifecycle classes used via reflection
-keep class androidx.lifecycle.** { *; }
