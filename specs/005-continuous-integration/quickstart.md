# Quickstart: validating the CI and the downloads

How to prove the feature works. Names and values are in [contracts/ci-interface.md](contracts/ci-interface.md); the reasons are in [research.md](research.md). `gh` is the GitHub CLI, signed in with write access to the repository.

## Before the merge, on the feature branch's pull request

### 1. Checks and skip reasons (User Story 1)

1. Open the pull request into `develop` and wait for the Build run.
2. The run is green, and the `build` and `real-ide-tests` jobs each show a **Skipped tests** table in their summary. Expected rows: `FreeMindCompatibilityTest` with "FREEMIND_HOME is not set", and the licensed runs of `OpenMapIntegrationTest` and `EditUndoIntegrationTest` with "No Ultimate licence in ADP_IDEA_LICENSE".
3. The run offers `test-reports` and `real-ide-test-reports` although nothing failed.
4. Push a commit that breaks one headless test on purpose. The run is red, the failing step is "Build, test and verify the plug-in", its log names the test, and no plug-in is offered. Push again while a run is going: the older run is cancelled. Remove the commit.

### 2. The plug-in from a pull request (User Story 2)

1. From the green run, download `etalii-adp-<version>.zip`. It is the plug-in itself: it holds a folder with `lib/`, not another zip.
2. In a clean IntelliJ IDEA 2026.2, **Settings > Plugins > gear > Install Plugin from Disk...**, pick the zip, restart, and open a map from `freemind/testdata/examples/`: it opens in the mind map.

### 3. The development build, rehearsed (User Story 3)

1. Add the temporary rehearsal commit (research R11): the `development-build` job also runs for this branch, with `DEVELOPMENT_TAG: development-rehearsal`.
2. `gh workflow run build.yml --ref features/005-continuous-integration`. When the run is green, the Releases page shows a pre-release "Development build <version> (<sha>, <date>)" with one `etalii-adp-<version>.zip`, notes naming the commit and date, and the tag `development-rehearsal` on the branch's head.
3. Run the same command again without a new commit. The `development-build` job succeeds with the notice that the build is already published, and the release is unchanged.
4. Push an empty commit and run again: the release now names the new commit, still with exactly one asset.
5. Push a commit that breaks one headless test and run again: the run fails, the `development-build` job is skipped, and the release still names the commit of step 4.
6. Clean up: `gh release delete development-rehearsal --cleanup-tag --yes`, and remove the temporary, empty and breaking commits from the branch.

### 4. A refused version mark (User Story 4)

1. `git tag v0.0.0-rehearsal` on the feature branch's head and `git push origin v0.0.0-rehearsal`.
2. The tag's run fails in the `build` job's first step with two errors, one naming the tag and the plug-in's version, one saying the commit is not on `develop`; the `release` job does not run and no release appears.
3. Clean up: `git push origin :refs/tags/v0.0.0-rehearsal` and `git tag -d v0.0.0-rehearsal`.

### 5. The readme (FR-020)

Follow the readme's "Install from disk" as a user would: the link leads to the Releases page. Follow "Build" locally: `./gradlew build` still produces `build/distributions/etalii-adp-<version>.zip`.

## After the merge, on `develop`

These can only be seen once the feature is on `develop`; they are the maintainer's to confirm and are not tasks.

1. **The first development build.** The merge's own run publishes the pre-release `development`. Install its zip as in step 2 above (SC-002, SC-006). Re-run that run from the Actions page: `development-build` ends with the notice that the build is already at this commit and changes nothing (step 3.3). The next merge replaces the build, still with one asset (step 3.4).
2. **A failing change leaves it alone.** The next time a run on `develop` fails, the development build still names the earlier commit.
3. **The first versioned release.** When `develop` is ready: `git tag v0.1.0 <commit on develop>` and `git push origin v0.1.0`. When the run is green, a release "0.1.0" appears with `etalii-adp-0.1.0.zip` and notes; the IDE's plug-in list shows 0.1.0. Then raise `pluginVersion` in a pull request, so development builds carry the next version.
4. **A second mark of the same version** is refused by git (the tag exists); re-running the tag's run fails at "release already exists".

## Results

