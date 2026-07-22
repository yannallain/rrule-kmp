@file:OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "io.github.yallain"
version = providers.gradleProperty("VERSION_NAME").orElse("0.1.0-SNAPSHOT").get()

private val minimumIosVersion = "13.0"
private val iosDeploymentTargetArgument =
    "-Xoverride-konan-properties=minVersion.ios=$minimumIosVersion"
private val frameworkShortVersion = project.version.toString().substringBefore('-')
private val frameworkBundleVersion = providers.gradleProperty("APPLE_BUNDLE_VERSION")
    .orElse(frameworkShortVersion)
    .get()

require(frameworkShortVersion.matches(Regex("[0-9]+(?:\\.[0-9]+){0,2}"))) {
    "VERSION_NAME must begin with a one-to-three-component numeric Apple framework version"
}
require(frameworkBundleVersion.matches(Regex("[0-9]+(?:\\.[0-9]+){0,2}"))) {
    "APPLE_BUNDLE_VERSION must contain one to three numeric components"
}

kotlin {
    explicitApi()

    abiValidation {}

    val xcFramework = XCFramework("RRuleKmpCore")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.all {
            freeCompilerArgs += iosDeploymentTargetArgument
        }
        target.binaries.framework {
            baseName = "RRuleKmpCore"
            isStatic = true
            binaryOption("bundleId", "io.github.yallain.rrule.apple")
            binaryOption("bundleShortVersionString", frameworkShortVersion)
            binaryOption("bundleVersion", frameworkBundleVersion)
            xcFramework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

val releaseXcFramework = layout.buildDirectory.dir(
    "XCFrameworks/release/RRuleKmpCore.xcframework",
)
val normalizedReleaseXcFramework = layout.buildDirectory.dir(
    "normalizedXCFrameworks/release/RRuleKmpCore.xcframework",
)

val normalizeReleaseXcFramework by tasks.registering(NormalizeXcFramework::class) {
    dependsOn("assembleRRuleKmpCoreReleaseXCFramework")
    sourceDirectory.set(releaseXcFramework)
    outputDirectory.set(normalizedReleaseXcFramework)
}

tasks.register<Sync>("prepareLocalSwiftPackage") {
    group = "distribution"
    description = "Assembles the local RRuleKit Swift package with its release XCFramework."
    dependsOn(normalizeReleaseXcFramework)

    from(rootProject.layout.projectDirectory.dir("apple/swift-package")) {
        exclude(
            ".build/**",
            ".swiftpm/**",
            "Artifacts/**",
            "DerivedData/**",
            "**/.DS_Store",
            "**/*.xcresult/**",
            "**/*.xcworkspace/**",
        )
    }
    from(normalizedReleaseXcFramework) {
        into("Artifacts/RRuleKmpCore.xcframework")
    }
    from(rootProject.layout.projectDirectory.file("LICENSE"))
    from(rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES"))
    into(rootProject.layout.buildDirectory.dir("swift-package"))
}

tasks.register<Zip>("zipRRuleKmpCoreReleaseXCFramework") {
    group = "distribution"
    description = "Creates the immutable XCFramework archive for remote SwiftPM distribution."
    dependsOn(normalizeReleaseXcFramework)

    archiveFileName.set("RRuleKmpCore-${project.version}.xcframework.zip")
    destinationDirectory.set(
        rootProject.layout.buildDirectory.dir("distributions/${project.version}"),
    )
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(normalizedReleaseXcFramework) {
        into("RRuleKmpCore.xcframework")
    }
}

/** Produces a byte-stable XCFramework by sorting Xcode's nondeterministic library array. */
@CacheableTask
abstract class NormalizeXcFramework : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun normalize() {
        val source = sourceDirectory.get().asFile
        val output = outputDirectory.get().asFile
        check(source.isDirectory) { "XCFramework source does not exist: $source" }
        if (output.exists()) check(output.deleteRecursively()) {
            "Could not replace normalized XCFramework: $output"
        }
        check(source.copyRecursively(output, overwrite = true)) {
            "Could not copy XCFramework to normalization directory: $output"
        }

        val infoPlist = output.resolve("Info.plist")
        val content = infoPlist.readText()
        val librariesKey = content.indexOf("<key>AvailableLibraries</key>")
        check(librariesKey >= 0) { "XCFramework Info.plist has no AvailableLibraries array" }
        val arrayTag = content.indexOf("<array>", startIndex = librariesKey)
        check(arrayTag >= 0) { "XCFramework AvailableLibraries value is not an array" }
        val bodyStart = content.indexOf('\n', startIndex = arrayTag) + 1
        // The outer array closes at one tab of indentation. Matching the complete line
        // avoids mistaking a nested SupportedArchitectures array for the outer array.
        val closingArrayLineBreak = content.indexOf("\n\t</array>", startIndex = bodyStart)
        val bodyEnd = closingArrayLineBreak + 1
        check(bodyStart > 0 && closingArrayLineBreak >= 0 && bodyEnd > bodyStart) {
            "XCFramework AvailableLibraries array has an unexpected format"
        }

        val body = content.substring(bodyStart, bodyEnd)
        val dictionaryPattern = Regex("(?s)\\t\\t<dict>.*?\\n\\t\\t</dict>")
        val identifierPattern = Regex(
            "<key>LibraryIdentifier</key>\\s*<string>([^<]+)</string>",
        )
        val dictionaries = dictionaryPattern.findAll(body).map { it.value }.toList()
        check(dictionaries.isNotEmpty() && dictionaryPattern.replace(body, "").isBlank()) {
            "XCFramework AvailableLibraries array contains an unexpected value"
        }
        val sorted = dictionaries.sortedBy { dictionary ->
            identifierPattern.find(dictionary)?.groupValues?.get(1)
                ?: error("XCFramework library has no LibraryIdentifier")
        }
        val normalizedBody = sorted.joinToString(separator = "\n", postfix = "\n")
        infoPlist.writeText(content.replaceRange(bodyStart, bodyEnd, normalizedBody))
    }
}
