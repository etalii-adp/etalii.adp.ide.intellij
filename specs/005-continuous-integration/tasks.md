# Tasks: Continuous Integration and Plug-in Downloads

**Input**: [plan.md](plan.md), [continuous-integration.spec.md](continuous-integration.spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/ci-interface.md](contracts/ci-interface.md), [quickstart.md](quickstart.md)

**Format**: `- [ ] **T###** [P?] [US#] Description · exact/file/path`. `[P]` means independent of the other tasks in its wave. `build.yml` is short for `.github/workflows/build.yml`. "Record" means add a row to the Results table in `quickstart.md`.

**How a workflow change is tested**: the plug-in gains no behaviour, so no plug-in test is written. Each change's expected outcome is already written in quickstart.md; the task that follows a change sees it in a real run, and the refusals are provoked on purpose (constitution IV, as the plan's Constitution Check records).

**Seams**:
- Nearly every story edits `build.yml`, so the story phases run in order, not side by side. Within a phase, only the `README.md` tasks are parallel to the `build.yml` tasks.
- What is already on `develop` (research R1: FR-001, FR-002, FR-003, FR-006, FR-007, FR-009) gets no implementation task, only its check in T006 and T007.
- What only `develop` can show (the first real development build, the first versioned release) is in quickstart.md under "After the merge" and is not a task (research R11).

---

## Phase 1: Setup

- [ ] **T001** Confirm the work is on `features/005-continuous-integration` in its own worktree, on top of the current `develop` (if the planning pull request was merged, start the branch again from `develop`), with a pull request into `develop` open for the implementation; record the latest green `develop` run (its number, and the minutes `build` and `real-ide-tests` took) as the baseline · `specs/005-continuous-integration/quickstart.md`

---

## Phase 2: Foundational

None. `build.yml` on `develop` is the foundation, and it is kept: Zulu 25, `xvfb-run` with a 1920x1080x24 screen, the real-IDE tests in their own job, the `terminology` job untouched (research R1).

---

## Phase 3: User Story 1, every pull request is built and tested (P1) MVP

**Goal**: skip reasons and test reports are readable on every run, also a passing one (FR-004, FR-005). The rest of the story is on `develop` already and is only checked.

**Independent Test**: quickstart step 1.

Files: `.github/scripts/skipped-tests.py`, `build.yml`.

**Wave 1 — one file:**

- [ ] **T002** [US1] Run `./gradlew :freemind:test --tests '*FreeMindCompatibilityTest'` without `FREEMIND_HOME`, then `GITHUB_STEP_SUMMARY=<a temp file> python .github/scripts/skipped-tests.py`: it fails because the script does not exist yet · (no file)
- [ ] **T003** [US1] Write the script (research R6): read every `**/build/test-results/**/*.xml` below the working directory; for each `<testcase>` with a `<skipped>` child take the test as `<classname>.<name>` and the reason from the `message` attribute, without a leading exception class name; append to the file named by `GITHUB_STEP_SUMMARY` (standard output when it is not set) the heading "Skipped tests" and a table with the columns Test and Reason, or the sentence "No tests were skipped."; exit 0 whatever it finds, also when there are no result files. Standard library only · `.github/scripts/skipped-tests.py`

**⟶ Wait for Wave 1 to finish, then:**

- [ ] **T004** [US1] Repeat T002's command: the temp file holds one row, `etalii.adp.freemind.ui.FreeMindCompatibilityTest` with "FREEMIND_HOME is not set, so FreeMind 1.0.1's reader is not available" · (no file)

**Wave 2 — one file, in order:**

- [ ] **T005** [US1] In the `build` job: after the Gradle step add "Report the skipped tests", `if: ${{ !cancelled() }}`, running `python3 .github/scripts/skipped-tests.py`; change "Keep the test reports" from `if: failure()` to `if: ${{ !cancelled() }}` · `.github/workflows/build.yml`
- [ ] **T006** [US1] In the `real-ide-tests` job: the same two changes as T005, for the artifact `real-ide-test-reports`; add `ADP_IDEA_LICENSE: ${{ secrets.ADP_IDEA_LICENSE }}` to the Gradle step's `env` (research R9); "Keep the IDE logs" stays `if: failure()` · `.github/workflows/build.yml`

**⟶ Wait for Wave 2 to finish, then:**

