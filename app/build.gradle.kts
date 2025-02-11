import com.android.build.api.variant.FilterConfiguration.FilterType.ABI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.bitzo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bitzo"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.play.services.nearby)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Ktor
    implementation("io.ktor:ktor-client-core:2.1.2")
    implementation("io.ktor:ktor-client-okhttp:2.1.2")
    implementation("io.ktor:ktor-client-cio:2.1.2")
    implementation("io.ktor:ktor-client-logging:2.1.2")
    implementation("io.ktor:ktor-client-content-negotiation:2.1.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")

    implementation(libs.ktor.client.logging)
    implementation (libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.android)

    // Dependency Injection
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)

    // Typed Preferences DataStore (SharedPreeferences like APIs)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore)

    // Bitcoin
    implementation("org.bitcoindevkit:bdk-android:1.0.0-beta.7")

    // Lightning
    implementation(files("libs/LDK-release.aar"))
}

// Allow references to generated code
kapt {
    correctErrorTypes = true
}

val abiCodes = mapOf("x86_64" to 1, "arm64-v8a" to 2)
// val abiCodes = mapOf("armeabi-v7a" to 1, "x86_64" to 2, "arm64-v8a" to 3)

androidComponents {
    onVariants { variant ->

        // Assigns a different version code for each output APK
        // other than the universal APK.
        variant.outputs.forEach { output ->
            val name = output.filters.find { it.filterType == ABI }?.identifier

            // Stores the value of abiCodes that is associated with the ABI for this variant.
            val baseAbiCode = abiCodes[name]
            // Because abiCodes.get() returns null for ABIs that are not mapped by ext.abiCodes,
            // the following code does not override the version code for universal APKs.
            // However, because we want universal APKs to have the lowest version code,
            // this outcome is desirable.
            if (baseAbiCode != null) {
                // Assigns the new version code to output.versionCode, which changes the version code
                // for only the output APK, not for the variant itself.
                output.versionCode.set(baseAbiCode * 1000 + (output.versionCode.get() ?: 0))
            }
        }
    }
}