| Date | Step | Outcome |
|---|---|---|
| 2026-10-04 | T001 baseline | Latest green develop run before the work: #57 (ef112e3), build 17 min, real-ide-tests 11 min, 17 min in all. Work on `claude/project-thread-k2tmp0` (the cloud harness's branch name, allowed by CLAUDE.md) instead of `features/005-continuous-integration`; PR #26. |
| 2026-10-04 | T002/T004 script | Run locally against a JUnit XML shaped like the one research R6 recorded (the IntelliJ Platform cannot be fetched in the cloud container): one row, the FreeMind reason without the exception class; "No tests were skipped." with no reports; exit 0 both times. Seen on CI in 1.2. |
| 2026-10-04 | 1.1 to 1.3 | PR #26 run 37210379580 on 166cc37: green (build, real-ide-tests, terminology); "Report the skipped tests" and "Keep the test reports" ran in both jobs; `test-reports` and `real-ide-test-reports` offered although nothing failed; development-build and release skipped. 15 min in all. |
| 2026-10-04 | 2.1 (T010) | The same run offers one artifact named `etalii-adp-0.1.0.zip` (538,587 bytes), not `etalii.adp.ide.intellij-plugin`: `archive: false` works. Its listing could not be read from the cloud container (artifact storage is outside its network allowlist); Peter's install (T011) confirms it. |
| 2026-10-04 | 1.2 (log) | PR run 37211334935 on 6dde42c, `build` log: the Skipped tests table lists `FreeMindCompatibilityTest.freeMindReadsWhatTheToolSaved` ("FREEMIND_HOME is not set, so FreeMind 1.0.1's reader is not available") and `SaveLifecycleTest.localHistoryRecordsTheChange` (Local History records no revisions in the test IDE). The script now also prints the table to the log, because the job summary cannot be read through the API. |
| 2026-10-04 | 3.2, first try | Manual run 37211334480 on 6dde42c: `build` red in `core`'s `ToolLeakTest.closedToolsAreNotReachable` (a leak held by the daemon's restart task), which passed in the PR run on the same commit; `development-build` was skipped, so nothing was published (FR-012 seen by accident). With `:core:test` failed, Gradle ran no later test task, and the table said "No tests were skipped". Failed jobs re-run once. |
| 2026-10-04 | 1.2 (real-IDE) | PR run 37211334935 `real-ide-tests` log: the table lists the licensed runs of `EditUndoIntegrationTest` and `OpenMapIntegrationTest` ("No Ultimate licence in ADP_IDEA_LICENSE, so the licensed run is skipped"). The job itself was red in `EditUndoIntegrationTest.addChildNodeThenUndoRestoresTheOriginalBytes` ("target component is not showing", the known intermittent undo failure); `real-ide-test-reports` and `real-ide-logs` were both offered. |
| 2026-10-04 | 3.2, re-run | The re-run's `build` passed and `development-build` ran, but found no zip: `download-artifact@v8` unpacks a zip artifact unless `skip-decompress` is set. Fixed in 2aa1046 (both publishing jobs); nothing was published. |
| 2026-10-04 | 3.2 | Manual run 37213103302 on 2aa1046: green, `development-build` created the pre-release "Development build 0.1.0 (2aa1046, 2026-10-04)" with the one asset `etalii-adp-0.1.0.zip` (538,587 bytes; its listing is `etalii-adp/lib/EtAlii.Adp.IntelliJ-0.1.0.jar`, the plug-in itself, and the same digest as the run's artifact, which settles 2.1); notes name the full commit, its subject, the date and the run; tag `development-rehearsal` on 2aa1046. |
| 2026-10-04 | 3.5 (FR-012) | Manual run 37214139845 on 2aa1046: `build` red in `core`'s `ToolboxTest.listsExactlyTheDeclaredEntriesInToolboxOrder` (expected the four entries, got none), a test this feature does not touch; `development-build` was skipped and the release still named 2aa1046 from the earlier run. |
| 2026-10-04 | 3.3 | Not seen before the merge: the run meant to show it (37214139845) failed in `core`, and the rehearsal had to be cleaned up before this branch could move on. Seen after the merge instead, by re-running the `develop` run that published `development` (After the merge, step 1). |
| 2026-10-04 | 3.4 | Not run: PR #26 was merged with the rehearsal still on it, before the empty-commit step. The replace path is the same code as 3.2's create path plus `upload --clobber`, `edit` and the tag move; the first two merges into `develop` show it (After the merge, step 1). |
| 2026-10-04 | 3.6 | Manual run 37215284181 with `rehearsal_cleanup` deleted the `development-rehearsal` release and its tag; `releases` and `git ls-remote --tags origin` are both empty. The workflow reads `refs/heads/develop` and `development` only again (61b5265). |
| 2026-10-04 | 4 | The cloud container cannot push tags (its git proxy allows only its own branch), so the "Check the version mark" step was run locally against the real compare API with `GITHUB_REF_NAME`/`GITHUB_SHA` set: `v0.0.0-rehearsal` at 61b5265 (not on develop) gives both errors and exit 1; `v0.1.0` at 61b5265 gives only "not on develop"; `v0.0.0-rehearsal` at c9cbe39 only the version error; `v0.1.0` at c9cbe39 passes. A real tag push is still to be seen (After the merge, step 3). |
| 2026-10-04 | T023 trust | In the final `build.yml`: the workflow's `permissions` is `contents: read`; only `development-build` (`if: github.ref == 'refs/heads/develop'`) and `release` (`if: startsWith(github.ref, 'refs/tags/v')`) declare `contents: write`, and neither ref can be a pull request's `refs/pull/<n>/merge`; the event is `pull_request`, never `pull_request_target`; the only secret read is `ADP_IDEA_LICENSE`, besides `github.token`; every `uses:` is `actions/*` or `gradle/actions/*`. actionlint reports nothing. |
| 2026-10-04 | T025 | The follow-up PR's run is the final check (recorded in that PR). `./gradlew build` was not run locally: the cloud container cannot fetch the IntelliJ Platform; every CI `build` job above produced `build/distributions/etalii-adp-0.1.0.zip`. |
