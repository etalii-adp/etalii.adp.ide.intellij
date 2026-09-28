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

// The draw.io designer: built on core's diagram framework only (principle III).
dependencies {
    implementation(project(":core"))
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation(project(":testing"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // A failure's message and causes in the console: on CI that is the only place they can be read without the reports.
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    // Vendored draw.io templates, read by the tests.
    systemProperty("adp.testdata", layout.projectDirectory.dir("testdata").asFile.absolutePath)
    inputs.dir("testdata")
}

// The unified IntelliJ IDEA distribution's Ultimate part cannot start in the headless test IDE;
// the plug-in depends only on the platform, so its tests run without it.
tasks.prepareTestSandbox {
    disabledPlugins.add("com.intellij.modules.ultimate")
}
