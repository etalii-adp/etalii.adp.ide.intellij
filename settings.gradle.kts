import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.19.0"
    // Provisions the Java toolchain the platform requires when it is not installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "EtAlii.Adp.IntelliJ"

include("core", "freemind", "drawio", "testing")

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        // TeamCity service messages, used by the Starter framework in integration tests.
        maven("https://download.jetbrains.com/teamcity-repository")
        intellijPlatform {
            defaultRepositories()
        }
    }
}
