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

1. **The first development build.** The merge's own run publishes the pre-release `development`. Install its zip as in step 2 above (SC-002, SC-006).
2. **A failing change leaves it alone.** The next time a run on `develop` fails, the development build still names the earlier commit.
3. **The first versioned release.** When `develop` is ready: `git tag v0.1.0 <commit on develop>` and `git push origin v0.1.0`. When the run is green, a release "0.1.0" appears with `etalii-adp-0.1.0.zip` and notes; the IDE's plug-in list shows 0.1.0. Then raise `pluginVersion` in a pull request, so development builds carry the next version.
4. **A second mark of the same version** is refused by git (the tag exists); re-running the tag's run fails at "release already exists".

## Results

| Date | Step | Outcome |
|---|---|---|
| | | |
