# Research: Continuous Integration and Plug-in Downloads

Decisions for [plan.md](plan.md). Each records what was chosen, why, and what was set aside. Facts about the repository were read on 2026-10-04 from `develop` at `d5ae4cd` and from the repository's Actions history.

## R1. What `develop` already does

**Finding.** `.github/workflows/build.yml` (etalii.adp spec 001, PRs #10 and #11, then the stabilising work of spec 003) already covers most of User Stories 1 and 2:

| Requirement | State on `develop` |
|---|---|
| FR-001, FR-002 | Done. Runs on every pull request into `develop` and every push to `develop`. |
| FR-003 | Done. The `build` job runs `./gradlew build -x integrationTest` (compile, headless tests, `verifyPlugin`, `verifyDependencyLicences`); the `real-ide-tests` job runs `./gradlew integrationTest`. Both decide the workflow's result since `9091e74`. |
| FR-004 | Partly. GitHub shows pass or fail per job and step, with the log. Test reports are kept only when a job fails, so skip reasons cannot be read on a passing run. |
| FR-005 | Partly. The tests skip with a reason (`assumeTrue`), but nothing shows it. |
| FR-006 | Done. `concurrency` cancels the older run of the same pull request. |
| FR-007 | Done. `workflow_dispatch`, and GitHub's "Re-run jobs". |
| FR-008, FR-009 | Done, with the plug-in wrapped in a second zip. The upload step runs only when the build step succeeded. |
| FR-010 to FR-018 | Not started. The repository has no releases and no tags. |
| FR-019 | Done for checks: the workflow's token is `contents: read` and the event is `pull_request`, which gives forks no secrets. Nothing publishes yet. |
| FR-020 | Not started. The readme still says "take a released zip". |

The spec's "Where it stands" paragraph was written while the real-IDE job could not fail the run; that changed on `develop` and the spec is brought up to date with this plan.

**Decision.** Extend `build.yml`; do not add a second checks workflow. Keep its decisions: Zulu rather than Temurin, `xvfb-run` with a full-size screen for both test jobs, Rider verified from its Maven archive, the real-IDE tests in their own job beside the build job, and the `terminology` job untouched.

## R2. Where publishing lives

**Decision.** Two new jobs in `build.yml`, `development-build` and `release`, each with `needs: [build, real-ide-tests]`.

**Rationale.** A job that needs both check jobs runs only when both passed, in the same run, so "published only after the checks pass" (FR-010, FR-012, FR-015) needs no extra mechanism, and the job publishes the very zip the `build` job uploaded. One workflow keeps one badge and one place to look.

**Alternatives considered.** A separate workflow triggered by `workflow_run`: it runs from the default branch's copy of the file, cannot be rehearsed on a feature branch, and has to find the other run's artifact. A reusable workflow called from a `release.yml`: two files and a second badge for the same steps.

## R3. The development build

**Decision.** One pre-release with the fixed tag `development`, updated in place by the `development-build` job with the `gh` CLI that hosted runners already have. The job runs when `github.ref` is `refs/heads/develop` (a push, or a manual run on `develop`). It:

1. downloads the plug-in the `build` job uploaded;
2. asks GitHub how the run's commit relates to the commit `development` points at, and stops with a notice when the commit is the same or older (R4);
3. creates the pre-release when it does not exist; otherwise uploads the zip over the existing asset, deletes assets with another name (the version changed), sets the title and notes, and moves the `development` tag to the commit last.

Title: `Development build <version> (<short sha>, <date>)`. Notes: the full commit with a link, its subject, the UTC date, a link to the check run, and one line saying that every merge into `develop` replaces this build. The asset is the bare `etalii-adp-<version>.zip`.

**Rationale.** Updating in place means the Releases page always offers a development build; the tag moves last, so a job that failed half-way is not skipped as "already published" when it is run again. `gh` needs no third-party action, which keeps a job with write access free of code the org does not control.

**Alternatives considered.** Delete and recreate the release each time: simpler, but there is a moment with no development build, and a failed create leaves none. A third-party release action: one more dependency holding a write token. A fixed asset name such as `etalii-adp-development.zip`: a stable download URL, but the spec asks for the bare `etalii-adp-<version>.zip` and the readme links to the release, not the file.

**The plug-in inside is the tested zip, unchanged.** Its version is the declared `pluginVersion`, so a development build and the later versioned release of the same number report the same version in the IDE. Stamping a development version would mean publishing a zip other than the tested one. The readme's release steps therefore raise `pluginVersion` right after a release, so development builds always carry the next version.

## R4. The latest commit wins (FR-013, SC-003)

**Decision.** Two guards. The workflow's existing `concurrency` group (`build-<ref>`, no cancelling for pushes) runs `develop`'s runs one after another, and GitHub keeps only the newest waiting run. The `development-build` job also compares its commit with the current `development` tag through GitHub's compare API (`compare/development...<sha>`) and publishes only when the result is `ahead`.

**Rationale.** Concurrency orders normal merges. The comparison covers what concurrency cannot: a maintainer re-running an old `develop` run (FR-007) must not put an older build back.

**Alternatives considered.** Publish only when the commit is `develop`'s head: when the head's own checks fail, an older commit that passed would never be published, against SC-003.

## R5. Versioned releases and the version mark

**Decision.** The version mark is a git tag `v<version>`, for example `v0.1.0`, pushed by the maintainer. `build.yml` also triggers on `push: tags: ['v*']`, so the tagged commit goes through the same `build` and `real-ide-tests` jobs. On a tag run:

- the `build` job starts with a step that refuses the mark, with a message saying why, when `v` plus `pluginVersion` from `gradle.properties` is not the tag (FR-016), or when the commit is not on `develop` (FR-014);
- the `release` job, which needs both check jobs, runs `gh release create <tag> --verify-tag --title <version> --generate-notes` with the bare `etalii-adp-<version>.zip` attached, and `--notes-start-tag` set to the previous `v*` tag when there is one.

**Rationale.** A tag is GitHub's own way to mark a commit and is one action (SC-005). Running the checks on the tag is the simplest proof that the marked commit passes (FR-015): no lookup of an earlier run. `--generate-notes` lists the pull requests merged since the start tag (FR-017); the start tag is given explicitly because the `development` tag would otherwise be taken as the previous release. `gh release create` fails when the release exists and git refuses a second tag of the same name, so a release is never replaced (FR-018) and an unchanged version is refused (edge case).

**Alternatives considered.** A bare `0.1.0` tag: the `v` prefix is the common convention and keeps `v*` from matching other tags. A `workflow_dispatch` input for the version: a form rather than a mark, and it lets the version be typed wrongly. Drafting the release in GitHub's UI: GitHub would create the release itself, without the checked zip. Reusing the `develop` run's artifact for the tagged commit: artifacts expire, and the lookup is more code than building again.

## R6. Skip reasons on a passing run (FR-004, FR-005)

**Finding.** Gradle 9.8 writes the reason into the JUnit XML. Verified locally on 2026-10-04: `freemind/build/test-results/test/TEST-etalii.adp.freemind.ui.FreeMindCompatibilityTest.xml` holds `<skipped message="org.junit.AssumptionViolatedException: FREEMIND_HOME is not set, so FreeMind 1.0.1's reader is not available" ...>`.

**Decision.** A small script, `.github/scripts/skipped-tests.py`, reads every `**/build/test-results/**/*.xml` and writes a "Skipped tests" table (test, reason) to the job summary, or "No tests were skipped". Both test jobs run it whenever the job was not cancelled, and both keep their test reports on every run, not only on failure. The script only reports; it never fails the job.

**Rationale.** The job summary is on the run's front page, readable without downloading anything, on green and red runs alike. Python is on the hosted runners and the `terminology` job already uses it.

**Alternatives considered.** A third-party test-report action: needs `checks: write` and code the org does not control. Printing skips with Gradle's `testLogging`: it prints the test's name but not the reason.

## R7. The run download (FR-008)

**Decision.** Upload the plug-in with `actions/upload-artifact@v7` and `archive: false`, so the download is the installable `etalii-adp-<version>.zip` itself. The publishing jobs fetch it with `actions/download-artifact@v8` by the pattern `etalii-adp-*.zip`. Retention stays GitHub's default of 90 days.

**Rationale.** A zip wrapped in a zip is not installable; the reviewer would have to know to unzip once and not twice. `archive` is an input of `upload-artifact@v7` (read from its `action.yml` on 2026-10-04); with `archive: false` the artifact is named after the file.

**Fallback.** If the direct upload does not behave as documented on the first run, keep the wrapped upload under the name `etalii.adp.ide.intellij-plugin` and say in the readme that the download has to be unzipped once.

## R8. Caching the IDEs the real-IDE tests download

**Finding.** Without that cache, the last twelve runs took 12 to 18 minutes of work (`real-ide-tests` about 11 minutes, `build` 12 to 16); two pull request runs took 56 minutes because a job waited for a runner. SC-004 allows 60. The download folder is about 30 GB locally; a repository's cache allowance is 10 GB.

**Decision.** Do not cache `ide-tests-home`. `setup-gradle` keeps caching Gradle's own downloads.

**Rationale.** The target is met, the folder does not fit, and nothing has to be invalidated (constitution V).

## R9. The Ultimate licence

**Decision.** The `real-ide-tests` job passes `ADP_IDEA_LICENSE: ${{ secrets.ADP_IDEA_LICENSE }}`. The repository has no such secret today, so the variable is empty, the tests treat it as absent and the licensed runs are skipped with their reason in the summary (R6). Adding the secret later is the maintainer's choice and needs no workflow change. Pull requests from forks never receive it.

## R10. Trust (FR-019)

**Decision.** The workflow keeps `permissions: contents: read`. Only `development-build` and `release` raise it, to `contents: write`, at job level, and neither can run for a pull request: one requires `refs/heads/develop`, the other a `v*` tag ref, and a pull request's ref is `refs/pull/<n>/merge`. Pushing to `develop` or pushing a tag needs write access to the repository. The event stays `pull_request`, never `pull_request_target`.

## R11. Proving it before the merge

**Finding.** The constitution lets a feature reach `develop` only when its tasks are done, but the development build publishes only from `develop` and a versioned release only from a tag on `develop`.

**Decision.** The development build's tag is a job-level variable, `DEVELOPMENT_TAG`. One temporary commit on the feature branch lets the job run there under the tag `development-rehearsal`; a manual run on the branch publishes it, a second manual run proves the "same or older commit" guard, a run with a broken test proves that a failing change leaves the build alone, and the rehearsal release, its tag and the temporary commit are then removed. The release's refusals (wrong version, commit not on `develop`) are proved by pushing a `v0.0.0-rehearsal` tag to the feature branch and deleting it. What only `develop` can show, the first real development build and the first versioned release, is in [quickstart.md](quickstart.md) under "After the merge" and is the maintainer's to confirm.

**Precedent.** Spec 003 stabilised the real-IDE tests on CI with temporary commits on its branch, removed before the merge (`9091e74`).

## R12. A JetBrains download that fails

**Decision.** No new mechanism. Gradle and the IDE Starter name the URL they could not fetch in the step's log, and "Re-run failed jobs" repeats the job without a push (FR-007). The readme says so.

**Alternatives considered.** Automatic retries around Gradle: they would also retry real failures and double the time to a red result.
