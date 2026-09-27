# Research: A Fast Sandbox IDE

Phase 0 of [plan.md](plan.md). Evidence for the cause is in the spec's Context; this file records how each requirement will be met and why.

## R1. What `runIde` opens

**Decision**: `runIde` passes one project path as a program argument: a folder `example-project` inside the runIde sandbox (`intellijPlatform` sandbox directory). A `prepareSandboxProject` Copy task, run before `runIde`, copies `freemind/testdata/examples/*.mm` and `drawio/testdata/examples/*.drawio` into it on every run, overwriting those files and leaving anything else a contributor added.

**Rationale**: an IntelliJ IDE opens a project path given on its command line instead of reopening the last one, which is what brought this repository back (spec Context 1). Copies keep the vendored test data clean: the working tree currently has sandbox edits to `drawio/testdata/examples/uml_1.drawio` and `workflow_1.drawio`, which is what opening the repository invites. `RunIdeTask` is a `JavaExec`, so `args(...)` and `dependsOn` are all it takes (checked in the IntelliJ Platform Gradle Plugin 2.19.0 sources). `freeplane-large-map.mm` (625 nodes, 97 KB) comes along, so a large map is at hand for a manual check.

**Alternatives considered**: opening `freemind/testdata/examples` directly, rejected because sandbox edits would change test data; a committed `sandbox-project/` folder, rejected because it duplicates vendored examples and their licences; the welcome screen, rejected by Peter.

## R2. Real-IDE test downloads outside the repository

**Decision**: the integration tests replace the IDE Starter framework's `GlobalPaths` binding before the first test runs, with a `GlobalPaths` whose checkout directory is a per-user cache folder, so `out/ide-tests` (installers, unpacked IDEs, per-test folders) lands under that folder instead of the repository. The folder is `%LOCALAPPDATA%\etalii-adp\ide-tests-home` on Windows and `$XDG_CACHE_HOME/etalii-adp/ide-tests-home` (default `~/.cache/...`) elsewhere. The build passes it to the test JVM as system property `adp.ideTests.home`; a Gradle property `adpIdeTestsHome` or environment variable `ADP_IDE_TESTS_HOME` overrides it. One shared helper in `src/integrationTest/java/etalii/adp/it/` installs the binding; every test class calls it in its setup.

**Rationale**: the Starter framework derives every folder from `GlobalPaths(checkoutDir)`: `testHomePath = checkoutDir/out/ide-tests`, and `installersDirectory`, `testsDirectory` and `localCacheDirectory` below it (Starter 262.10968.63 `GlobalPaths.kt`). The default `StarterGlobalPaths` uses the git repository root, which is why 29 GB sits in this repository. Starter documents replacing the binding (`di = DI { extend(di); bindSingleton<GlobalPaths>(overrides = true) {...} }` in `diContainer.kt`). One folder per user means every clone and worktree shares the same downloads (FR-009).

**Open for implementation**: the tests are Java and Kodein's `bindSingleton` is an inline Kotlin function. The implementation first tries Kodein's non-inline builder API from Java (`DI.Companion`, `erased(GlobalPaths.class)`, a `Singleton` binding). If that is unreadable, the fallback is to run the test JVM with its working directory set to the cache folder and `git` not finding a repository there, which Starter's `Git.getRepoRoot()` handles by using the working directory; that fallback must be proven with a test that fails when `out/ide-tests` appears in the repository.

**Alternatives considered**: a directory junction from `out/ide-tests` to the cache, rejected because the IDE follows links inside a project, so it would still be indexed; excluding `out/` only, rejected by Peter (it still costs 29 GB per clone and worktree).

## R3. Excluding generated folders from any project the sandbox opens

**Decision**: apply Gradle's built-in `idea` plug-in in the root build and add `out`, `.intellijPlatform`, `.claude/worktrees` and `build` of every module to `idea.module.excludeDirs`.