- [ ] **T007** [US1] Push, and follow quickstart step 1.1 to 1.3 on the pull request's run: green; both jobs' summaries show the Skipped tests table with the FreeMind and Ultimate licence reasons; `test-reports` and `real-ide-test-reports` are offered. Record · `specs/005-continuous-integration/quickstart.md`
- [ ] **T008** [US1] Quickstart step 1.4: push a commit that breaks one headless test; the run is red at "Build, test and verify the plug-in", the log names the test, no plug-in is offered (FR-009); push again during a run and see the older run cancelled (FR-006); re-run a finished run from the Actions page without pushing (FR-007); remove the breaking commit. Record · `specs/005-continuous-integration/quickstart.md`

**Checkpoint**: User Story 1 is complete and checked by someone other than the author.

---

## Phase 4: User Story 2, download the plug-in built from a pull request (P1)

**Goal**: the run's download is the installable zip itself (FR-008).

**Independent Test**: quickstart step 2.

Files: `build.yml`.

- [ ] **T009** [US2] In the `build` job's "Offer the plug-in for download" step add `archive: false` and remove `name:` (the artifact is then named after the file, `etalii-adp-<version>.zip`); keep `path: build/distributions/*.zip` and `if-no-files-found: error` (research R7) · `.github/workflows/build.yml`
- [ ] **T010** [US2] Push; `gh run download <run> --pattern 'etalii-adp-*.zip'` gives a zip whose listing shows the plug-in's folder with `lib/`, not another zip. If the direct upload does not work, apply research R7's fallback here and in T012, T013 and T019, and say so in research.md. Record · `specs/005-continuous-integration/quickstart.md`
- [ ] **T011** [US2] Manual, Peter: quickstart step 2.2, install the downloaded zip from disk into a clean IntelliJ IDEA 2026.2 and open a FreeMind example map (SC-006). Record · `specs/005-continuous-integration/quickstart.md`

**Checkpoint**: a reviewer can try a pull request without building it.

---

## Phase 5: User Story 3, download the latest development build (P1)

**Goal**: one `development` pre-release on the Releases page, replaced by every change to `develop` that passes its checks, and the readme pointing there (FR-010 to FR-013, FR-020).

**Independent Test**: quickstart step 3 before the merge; "After the merge" step 1 on `develop`.

Files: `build.yml`, `README.md`.

**Wave 1 — independent (different files):**

