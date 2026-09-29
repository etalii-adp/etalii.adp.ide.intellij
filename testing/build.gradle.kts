import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform.module")
}

val javaRelease = providers.gradleProperty("javaVersion").get().toInt()

tasks.withType<JavaCompile>().configureEach {
    options.release = javaRelease
    options.encoding = "UTF-8"
}

// The tool test kit (contracts/test-kit.md): main code here is test support for format modules.
dependencies {
    implementation(project(":core"))
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        testFramework(TestFrameworkType.Platform, configurationName = "implementation")
    }
    implementation("junit:junit:4.13.2")
}
