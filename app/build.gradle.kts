plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    compileSdk = 35

    defaultConfig {
        namespace = "com.trailblazewellness.fitglide"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" // Compatible with Kotlin 2.0.21
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
        exclude("com.intellij", "annotations") // Kotlin DSL syntax
    }
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.retrofit) {
        exclude("com.intellij", "annotations") // Kotlin DSL syntax
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



    // Explicitly use latest annotations
    implementation(libs.annotations)
    implementation(libs.androidx.foundation.android)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.androidx.datastore.core.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    implementation (libs.hilt.android)
    implementation (libs.androidx.work.runtime.ktx.v290)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.browser)

    // Force resolution to 23.0.0 and exclude older versions
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains:annotations:23.0.0")
            exclude("com.intellij", "annotations") // Kotlin DSL syntax
        }
    }
}