- [ ] **T012** [US3] Add the job `development-build` (research R3, R4; the contract's "Development build"): `needs: [build, real-ide-tests]`; `if: github.ref == 'refs/heads/develop'`; `permissions: contents: write`; job `env`: `DEVELOPMENT_TAG: development`, `GH_TOKEN: ${{ github.token }}`, `GH_REPO: ${{ github.repository }}`. Steps, with no checkout: (1) `actions/download-artifact@v8` with `pattern: etalii-adp-*.zip` and `merge-multiple: true`; (2) if the release `$DEVELOPMENT_TAG` exists, `gh api repos/$GH_REPO/compare/$DEVELOPMENT_TAG...$GITHUB_SHA --jq .status`, and unless it is `ahead`, print a `::notice::` saying the development build is already at this commit or a newer one, and end the job successfully without publishing; (3) title `Development build <version> (<7-character sha>, <UTC yyyy-mm-dd>)`, with the version taken from the zip's file name, and notes holding the full commit with its link, the commit's subject, the UTC date, the link to this run, and "Every merge into develop replaces this build."; (4) when the release does not exist: `gh release create $DEVELOPMENT_TAG --target $GITHUB_SHA --prerelease --latest=false` with title, notes and the zip; otherwise, in this order: `gh release upload --clobber` the zip, delete every other asset of the release, `gh release edit` title, notes, `--prerelease`, and last move the tag with `gh api -X PATCH repos/$GH_REPO/git/refs/tags/$DEVELOPMENT_TAG -f sha=$GITHUB_SHA -F force=true` · `.github/workflows/build.yml`
- [ ] **T013** [P] [US3] Rewrite "Install from disk": step 1 becomes downloading `etalii-adp-<version>.zip` from the [Releases page](https://github.com/etalii-adp/etalii.adp.ide.intellij/releases), either the newest versioned release or the "Development build" pre-release, which is the plug-in from the current `develop` after it passed every check; say that the zip is installed as it is, not unzipped; mention that a pull request's Build run offers that pull request's plug-in for 90 days; building it yourself stays as the alternative, and the "Build" section is unchanged (FR-020) · `README.md`

**⟶ Wait for Wave 1 to finish, then, in order:**

- [ ] **T014** [US3] Add the temporary rehearsal commit (research R11): the job's `if` also accepts `refs/heads/features/005-continuous-integration` and `DEVELOPMENT_TAG` is `development-rehearsal`. The commit's message starts with "TEMPORARY" · `.github/workflows/build.yml`
- [ ] **T015** [US3] Quickstart steps 3.2 to 3.4: a manual run on the branch publishes the pre-release with one asset and the commit and date in its title and notes; a second run on the same commit ends with the notice and changes nothing; after an empty commit a third run replaces it, still with exactly one asset; after a commit that breaks one headless test a fourth run fails, `development-build` is skipped and the release still names the third run's commit (FR-012). Record · `specs/005-continuous-integration/quickstart.md`
- [ ] **T016** [US3] Clean up: `gh release delete development-rehearsal --cleanup-tag --yes`; remove the temporary commit, the empty commit and the breaking commit from the branch; confirm `gh release list` and `git ls-remote --tags origin` show nothing with "rehearsal", and that `build.yml` again reads `refs/heads/develop` and `development` only. Record · `.github/workflows/build.yml`

**Checkpoint**: the development build is proven on the branch; the real one appears with the merge.

---

## Phase 6: User Story 4, publish a versioned release (P2)

**Goal**: pushing a `v<version>` tag on a commit of `develop` publishes that version, and every wrong mark is refused with its reason (FR-014 to FR-018).

**Independent Test**: quickstart step 4 before the merge; "After the merge" steps 3 and 4 on `develop`.

Files: `build.yml`, `README.md`.

**Wave 1 — independent (different files):**

- [ ] **T017** [US4] Add `tags: ['v*']` to the workflow's `push` trigger. In the `build` job, directly after checkout, add the step "Check the version mark", `if: startsWith(github.ref, 'refs/tags/v')`, with `GH_TOKEN: ${{ github.token }}` in its `env` (research R5): read `pluginVersion` from `gradle.properties`; when `v<pluginVersion>` is not `$GITHUB_REF_NAME`, print `::error::` naming the tag and the plug-in's version; when `gh api repos/$GITHUB_REPOSITORY/compare/$GITHUB_SHA...develop --jq .status` is neither `ahead` nor `identical`, print `::error::` saying the marked commit is not on `develop`; evaluate both, then fail the step if either was printed · `.github/workflows/build.yml`
- [ ] **T018** [P] [US4] Add a "Releasing" section for maintainers: the version is `pluginVersion` in `gradle.properties`; to release, push the tag `v<that version>` on a commit of `develop` (`git tag v0.1.0 <commit>`, `git push origin v0.1.0`); the Build run checks that commit and publishes the release with generated notes; a mark that does not match the version, is not on `develop`, or whose checks fail publishes nothing; a published release is never replaced; right after a release, raise `pluginVersion` in a pull request so development builds carry the next version · `README.md`

**⟶ Wait for Wave 1 to finish, then, in order:**

- [ ] **T019** [US4] Add the job `release` (the contract's "Versioned release"): `needs: [build, real-ide-tests]`; `if: startsWith(github.ref, 'refs/tags/v')`; `permissions: contents: write`; `GH_TOKEN` and `GH_REPO` as in T012. Steps, with no checkout: download the plug-in as in T012; find the previous mark as the highest `v*` tag other than this one (`gh api repos/$GH_REPO/tags --paginate`, sorted with `sort -V`); `gh release create "$GITHUB_REF_NAME" --verify-tag --title "<version>" --generate-notes` with the zip, adding `--notes-start-tag <previous>` when there is one. No `--clobber`, no edit and no delete anywhere in this job (FR-018) · `.github/workflows/build.yml`
- [ ] **T020** [US4] Quickstart step 4: push `v0.0.0-rehearsal` on the branch's head; its run fails at "Check the version mark" with both errors (the version, and not on `develop`), `release` does not run and `gh release list` shows no new release; delete the tag on `origin` and locally. Record · `specs/005-continuous-integration/quickstart.md`

**Checkpoint**: every refusal is proven; the first real release is Peter's to make after the merge.

---

## Phase 7: Polish

Files: `build.yml`, `README.md`, the spec documents.

**Wave 1 — independent (different files):**

- [ ] **T021** Bring the workflow's header comment up to date: the triggers, that both test jobs report skipped tests and keep their reports, what `development-build` and `release` do and that they alone hold `contents: write`; replace the line "What else this repository's CI does ... is spec 005's to decide" · `.github/workflows/build.yml`
- [ ] **T022** [P] In the "Build" section, one paragraph on CI: every pull request and every change to `develop` is checked by the Build workflow; skipped tests and their reasons are in each job's summary; when a download from JetBrains fails, the step's log names the URL, and "Re-run failed jobs" repeats the job without a push (research R12) · `README.md`

**⟶ Wait for Wave 1 to finish, then:**

- [ ] **T023** Trust review (FR-019, research R10): in the final `build.yml`, the workflow's `permissions` is `contents: read`; only `development-build` and `release` declare `contents: write`; neither `if` can be true for a `pull_request` event; the event is never `pull_request_target`; no secret other than `ADP_IDEA_LICENSE` and the token is read; every `uses:` is an `actions/*` or `gradle/actions/*` action. Record · `specs/005-continuous-integration/quickstart.md`
- [ ] **T024** Compare `contracts/ci-interface.md` and `data-model.md` line by line with the final `build.yml` and correct whichever is wrong; rewrite the spec's "Where it stands" paragraph to say what is now done and what follows the merge · `specs/005-continuous-integration/continuous-integration.spec.md`
- [ ] **T025** Final validation: the pull request's last run is green in `build`, `real-ide-tests` and `terminology`, with no deprecation annotation from GitHub, and took under 60 minutes (SC-004); `./gradlew build` locally still produces `build/distributions/etalii-adp-<version>.zip` (quickstart step 5); every "before the merge" step of quickstart.md has its row in Results · `specs/005-continuous-integration/quickstart.md`

After the merge, for Peter, not tasks: quickstart.md's "After the merge" steps 1 to 4, and deciding when `develop` becomes `v0.1.0`. Adding the `ADP_IDEA_LICENSE` secret is optional and his choice.

---

## Dependencies & Execution Order

- T001 first.
- Story 1: T002, T003, T004, then T005 and T006 in order (one file), then T007, then T008.
- Story 2 (T009 to T011) after Story 1, because both edit the `build` job. T011 waits for Peter and blocks nothing but T025.
- Story 3: T012 after T009 (it downloads what T009 uploads); T013 beside it; then T014, T015, T016 in order.
- Story 4: T017 after T016 (so no rehearsal commit is in the way); T018 beside it; then T019, T020.
- Polish: T021 and T022 side by side, then T023, T024, T025.

## Parallel Opportunities

Only the `README.md` tasks: T013 beside T012, T018 beside T017, T022 beside T021. Everything else is one file or a run that needs the change before it.

## Implementation Strategy

Stories 1 and 2 are small and finish what `develop` started; they are the MVP and can be reviewed on the pull request's own runs. Story 3 is the ask ("available as a download in GitHub") and is proven by the rehearsal. Story 4 can follow in the same pull request or, if Peter prefers a smaller first merge, be split off after T016 together with T018 to T020.

## Requirement Coverage

| Requirement | Tasks |
|---|---|
| FR-001, FR-002, FR-003 | on `develop`; checked in T007 |
| FR-004, FR-005 | T002 to T007 |
| FR-006, FR-007, FR-009 | on `develop`; checked in T008 |
| FR-008 | T009, T010, T011 |
| FR-010, FR-011 | T012, T015 |
| FR-012 | T012 (`needs`), T015 |
| FR-013 | T012, T015 |
| FR-014, FR-016 | T017, T020 |
| FR-015, FR-017, FR-018 | T019; the published release is seen after the merge |
| FR-019 | T023 |
| FR-020 | T013, T018, T022, T025 |
| SC-004 | T001 (baseline), T025 |
| SC-006 | T011 for a run's download; the development build and the first release after the merge |
| SC-001, SC-002, SC-003, SC-005 | outcomes seen on `develop` after the merge (quickstart.md) |
