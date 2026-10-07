import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform.module")
}

val javaRelease = providers.gradleProperty("javaVersion").get().toInt()

tasks.withType<JavaCompile>().configureEach {
    options.release = javaRelease
    options.encoding = "UTF-8"
    // The repository's rule is no compiler warnings; this module enforces it.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

// The FBL library imports nothing from the platform (HostFreeTest); the platform is here for the one test that runs on it.
dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        testFramework(TestFrameworkType.Platform)
    }
    // FreeMindCrossCheckTest compares the mindmap binding with the FreeMind module's own parser.
    testImplementation(project(":freemind"))
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
    // The conformance corpus, the real files, the baseline inventory and the divergence record.
    systemProperty("adp.fbl.testdata", layout.projectDirectory.dir("testdata").asFile.absolutePath)
    inputs.dir("testdata")
    // The repository's own mind maps, read by the real-file tests.
    val freemindTestdata = rootProject.layout.projectDirectory.dir("freemind/testdata")
    systemProperty("adp.freemind.testdata", freemindTestdata.asFile.absolutePath)
    inputs.dir(freemindTestdata)
}

// The unified IntelliJ IDEA distribution's Ultimate part cannot start in the headless test IDE;
// the plug-in depends only on the platform, so its tests run without it.
tasks.prepareTestSandbox {
    disabledPlugins.add("com.intellij.modules.ultimate")
}