**Rationale**: FR-001 applies to any project the sandbox opens, including this repository opened on purpose. `.claude/worktrees` is included because worktrees are created there and each is a full checkout; the merged spec 004 worktree there holds 31 GB today. IntelliJ's Gradle import reads `excludeDirs` from the `idea` plug-in model, so the exclusion travels with the build and needs no committed `.idea` files (only local, untracked ones exist). It also protects a contributor's main IDE. `idea` ships with Gradle, so no dependency is added (principle V).

**Alternatives considered**: committing `.idea/*.iml` exclusions, rejected because the Gradle import regenerates them; relying on R2 alone, rejected because `out/` also holds other output and older downloads may linger.

## R4. Heap dumps and crash logs

**Decision**: `runIde` gets two JVM arguments through a `jvmArgumentProviders` entry: `-XX:HeapDumpPath=<sandboxLogDirectory>` and `-XX:ErrorFile=<sandboxLogDirectory>/hs_err_pid%p.log`. The existing `-XX:+HeapDumpOnOutOfMemoryError` stays (Peter: keep them).

**Rationale**: the JVM writes a dump without a path into its working directory, and `RunIdeTask` sets the working directory to the unpacked platform in the Gradle cache (`workingDir = platformPath.toFile()` in `RunIdeTask.kt`). That is how a 3 GB dump reached the Gradle cache. A directory as `HeapDumpPath` gives `java_pid<pid>.hprof` inside it. The log folder is where freeze reports and `idea.log` already are, so all evidence of one failure is in one place (US2), and it is outside the Gradle cache (SC-005).

**Alternatives considered**: turning dumps off, rejected by Peter; changing the working directory, rejected because the IDE launcher expects the platform folder.

## R5. Measuring the baseline and the success criteria

**Decision**: measure from what the sandbox already records, with one documented procedure in `quickstart.md`:
- Start time (SC-001) and indexing (SC-002): `log_runIde/idea.log` timestamps from `IDE STARTED` to the first `exit dumb mode`, and the files counted in `log_runIde/indexing-diagnostic/`.
- Memory and stability (SC-003): `jcmd <pid> GC.heap_info` with the platform's bundled runtime after a forced collection, and a search of `idea.log` for `OutOfMemoryError`, `Low memory signal` and `threadDumps-freeze` folders.
- Designer latency (SC-004): new headless tests `TypingLatencyTest` in `freemind` and `drawio`, next to the existing `EditPerformanceTest` and `OpenPerformanceTest`, which type into the text editor of a generated 2,000-node map or 500-cell diagram and time until the designer shows the change. They print the figure and use the same CI headroom convention as those tests.
- No re-download (SC-006): `./gradlew runIde --offline` succeeds on a second run.

**Rationale**: FR-005 wants repeatability without a new tool. The headless tests catch a designer regression in every build; the sandbox checks are a manual procedure because a real sandbox needs a display.

**Alternatives considered**: a Starter-based performance test of the sandbox itself, rejected as too heavy for a figure checked by hand; the platform's internal typing latency report, rejected because it times the text editor, not the designer.

## R6. Idle cost and memory release of the designers

**Decision**: verify, then fix only what the measurement shows. The designer refresh is already coalesced (one re-parse per batch of document changes on the event thread, `AdpDesignerEditor.java:127`) and registered with the editor's disposable. A headless test opens and closes designers in a loop and checks that no editor instance stays reachable, using the platform test framework's leak checks.

**Rationale**: the spec lists this as a check, not a known defect. Nothing in the code runs on a timer.

**Alternatives considered**: moving parsing off the event thread now, rejected until SC-004 shows it is needed (principle V).

## R7. Heap size

**Decision**: keep the platform default of 2 GB for `runIde`.

**Rationale**: the out-of-memory errors came from indexing 29 GB of IDEs. With R1 to R3 the project has about 20 files. The baseline after the change decides whether this needs revisiting (spec Assumptions).
