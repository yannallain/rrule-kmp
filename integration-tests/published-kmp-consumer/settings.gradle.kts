pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val rruleGroup = providers.gradleProperty("rruleGroup")
    .orElse("com.github.yannallain")
val rruleRepositoryUrl = providers.gradleProperty("rruleRepositoryUrl")
    .orElse(rootDir.resolve("../../build/repository").toURI().toString())

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "rruleKmpPublication"
                    url = uri(rruleRepositoryUrl.get())
                }
            }
            filter {
                includeGroup(rruleGroup.get())
            }
        }
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "rrule-kmp-published-consumer"
