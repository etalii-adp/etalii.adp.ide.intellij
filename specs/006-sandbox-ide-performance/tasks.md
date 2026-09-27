# Tasks: A Fast Sandbox IDE

**Input**: [plan.md](plan.md), [sandbox-ide-performance.spec.md](sandbox-ide-performance.spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Before T001**: the spec documents were drafted uncommitted on `develop` in `C:\git\etalii.adp.ide.intellij`, next to unrelated uncommitted changes. They move to this feature's branch in T001; nothing else from that working tree comes along.

**Format**: `- [ ] **T###** [P?] [US#] Description · exact/file/path`. `[P]` means independent within its wave. `it/` is short for `src/integrationTest/java/etalii/adp/it/`. Tests come first in every phase (constitution IV): write them, see them fail for the right reason, then implement.

**Seams that keep phases file-disjoint**:
- Every change to `build.gradle.kts` is foundational (Phase 2), because Stories 1 and 2 both configure `runIde`. Story phases only verify what it does.
- `IdeTestsHome` and its test belong to Story 1; each integration test class is edited only in Story 1.
- Story 3 owns only test files in `freemind`, `drawio` and `core`, plus any production fix a failing latency or leak test calls for, in the file that test names.

---

## Phase 1: Setup

**Wave 1 — independent:**

- [ ] **T001** Create branch `features/006-sandbox-ide-performance` from `develop` in its own worktree beside the repository (not under `.claude/worktrees`, which the old spec 004 worktree shows costs a second full checkout inside the repo), copy `specs/006-sandbox-ide-performance/` and `.specify/feature.json` into it, and commit them · `specs/006-sandbox-ide-performance/`

**⟶ Wait for Wave 1 to finish, then:**

- [ ] **T002** Run `./gradlew check` in the new worktree: green before any change · (no file)
- [ ] **T003** Baseline: follow quickstart steps 1 and 2 once in the original clone, where `out/ide-tests` and the spec 004 worktree are still present; stop the sandbox at the first out-of-memory error or after five minutes; add the row to Results · `specs/006-sandbox-ide-performance/quickstart.md`

---

## Phase 2: Foundational (blocks every story)

Files: `build.gradle.kts`.

**Wave 1 — one file:**

- [ ] **T004** In one pass over the root build: (a) apply Gradle's `idea` plug-in and exclude `out`, `.intellijPlatform`, `.claude/worktrees` and every module's `build` (R3, FR-001); (b) register `prepareSandboxProject`, a Copy of `freemind/testdata/examples/*.mm` and `drawio/testdata/examples/*.drawio` into `<sandboxDirectory>/example-project`, and make `runIde` depend on it and pass that folder as its argument (R1, FR-002); (c) add a `jvmArgumentProviders` entry to `runIde` with `-XX:HeapDumpPath=<sandboxLogDirectory>` and `-XX:ErrorFile=<sandboxLogDirectory>/hs_err_pid%p.log` (R4, FR-003); (d) pass `adp.ideTests.home` to `integrationTest`, resolved from `adpIdeTestsHome`, `ADP_IDE_TESTS_HOME`, then the per-user default in data-model.md (R2, FR-009). No new Gradle deprecation warnings · `build.gradle.kts`

**⟶ Wait for Wave 1 to finish, then:**

- [ ] **T005** `./gradlew help --warning-mode all` shows no new warnings, and `./gradlew prepareSandboxProject` produces the example folder with every example file · (no file)

---

## Phase 3: User Story 1, the sandbox is ready quickly and stays responsive (P1) MVP

**Goal**: the sandbox opens a small example project, never indexes downloads, build output or nested worktrees, and the real-IDE tests stop writing into the repository.

**Independent Test**: spec User Story 1.

Files: `it/IdeTestsHome.java`, `it/IdeTestsHomeTest.java`, `it/EditUndoIntegrationTest.java`, `it/NoPreviousHostIntegrationTest.java`, `it/OpenDrawioTest.java`, `it/OpenMapIntegrationTest.java`, `it/SettingsPageIntegrationTest.java`.

### Tests

- [ ] **T006** [US1] `IdeTestsHomeTest`: after `IdeTestsHome.install()`, the Starter framework's test home, installers, cache and tests folders all resolve below `adp.ideTests.home` and none is inside `adp.repository`; a missing property fails with a message naming it. See it fail (no class yet) · `it/IdeTestsHomeTest.java`

### Implementation

**Wave 1 — one file:**

- [ ] **T007** [US1] `IdeTestsHome.install()`: replace the Starter `GlobalPaths` binding with one rooted at `adp.ideTests.home`, idempotently. First try Kodein's non-inline builder API from Java; if that is unreadable, use research R2's fallback and record the choice in research.md · `it/IdeTestsHome.java`

**⟶ Wait for Wave 1 to finish, then:**

**Wave 2 — independent (different files):**

- [ ] **T008** [P] [US1] Call `IdeTestsHome.install()` before the IDE starts · `it/EditUndoIntegrationTest.java`
- [ ] **T009** [P] [US1] Call `IdeTestsHome.install()` before the IDE starts · `it/NoPreviousHostIntegrationTest.java`
- [ ] **T010** [P] [US1] Call `IdeTestsHome.install()` before the IDE starts · `it/OpenDrawioTest.java`
- [ ] **T011** [P] [US1] Call `IdeTestsHome.install()` before the IDE starts · `it/OpenMapIntegrationTest.java`
- [ ] **T012** [P] [US1] Call `IdeTestsHome.install()` before the IDE starts · `it/SettingsPageIntegrationTest.java`

**⟶ Wait for Wave 2 to finish, then:**

- [ ] **T013** [US1] Run `./gradlew integrationTest` with an empty `out/` in the worktree: every test passes, the downloads appear under `adp.ideTests.home`, and `out/ide-tests` is not created · (no file)
- [ ] **T014** [US1] Quickstart steps 2 and 3: the sandbox opens `example-project` (not the repository), indexes fewer than 5,000 files within 30 seconds, and logs no memory event in thirty minutes; also open the repository itself in the sandbox and confirm `out`, `.intellijPlatform` and `.claude/worktrees` are excluded (SC-001, SC-002, SC-003) · (no file)

**Checkpoint**: the reported slowdown is gone; Story 1 is independently usable.

---

## Phase 4: User Story 2, a failure leaves evidence in the right place (P2)

**Goal**: heap dumps and crash logs land in the sandbox's log folder, never in the Gradle cache.

**Independent Test**: spec User Story 2.

Files: none (the configuration is T004).

- [ ] **T015** [US2] Quickstart step 5: with a temporary `-Xmx256m`, force an out-of-memory error; `java_pid*.hprof` is in `log_runIde` and the Gradle cache grew by less than 10 MB; remove the temporary setting (SC-005) · (no file)

**Checkpoint**: Story 2 verified.

---

## Phase 5: User Story 3, the designers' own cost is known and bounded (P2)

**Goal**: typing latency on large files is measured in every build, idle designers cost nothing, and closed designers are released.

**Independent Test**: spec User Story 3.

Files: `freemind/src/test/java/etalii/adp/freemind/ui/TypingLatencyTest.java`, `drawio/src/test/java/etalii/adp/drawio/GeneratedDiagrams.java`, `drawio/src/test/java/etalii/adp/drawio/TypingLatencyTest.java`, `core/src/test/java/etalii/adp/core/DesignerLeakTest.java`, and only if a test fails, the production file it points at.

### Tests

**Wave 1 — independent (different files):**

- [ ] **T016** [P] [US3] Type a burst of characters into a node's text in the text editor beside a designer showing `FreeMindAsserts.generatedMap(2000)`; time each keystroke until the designer shows it; print `SC-004 typing map ... ms` and assert the median within 100 ms, using `EditPerformanceTest`'s warm-up and CI headroom convention · `freemind/src/test/java/etalii/adp/freemind/ui/TypingLatencyTest.java`
- [ ] **T017** [P] [US3] A generator for a valid draw.io file with a given number of cells, laid out on a grid with edges between neighbours · `drawio/src/test/java/etalii/adp/drawio/GeneratedDiagrams.java`
- [ ] **T018** [P] [US3] Open and close a designer many times on `FakeFormat` and check that no editor, document listener or view stays reachable, with the platform test framework's leak checks (FR-006) · `core/src/test/java/etalii/adp/core/DesignerLeakTest.java`

**⟶ Wait for Wave 1 to finish, then:**

- [ ] **T019** [US3] The same typing measurement as T016 on `GeneratedDiagrams` with 500 cells, printing `SC-004 typing diagram ... ms` · `drawio/src/test/java/etalii/adp/drawio/TypingLatencyTest.java`

### Implementation

- [ ] **T020** [US3] Run the three tests. If one fails, fix the cause in the file it points at and record the change and its reason in research.md R6; if all pass, record the measured figures in the quickstart Results row · (file named by the failing test, or `specs/006-sandbox-ide-performance/quickstart.md`)

**Checkpoint**: Story 3 verified; the designers are ruled in or out as a second cause.

---

## Phase 6: User Story 4, setting up the sandbox is a known cost (P3)

**Goal**: a second `runIde` downloads nothing, and `runIde` does not verify or run real-IDE tests.

Files: none.

- [ ] **T021** [US4] Run `./gradlew runIde` twice; the second with `--offline` succeeds, and `--dry-run` shows no `verifyPlugin` or `integrationTest` in its task graph (SC-006, FR-008) · (no file)

**Checkpoint**: Story 4 verified.

---

## Phase 7: Polish

Files: `README.md`, `specs/006-sandbox-ide-performance/quickstart.md`.

**Wave 1 — independent (different files):**

- [ ] **T022** [P] A "Trying the plug-in" section: what `runIde` opens, where the sandbox keeps settings, logs, dumps and crash logs, how to reset it, where the real-IDE tests keep their downloads (about 30 GB), how to override that folder, and that old `out/ide-tests` folders can be deleted (FR-007) · `README.md`
- [ ] **T023** [P] Quickstart steps 1 to 5 after the change on Peter's machine; add the "after" row to Results and record any success-criteria number that had to change, with its reason · `specs/006-sandbox-ide-performance/quickstart.md`

**⟶ Wait for Wave 1 to finish, then:**

- [ ] **T024** Validate against the Success Criteria: `./gradlew check` green with no new compiler or Gradle warnings, and every SC-001 to SC-006 met in the Results table · (no file)

Manual clean-up for Peter, not a task: deleting `out/ide-tests` in the original clone (29 GB), and removing the merged spec 004 worktree at `.claude/worktrees/004-settings-page` (31 GB) as CLAUDE.md asks for merged branches. Both are his call.

---

## Dependencies & Execution Order

- Setup (T001 to T003) blocks everything. T003 runs in the original clone, so it can overlap T002.
- Foundational (T004, T005) blocks every story.
- Story 1: T006, then T007, then T008 to T012 in parallel, then T013 and T014.
- Story 2 (T015) needs only Foundational; it can run beside Story 1.
- Story 3: T016, T017 and T018 in parallel, then T019, then T020. It needs only Foundational and can run beside Stories 1 and 2.
- Story 4 (T021) needs only Foundational.
- Polish: T022 and T023 after every story, then T024.
