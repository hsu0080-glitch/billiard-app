# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Google Maps and Places API classes
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.android.libraries.places.** { *; }

# Keep data classes
-keep class com.billiard.nearby.BilliardHall { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
