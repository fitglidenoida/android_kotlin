plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    compileSdk = 35

    val versionMajor = 4
    val versionMinor = 0
    val versionPatch = 1
    val versionBuild = 0

    val computedVersionCode = versionMajor * 1000000 + versionMinor * 10000 + versionPatch * 100 + versionBuild
    val computedVersionName = "$versionMajor.$versionMinor.$versionPatch.$versionBuild"

    defaultConfig {
        namespace = "com.trailblazewellness.fitglide"
        minSdk = 26
        targetSdk = 35
        versionCode = computedVersionCode
        versionName = computedVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.6.10" // Fallback to stable version
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(17)
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.navigation)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.health.connect.client) {
        exclude("com.intellij", "annotations")
    }
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.retrofit) {
        exclude("com.intellij", "annotations")
    }
    implementation(libs.converter.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.foundation.layout.android)
    implementation(libs.google.play.services.auth)
    implementation(libs.material.components)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.places)
    implementation(libs.androidx.room.compiler)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.bom)
    implementation(libs.accompanist.swiperefresh)
    implementation(libs.annotations)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.androidx.datastore.core.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.ui.graphics.android)
    implementation(libs.androidx.ui.graphics.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    implementation(libs.hilt.android)
    implementation(libs.androidx.work.runtime.ktx.v290)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.browser)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains:annotations:23.0.0")
            exclude("com.intellij", "annotations")
        }
    }
}