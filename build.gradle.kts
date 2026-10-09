// Top-level build file
//
// Target-SDK context (verified 2026-10-09):
// Google Play requires ordinary mobile app updates to target API 36
// (Android 16) since 2026-08-31. compileSdk/targetSdk are 36 below.
//
// AGP/Kotlin/Gradle are upgraded as one coherent set:
//   AGP 8.10.1 (first AGP line with official API 36 support)
//   Kotlin 2.0.20, Gradle 8.10.2 (via the checked-in wrapper)
// minSdk stays 26.
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}
