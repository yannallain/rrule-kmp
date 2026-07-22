import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

val rruleVersion = providers.gradleProperty("rruleVersion").orElse("0.1.0")

android {
    namespace = "io.github.yallain.rrule.integration.shrinker"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.yallain.rrule.integration.shrinker"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation("io.github.yallain:rrule-kmp:${rruleVersion.get()}")
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
