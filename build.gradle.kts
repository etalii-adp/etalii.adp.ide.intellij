import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

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
        testFramework(TestFrameworkType.Starter, configurationName = integrationTestImplementation.name)
        pluginVerifier()
    }
    integrationTestImplementation(platform("org.junit:junit-bom:5.13.4"))
    integrationTestImplementation("org.junit.jupiter:junit-jupiter")
    integrationTestRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

tasks.check {
    dependsOn(integrationTestTask, tasks.verifyPlugin)
}
