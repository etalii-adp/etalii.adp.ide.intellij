# Contract: Build Interface for the Sandbox and the Real-IDE Tests

What a contributor, CI and the tests can rely on after this feature.

## `./gradlew runIde`

- Depends on `prepareSandboxProject`, which copies `freemind/testdata/examples/*.mm` and `drawio/testdata/examples/*.drawio` into `<sandbox>/example-project`, overwriting same-named files and keeping others.
- Opens `<sandbox>/example-project` as its project.
- Passes `-XX:HeapDumpPath=<sandbox>/log_runIde` and `-XX:ErrorFile=<sandbox>/log_runIde/hs_err_pid%p.log`. `-XX:+HeapDumpOnOutOfMemoryError` stays on.
- Heap: the platform default (2 GB).

## `./gradlew integrationTest`

- Passes system property `adp.ideTests.home` to the test JVM.
- Resolution order: Gradle property `adpIdeTestsHome`, then environment variable `ADP_IDE_TESTS_HOME`, then the per-user default in [data-model.md](../data-model.md).
- Every test class installs the location with `IdeTestsHome.install()` before it starts an IDE. The Starter framework then keeps installers, unpacked IDEs and per-test folders below `<adp.ideTests.home>/out/ide-tests/`.
- Nothing is written to `<repository>/out/ide-tests`.

## IDE project model

- The root build applies Gradle's `idea` plug-in and excludes `out`, `.intellijPlatform`, `.claude/worktrees` and each module's `build` folder, so any IntelliJ Platform IDE importing this Gradle build leaves them unindexed.
