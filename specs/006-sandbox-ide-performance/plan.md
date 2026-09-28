# Implementation Plan: A Fast Sandbox IDE

**Branch**: `features/006-sandbox-ide-performance` | **Date**: 2026-09-27 | **Spec**: [sandbox-ide-performance.spec.md](sandbox-ide-performance.spec.md)

## Summary

The sandbox slows down because it reopens this repository, which holds 29 GB of IDEs downloaded by the real-IDE tests, and indexes them until its 2 GB heap runs out. The fix is in the build, not the plug-in: `runIde` opens a freshly copied example project in the sandbox folder, the real-IDE tests keep their downloads in a per-user cache outside the repository, the build excludes generated folders from any IDE project, and the sandbox writes heap dumps and crash logs to its own log folder. New headless tests time the designers on large files, and a quickstart procedure measures the sandbox before and after. No new dependency: the `idea` plug-in ships with Gradle and the IDE Starter framework is already on the integration test classpath.

## Technical Context

**Language/Version**: Java 25 (plug-in and tests), Gradle Kotlin DSL (build)
**Primary Dependencies**: IntelliJ Platform Gradle Plugin 2.19.0, IntelliJ Platform 2026.2.3, IDE Starter framework 262.10968.63 (integration tests)
**Testing**: headless platform tests (`./gradlew test`), Starter and Driver tests in real IDEs (`./gradlew integrationTest`), a manual sandbox measurement in [quickstart.md](quickstart.md)
**Target Platform**: contributor machines on Windows, macOS and Linux; Peter's machine has 32 logical processors and 64 GB
**Performance Goals**: the spec's SC-001 to SC-006
**Constraints**: nothing may slow down the headless build or CI; no committed `.idea` files; test data stays untouched by sandbox use

## Constitution Check

| Principle | Assessment |
|---|---|
| I. Native IntelliJ Platform citizenship | PASS. No designer behaviour changes. Exclusions use the platform's own Gradle import. |
| II. The text file is the source of truth | PASS. The sandbox edits copies, so vendored example files are never rewritten by hand trials. |
| III. One framework, many designers | PASS. No change to `core`; each format adds only its own latency test. |
| IV. Test-first, against real files | PASS. The latency tests and the download-location test are written first and seen failing. The latency tests use generated files, like the existing performance tests; the sandbox project uses the real published examples. |
| V. Simplicity | PASS. Build configuration and one test helper; no new library; the heap stays at its default. |
| Platform constraints | PASS. Headless build unchanged; no runtime network access added. |
| Workflow | PASS once the work moves to its own branch and worktree (the spec was drafted on `develop`). |
| Quality above everything else | PASS. The change must build with no new compiler or Gradle deprecation warnings. |

Re-checked after Phase 1: unchanged.

## Project Structure

### Documentation (this feature)

```text
specs/006-sandbox-ide-performance/
├── sandbox-ide-performance.spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── build-interface.md
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

```text
build.gradle.kts                          # runIde project argument, dump paths, idea excludes, test cache property
src/integrationTest/java/etalii/adp/it/
├── IdeTestsHome.java                     # new: points the Starter framework at the per-user cache
└── *IntegrationTest.java, OpenDrawioTest.java   # call IdeTestsHome before starting an IDE
freemind/src/test/java/etalii/adp/freemind/ui/
└── TypingLatencyTest.java                # new: SC-004 on a generated 2,000-node map
drawio/src/test/java/etalii/adp/drawio/
├── DrawioAsserts.java (or existing helper) # new generator for a 500-cell diagram
└── TypingLatencyTest.java                # new: SC-004 on a generated 500-cell diagram
README.md                                 # sandbox folders, reset, test download location and size
.gitignore                                # nothing new expected; verify
```

**Structure Decision**: all behaviour lives in the root build script and the integration test source set; the format modules gain one test each. No production code changes unless the latency tests fail (research R6).

## Approach

1. **Baseline first.** Before any change, run the quickstart measurement once with `out/ide-tests` present, and record the figures in `quickstart.md` under "Baseline". This is the only point where the sandbox is expected to fail.
2. **Downloads out of the repository** (R2, FR-009). Write a test in `src/integrationTest` that asserts the Starter test home resolves outside the repository, see it fail, add `IdeTestsHome`, pass `adp.ideTests.home` from `build.gradle.kts`, and call it from every integration test.
3. **Exclusions** (R3, FR-001). Apply the `idea` plug-in and exclude `out`, `.intellijPlatform`, `.claude/worktrees` and every `build` folder.
4. **Sandbox project** (R1, FR-002). Add `prepareSandboxProject` and pass its folder to `runIde`.
5. **Dumps** (R4, FR-003). Add the two JVM arguments to `runIde`.
6. **Designer latency** (R5, R6, FR-006). Write the two `TypingLatencyTest` classes and the leak check; fix only what fails.
7. **Documentation** (FR-007). README section on the sandbox and on the test download cache, including how to delete the old `out/ide-tests`.
8. **Measure after** with the same quickstart procedure and record the figures next to the baseline. Adjust a success-criteria number only with a reason recorded in `quickstart.md`.

## Risks

- **Kodein binding from Java** may be awkward (R2). The fallback is recorded in research R2, and the download-location test decides which one ships.
- **The sandbox's remembered project list** may still reopen this repository in addition to the example project. The after-measurement checks the log for the repository path; if it appears, the fix is to reset the sandbox's recent-projects state in `prepareSandboxProject`.
- **CI** (spec 005) will also use the per-user cache folder on its runners. That is fine for a fresh runner; whether CI caches it between runs is spec 005's decision.
