@file:OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)

import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `maven-publish`
}

group = "io.github.yallain"
version = providers.gradleProperty("VERSION_NAME").orElse("0.1.0-SNAPSHOT").get()

kotlin {
    explicitApi()

    abiValidation {}

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    android {
        namespace = "io.github.yallain.rrule"
        compileSdk = 36
        minSdk = 21
        aarMetadata {
            // The library uses no platform resources or APIs newer than its runtime floor.
            minCompileSdk = 21
            // desugar_jdk_libs 2.1.x requires an AGP 8.x consumer toolchain.
            minAgpVersion = "8.0.0"
        }
        // Packages java.time support into this module's instrumented-test APK. Published consumers
        // below API 26 must still enable desugaring in the application module; see README.md.
        enableCoreLibraryDesugaring = true
        withHostTestBuilder {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.junit)
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

val verifyEmbeddedLegalFiles by tasks.registering {
    val rootLicense = layout.projectDirectory.file("LICENSE")
    val embeddedLicense = layout.projectDirectory.file(
        "src/commonMain/resources/META-INF/LICENSE-rrule-kmp",
    )
    val rootNotices = layout.projectDirectory.file("THIRD_PARTY_NOTICES")
    val embeddedNotices = layout.projectDirectory.file(
        "src/commonMain/resources/META-INF/THIRD_PARTY_NOTICES-rrule-kmp",
    )
    inputs.files(rootLicense, embeddedLicense, rootNotices, embeddedNotices)

    doLast {
        check(rootLicense.asFile.readBytes().contentEquals(embeddedLicense.asFile.readBytes())) {
            "The embedded artifact license must exactly match the root LICENSE file."
        }
        check(rootNotices.asFile.readBytes().contentEquals(embeddedNotices.asFile.readBytes())) {
            "The embedded third-party notices must exactly match the root notice file."
        }
    }
}

tasks.named("check") {
    dependsOn(verifyEmbeddedLegalFiles)
}

// Java consumer tests compile against the same bytecode floor as the Kotlin/JVM artifact.
tasks.withType<org.gradle.api.tasks.compile.JavaCompile>()
    .matching { it.name.startsWith("compileJvm") }
    .configureEach { options.release.set(11) }

publishing {
    repositories {
        maven {
            // A deterministic, disposable repository for testing every generated publication.
            name = "localBuild"
            setUrl(layout.buildDirectory.dir("repository"))
        }
    }

    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("rrule-kmp")
            description.set("RFC 5545 recurrence rules for Kotlin Multiplatform.")
            url.set("https://github.com/yannallain/rrule-kmp")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/license/mit")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("yannallain")
                    name.set("Yann Allain")
                    email.set("yann.allain@protonmail.com")
                }
            }
            scm {
                url.set("https://github.com/yannallain/rrule-kmp")
                connection.set("scm:git:https://github.com/yannallain/rrule-kmp.git")
                developerConnection.set("scm:git:ssh://git@github.com/yannallain/rrule-kmp.git")
            }
        }
    }
}
