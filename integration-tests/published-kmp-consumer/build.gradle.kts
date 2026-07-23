import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val rruleGroup = providers.gradleProperty("rruleGroup")
    .orElse("com.github.yannallain")
val rruleVersion = providers.gradleProperty("rruleVersion")
    .orElse("0.0.0")

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("${rruleGroup.get()}:rrule-kmp:${rruleVersion.get()}")
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
