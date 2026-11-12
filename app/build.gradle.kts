plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val mapsApiKey = providers.gradleProperty("MAPS_API_KEY")
    .orElse(providers.environmentVariable("MAPS_API_KEY"))
    .orElse("")

android {
    namespace = "com.baboons.vendedorescalle"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.baboons.vendedorescalle"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "BABOONS_API_BASE", "\"https://concesionarias.baboons.com.ar/api/\"")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey.get()
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.maps.android:maps-compose:7.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
