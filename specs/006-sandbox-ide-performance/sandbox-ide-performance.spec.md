# Feature Specification: A Fast Sandbox IDE

**Feature Branch**: `features/006-sandbox-ide-performance` (merged into `develop` by PR #13)
**Created**: 2026-09-27
**Status**: Draft
**Input**: "Running the plug-in with `./gradlew runIde` is slow. Investigate the slowdown and fix it."

## Context

`./gradlew runIde` starts a sandbox IntelliJ IDEA with the plug-in installed. It is how a contributor tries a tool by hand. Peter reports that the sandbox becomes slow to the point of being unusable. The investigation below was done on Peter's machine on 2026-09-27 from the sandbox's own log (`.intellijPlatform/sandbox/EtAlii.Adp.IntelliJ/IU-2026.2.3/log_runIde/idea.log`), its freeze reports and the repository's folders. The sandbox was not running at the time.

What the evidence shows:

1. **The sandbox opens this repository as its project.** Its most recent session (2026-09-27 10:46) reopened `C:\git\etalii.adp.ide.intellij` from its recent projects, imported it as a Gradle project and started a Gradle sync inside the sandbox.
2. **That project contains 29 GB of downloaded IDEs.** The real-IDE tests (`./gradlew integrationTest`) download and unpack IntelliJ IDEA, PyCharm, Rider and WebStorm under `out/ide-tests/` (19 GB unpacked, 6.4 GB installers, 4.2 GB per-test folders). `out/` is ignored by git but not excluded from the IDE project, so the sandbox scans and indexes it. The log shows it indexing files such as `out/ide-tests/cache/builds/RD-262.10315.191/lib/ReSharperHost/...`.
3. **The sandbox then runs out of memory.** It has a 2 GB heap. About three and a half minutes after start it logs low-memory signals, then 11 `OutOfMemoryError: Java heap space` errors (15 heap-space messages in all) from the indexer, the file refresher and the UI thread, two freeze reports (10:50:31 and 10:50:41), and actions taking 1.8 to 2.6 seconds to update. The session has no clean shutdown in the log. Each of the three earlier sessions also logged one out-of-memory error, so the problem is not new.
4. **A heap dump lands in the Gradle cache.** The sandbox runs with `-XX:+HeapDumpOnOutOfMemoryError` and no dump path, so the JVM writes the dump into its working directory, which is the unpacked IDE inside the Gradle cache (`~/.gradle/caches/9.8.0/transforms/.../idea-2026.2.3-win`). This matches the 3 GB heap dump found there earlier today. That this dump came from the 10:50 session is inferred from the timing, not proven.
5. **The plug-in itself is not implicated yet.** The failure happened while typing (last action `EditorBackSpace`), but every out-of-memory error came from platform indexing and refresh. The tools re-parse the whole document once per keystroke batch on the UI thread (`core/src/main/java/etalii/adp/core/AdpToolFileEditor.java:127`), which is cheap for the example files and has not been measured on large ones.
6. **A second copy sits inside the repository.** The worktree of the merged spec 004 is still at `.claude/worktrees/004-settings-page`, inside this repository, and holds its own 31 GB, mostly its own real-IDE test downloads. A sandbox that opens this repository sees that too.
7. **Other observations, probably minor.** The sandbox's log has grown across four sessions since 2026-09-24 and still carries paths from the repository's two former folder names. JCEF starts in the sandbox. The sandbox runs IntelliJ IDEA Ultimate with the Ultimate module disabled, which logs a long list of excluded modules at every start.

So the slowdown is not the plug-in's code but what the sandbox is asked to do: index a project that holds several complete IDEs, inside a heap sized for a small project. This feature makes the sandbox fast and keeps it fast, and makes the cost of the tools themselves visible.

A **contributor** below is a person or agent who runs the sandbox to try the plug-in by hand.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The sandbox is ready quickly and stays responsive (Priority: P1)

A contributor runs `./gradlew runIde`. The sandbox opens a project that contains the files a contributor tries tools on, finishes indexing quickly, and stays responsive for as long as the contributor works in it. It never indexes downloaded IDEs, build output or its own sandbox folders, wherever the contributor has put them.

**Why this priority**: this is the reported problem, and the evidence points at it as the whole cause.

**Independent Test**: with the real-IDE test downloads present on disk, run `./gradlew runIde`, open an example `.mm` and an example `.drawio`, and edit both for ten minutes; the sandbox logs no out-of-memory error, no freeze report and no low-memory signal.

**Acceptance Scenarios**:

1. **Given** the real-IDE tests have run and their downloads are on disk, **When** the contributor runs `./gradlew runIde`, **Then** the sandbox does not scan or index any of those downloads.
2. **Given** a fresh sandbox, **When** it starts, **Then** it opens a project that contains example FreeMind and draw.io files, without the contributor choosing one.
3. **Given** a sandbox that last had a different project open, **When** it starts again, **Then** it still indexes no build output, downloaded IDE or sandbox folder.
4. **Given** the sandbox has been open and in use for thirty minutes, **When** the contributor opens a tool or types in one, **Then** it responds as quickly as it did in the first minute.

---

### User Story 2 - A failure leaves evidence in the right place (Priority: P2)

When the sandbox does run out of memory or freezes, the contributor finds the heap dump, freeze report and log in the sandbox's own folder, not in the Gradle cache, and the Gradle cache never grows by gigabytes because of a sandbox failure.

**Why this priority**: today a failure silently costs gigabytes in a shared cache and the evidence is hard to find. It does not make the sandbox faster, so it follows Story 1.

**Independent Test**: force the sandbox out of memory (for example with a deliberately tiny heap); the heap dump appears in the sandbox's log folder, nothing new appears in the Gradle cache.

**Acceptance Scenarios**:

1. **Given** the sandbox runs out of memory, **When** the JVM writes a heap dump, **Then** the dump is inside the sandbox's folder and not inside the Gradle cache.
2. **Given** the contributor runs `./gradlew clean`, **When** it completes, **Then** any heap dump from an earlier sandbox is gone with the rest of the build output, or is kept in one known place the readme names.

---

### User Story 3 - The tools' own cost is known and bounded (Priority: P2)

A contributor opens a large mind map and a large diagram in the sandbox and types in the text view beside the tool. Typing stays fluid. The project knows how large a file the tools stay fluid for, because it has been measured, and a change that makes them slower is noticed.

**Why this priority**: the investigation could not rule the tools in or out, because the platform ran out of memory first. Once Story 1 removes that noise, this confirms the plug-in is not a second cause.

**Independent Test**: open a generated FreeMind map of the size named in SC-004 and a draw.io file of comparable size, type a burst of characters in the text editor beside each tool, and measure the time from keystroke to the tool showing the change.

**Acceptance Scenarios**:

1. **Given** a large file of the size named in SC-004 is open in a tool, **When** the contributor types in the text editor beside it, **Then** each keystroke appears in the text editor without a visible delay and the tool catches up within the time named in SC-004.
2. **Given** a tool is open and the contributor is not doing anything, **When** a minute passes, **Then** the plug-in uses no measurable processor time.
3. **Given** several tools were opened and closed, **When** the contributor looks at memory use, **Then** the closed tools no longer hold memory.

---

### User Story 4 - Setting up the sandbox is a known cost, not a surprise (Priority: P3)

A contributor who runs `./gradlew runIde` for the first time, or after a platform version change, sees what is being downloaded and set up. Runs after that start the sandbox without downloading or unpacking anything again.

**Why this priority**: first runs are slow for a legitimate reason. It is worth telling apart from the reported slowdown but does not need to change.

**Independent Test**: run `./gradlew runIde` twice in a row; the second run downloads nothing.

**Acceptance Scenarios**:

1. **Given** the platform and plug-in dependencies are already on disk, **When** the contributor runs `./gradlew runIde`, **Then** nothing is downloaded.
2. **Given** `./gradlew runIde` runs, **When** it builds the plug-in, **Then** it does not also verify the plug-in against other IDEs or run the real-IDE tests.

### Edge Cases

- The contributor opens this repository in the sandbox on purpose, for example to look at a real `.mm` in `docs/`. The downloaded IDEs and build output must still not be indexed.
- A feature worktree lives inside the repository (under `.claude/worktrees/`, as CLAUDE.md's worktree convention produces). Its contents, including any downloads it holds, must not be indexed either.
- The contributor keeps real-IDE test downloads from several platform versions. Their size must not affect the sandbox.
- A contributor on another machine has less memory than Peter's 64 GB. The sandbox must stay usable within the heap it is given.
- The sandbox folder is reused after the repository was moved (its log still names the repository's two former folder paths). Stale recent-project entries must not reopen a project that no longer exists or is huge.
- Cloud sessions run no sandbox. Nothing here may make headless builds or the real-IDE tests slower.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The sandbox MUST NOT scan or index the real-IDE test downloads, build output, Gradle caches, its own sandbox folders or worktrees nested in the repository, in whatever project it opens.
- **FR-002**: `./gradlew runIde` MUST open a small example project containing FreeMind and draw.io files by default, not this repository.
- **FR-003**: The sandbox MUST keep writing a heap dump when it runs out of memory. A heap dump, freeze report or crash log from the sandbox MUST be written inside the sandbox's log folder and never inside the Gradle cache.
- **FR-004**: The sandbox MUST start and stay within the success criteria below on a machine that holds the real-IDE test downloads.
- **FR-005**: The project MUST have a repeatable measurement of the sandbox's start time, idle memory and tool typing latency, so the success criteria can be checked again after a change.
- **FR-006**: The tools MUST NOT use processor time while nothing changes, and MUST release their memory when closed.
- **FR-007**: The readme or quickstart MUST say where the sandbox keeps its logs, dumps and settings, how to reset it, and how much disk the real-IDE tests use and where.
- **FR-008**: `./gradlew runIde` MUST NOT download anything when its dependencies are already present.
- **FR-009**: The real-IDE test downloads MUST live outside the repository, in a per-user cache that every clone and worktree on the machine shares.

### Key Entities

- **Sandbox**: the IDE started by `./gradlew runIde`, with its own settings, system, plug-ins and log folders.
- **Sandbox project**: the project the sandbox opens, with the example files a contributor tries tools on.
- **Real-IDE test downloads**: the IDE installers and unpacked IDEs the real-IDE tests fetch, about 30 GB, kept in the per-user cache `adp.ideTests.home` (29 GB under `out/ide-tests/` before this feature).
- **Performance baseline**: the recorded start time, idle memory and typing latency the success criteria are checked against.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With the real-IDE test downloads on disk, the sandbox reaches a usable editor with an example map open within 60 seconds of `./gradlew runIde` on Peter's machine, when nothing needs downloading.
- **SC-002**: The sandbox's first indexing of its project covers fewer than 5,000 files and finishes within 30 seconds.
- **SC-003**: Over thirty minutes of opening, editing and closing tools, the sandbox logs no out-of-memory error, no low-memory signal and no freeze report, and its used heap after a garbage collection stays under half of its maximum.
- **SC-004**: In a FreeMind map of 2,000 nodes and a draw.io file of 500 cells, the tool shows a typed change within 100 milliseconds of the keystroke.
- **SC-005**: After any sandbox failure, the Gradle cache has not grown by more than 10 MB.
- **SC-006**: A second `./gradlew runIde` in a row downloads nothing.

## Assumptions

- The slowdown Peter sees is the one recorded on 2026-09-27 10:46 to 10:50. If it also happens with a small project open, Story 3 becomes the priority and the plan must say so.
- The 29 GB under `out/ide-tests/` is the IDE Starter framework's default download folder for the real-IDE tests; keeping those downloads between runs is wanted. Peter chose on 2026-09-27 to move them to a per-user cache outside the repository (FR-009).
- The sandbox's 2 GB heap is the IntelliJ Platform Gradle Plugin's default and is enough once the project is small. The plan may raise it, but not as the fix.
- The success-criteria numbers are first targets for Peter's machine (32 logical processors, 64 GB). The plan records the measured baseline and may adjust them with a reason.

## Out of Scope

- Speeding up the headless build, the unit tests or the real-IDE tests themselves.
- Deleting the existing downloads or the stale sandbox; that is a manual clean-up the contributor chooses.
- Tuning the platform's own startup (plug-in set, JCEF) beyond what the success criteria need.
