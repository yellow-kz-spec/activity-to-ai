plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun requiredStravaCredential(name: String): String =
    (localProperties.getProperty(name) ?: providers.gradleProperty(name).orNull ?: System.getenv(name))
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: throw GradleException("$name is not configured. Set it in the ignored root local.properties file, a Gradle property, or an environment variable.")

fun buildConfigString(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

val stravaClientId = requiredStravaCredential("STRAVA_CLIENT_ID")
val stravaClientSecret = requiredStravaCredential("STRAVA_CLIENT_SECRET")

val stravaCallbackScheme = "activitytomd"
val stravaCallbackHost = "strava-auth.garminaiexporter.com"
val stravaCallbackPath = "/callback"
val stravaCallbackUri = "$stravaCallbackScheme://$stravaCallbackHost$stravaCallbackPath"

android {
    namespace = "com.garminaiexporter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.garminaiexporter"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.2.7"

        buildConfigField("String", "STRAVA_CLIENT_ID", "\"${buildConfigString(stravaClientId)}\"")
        buildConfigField("String", "STRAVA_CLIENT_SECRET", "\"${buildConfigString(stravaClientSecret)}\"")
        // One callback definition feeds both the OAuth request and manifest.
        buildConfigField("String", "STRAVA_REDIRECT_URI", "\"$stravaCallbackUri\"")
        manifestPlaceholders["stravaCallbackScheme"] = stravaCallbackScheme
        manifestPlaceholders["stravaCallbackHost"] = stravaCallbackHost
        manifestPlaceholders["stravaCallbackPath"] = stravaCallbackPath

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "Activity-to-MD-v${variant.versionName}-${variant.buildType.name}.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
