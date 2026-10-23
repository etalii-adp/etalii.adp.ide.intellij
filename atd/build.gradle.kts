import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("java")
    id("org.jetbrains.intellij.platform.module")
}

val javaRelease = providers.gradleProperty("javaVersion").get().toInt()

tasks.withType<JavaCompile>().configureEach {
    options.release = javaRelease
    options.encoding = "UTF-8"
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        // The platform's language server support, which the module's server support provider plugs into.
        bundledModule("intellij.platform.lsp")
    }
}

// ATD's language server for each platform the plug-in ships it for (etalii.adp spec 035, T115): the desktop host's
// release that atd-lsp.lock names, each archive checked against the SHA-256 the lock states, its atd-lsp unpacked into
// build/atd-lsp/bin/<platform>/ for the plug-in's bin/. ATD_RELEASE_DIR takes the archives from a folder instead of
// downloading them; they are checked all the same.
val lockFile = rootProject.layout.projectDirectory.file("atd-lsp.lock")
val downloads = layout.buildDirectory.dir("atd-lsp/downloads")
val serverBinaries = layout.buildDirectory.dir("atd-lsp/bin")

val downloadAtdLsp = tasks.register("downloadAtdLsp") {
    description = "Downloads the atd-lsp release atd-lsp.lock names and checks each archive's SHA-256."
    group = "build"
    val releaseDir = providers.environmentVariable("ATD_RELEASE_DIR")
    inputs.file(lockFile)
    outputs.dir(downloads)
    doLast {
        val lock = Properties().apply { lockFile.asFile.reader().use { load(it) } }
        val release = lock.getProperty("release")
        val repository = lock.getProperty("repository")
        val folder = downloads.get().asFile.apply { mkdirs() }
        for (name in lock.stringPropertyNames().filter { it.startsWith("atd-") }) {
            val expected = lock.getProperty(name)
            val bytes = if (releaseDir.isPresent) {
                File(releaseDir.get(), name).readBytes()
            } else {
                URI("https://github.com/$repository/releases/download/$release/$name").toURL().openStream().use { it.readBytes() }
            }
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            if (actual != expected) {
                throw GradleException("$name of $release has SHA-256 $actual; atd-lsp.lock states $expected.")
            }
            File(folder, name).writeBytes(bytes)
        }
    }
}

val fetchAtdLsp = tasks.register<Sync>("fetchAtdLsp") {
    description = "Unpacks each platform's atd-lsp from the checked release into build/atd-lsp/bin/<platform>/."
    group = "build"
    dependsOn(downloadAtdLsp)
    for (platform in listOf("windows-x64", "macos-arm64", "linux-x64")) {
        val archive = downloads.map { it.file(if (platform.startsWith("windows")) "atd-$platform.zip" else "atd-$platform.tar.gz") }
        from(archive.map { if (it.asFile.name.endsWith(".zip")) zipTree(it) else tarTree(resources.gzip(it)) }) {
            include("atd-lsp", "atd-lsp.exe")
            filePermissions { unix("rwxr-xr-x") }
            into(platform)
        }
    }
    into(serverBinaries)
}

// The walk of the same release, with the examples it edits, for the integration tests (T116); never shipped.
val walkFolder = layout.buildDirectory.dir("atd-lsp/walk")
val fetchAtdWalk = tasks.register<Sync>("fetchAtdWalk") {
    description = "Unpacks the walk of the checked release into build/atd-lsp/walk/."
    group = "build"
    dependsOn(downloadAtdLsp)
    from(downloads.map { zipTree(it.file("atd-walk.zip")) })
    into(walkFolder)
}

configurations.create("atdWalk") {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts.add("atdWalk", walkFolder) { builtBy(fetchAtdWalk) }

// The root project packs build/atd-lsp/bin/ into the plug-in's bin/.
configurations.create("atdLspBinaries") {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts.add("atdLspBinaries", serverBinaries) { builtBy(fetchAtdLsp) }
