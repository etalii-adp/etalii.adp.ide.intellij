import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.Properties

plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = "etalii.adp"
version = providers.gradleProperty("pluginVersion").get()

val javaRelease = providers.gradleProperty("javaVersion").get().toInt()

tasks.withType<JavaCompile>().configureEach {
    options.release = javaRelease
    options.encoding = "UTF-8"
}

// Starter + Driver tests in real IDEs (research R9). They install the built plug-in zip.
val integrationTest: SourceSet = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
val integrationTestImplementation: Configuration = configurations.getByName("integrationTestImplementation") {
    extendsFrom(configurations.testImplementation.get())
}
val integrationTestRuntimeOnly: Configuration = configurations.getByName("integrationTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        pluginComposedModule(implementation(project(":core")))
        pluginComposedModule(implementation(project(":freemind")))
        pluginComposedModule(implementation(project(":drawio")))
        testFramework(TestFrameworkType.Starter, configurationName = integrationTestImplementation.name)
        pluginVerifier()
    }
    integrationTestImplementation(platform("org.junit:junit-bom:5.13.4"))
    integrationTestImplementation("org.junit.jupiter:junit-jupiter")
    // The platform plug-in strips Kotlin from the classpath; the Starter framework needs it.
    integrationTestImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.0")
    integrationTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
    integrationTestRuntimeOnly("org.junit.platform:junit-platform-launcher")
    integrationTestRuntimeOnly("junit:junit:4.13.2")
    integrationTestRuntimeOnly("org.jetbrains.teamcity:serviceMessages:2026.3-dsl6")
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            select {
                types = listOf(
                    IntelliJPlatformType.IntellijIdea,
                    IntelliJPlatformType.Rider,
                    IntelliJPlatformType.WebStorm,
                    IntelliJPlatformType.PyCharm,
                    IntelliJPlatformType.CLion,
                    IntelliJPlatformType.GoLand,
                    IntelliJPlatformType.PhpStorm,
                    IntelliJPlatformType.RubyMine,
                )
                sinceBuild = providers.gradleProperty("pluginSinceBuild").map { "$it.3" }
                untilBuild = providers.gradleProperty("pluginSinceBuild").map { "$it.*" }
            }
        }
    }
}

tasks.buildPlugin {
    archiveBaseName = "etalii-adp"
}

tasks.assemble {
    dependsOn(tasks.buildPlugin)
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs the Starter + Driver suite in real IntelliJ Platform IDEs."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
    dependsOn(tasks.buildPlugin)
    systemProperty("adp.plugin.zip", tasks.buildPlugin.flatMap { it.archiveFile }.get().asFile.absolutePath)
    systemProperty("adp.repository", rootDir.absolutePath)
    shouldRunAfter(tasks.test)
}

// Every third-party library the plug-in ships must have an Apache-2.0-compatible licence (research R21, SC-006).
// The composed modules' external runtime artifacts are checked against gradle/allowed-licences.properties;
// the IntelliJ Platform is provided by the IDE and is not on this classpath.
val verifyDependencyLicences = tasks.register("verifyDependencyLicences") {
    description = "Fails when a shipped third-party library is not allowed with an Apache-2.0-compatible licence."
    group = "verification"
    val allowlist = layout.projectDirectory.file("gradle/allowed-licences.properties")
    val compatible = setOf("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause", "CC0-1.0")
    val artifacts = configurations.runtimeClasspath.get().incoming.artifacts.resolvedArtifacts.map { results ->
        results.map { it.id.componentIdentifier }
            .filterIsInstance<org.gradle.api.artifacts.component.ModuleComponentIdentifier>()
            .map { "${it.group}:${it.module}" }
            .distinct()
            .sorted()
    }
    inputs.file(allowlist)
    inputs.property("artifacts", artifacts)
    doLast {
        val allowed = Properties().apply { allowlist.asFile.reader().use { load(it) } }
        val problems = artifacts.get().mapNotNull { artifact ->
            when (val licence = allowed.getProperty(artifact)) {
                null -> "$artifact is not in gradle/allowed-licences.properties"
                !in compatible -> "$artifact is listed with $licence, which is not Apache-2.0-compatible"
                else -> null
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Third-party libraries without an allowed licence:\n  " + problems.joinToString("\n  "))
        }
    }
}

tasks.check {
    dependsOn(integrationTestTask, tasks.verifyPlugin, verifyDependencyLicences)
}
