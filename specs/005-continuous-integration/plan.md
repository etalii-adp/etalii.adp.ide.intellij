# Implementation Plan: Continuous Integration and Plug-in Downloads

**Branch**: `features/005-continuous-integration` | **Date**: 2026-10-04 | **Spec**: [continuous-integration.spec.md](continuous-integration.spec.md)

## Summary

Most of the checking half is already on `develop`: the Build workflow from etalii.adp spec 001 builds, tests and verifies every pull request and every change to `develop`, with the real-IDE tests deciding the result, and offers the plug-in from each run. This plan extends that one workflow rather than adding another. It makes skip reasons readable on passing runs, offers the plug-in as the installable zip itself, and adds two publishing jobs that need both check jobs: one keeps a single `development` pre-release on the Releases page in step with `develop`, the other publishes a versioned release when a maintainer pushes a `v<version>` tag. The readme then points to the Releases page. Publishing uses the `gh` CLI on the runner; no third-party action is added, and the plug-in and its Gradle build do not change.

## Technical Context

**Language/Version**: GitHub Actions workflow YAML; Bash in workflow steps; Python 3 for one reporting script. The plug-in stays Java 25 with Gradle 9.8.

**Primary Dependencies**: `actions/checkout@v7`, `actions/setup-java@v6` (Zulu 25), `gradle/actions/setup-gradle@v6`, `actions/upload-artifact@v7`, all in use today; `actions/download-artifact@v8`, new; the `gh` CLI preinstalled on hosted runners.

**Storage**: GitHub's own: run artifacts (90 days), releases and tags.

**Testing**: the existing `./gradlew build -x integrationTest` and `./gradlew integrationTest` are what the workflow runs. The workflow itself is validated by the runs in [quickstart.md](quickstart.md), including a rehearsal of the development build on the feature branch (research R11).

**Target Platform**: GitHub-hosted `ubuntu-latest` runners, for the public repository etalii-adp/etalii.adp.ide.intellij.

**Project Type**: CI configuration and documentation for an IntelliJ Platform plug-in.

**Performance Goals**: SC-004, a result within 60 minutes of a push. Today's runs take 12 to 18 minutes of work.

**Constraints**: no branch protection in the org, so checks inform and do not block; fork pull requests get no secrets and publish nothing; only GitHub's own actions and Gradle's; a published versioned release never changes.

**Scale/Scope**: one workflow file, one script, the readme, the spec.

## Constitution Check

| Principle | Assessment |
|---|---|
| I. Native IntelliJ Platform citizenship | PASS. No tool or plug-in code changes. |
| II. The text file is the source of truth | PASS. Not touched. |
| III. One framework, many tools | PASS. No module changes. |
| IV. Test-first, against real files | PASS, with a note. No behaviour of the plug-in is added, so no plug-in test is written. Each workflow change has its expected outcome written in quickstart.md before the change, and is seen in a real run; the refusals (a wrong version mark, an older commit) are run on purpose. |
| V. Simplicity | PASS. One workflow extended, `gh` instead of release actions, no cache added for the real-IDE downloads (research R8), no automatic version bumping. |
| Platform constraints | PASS. The headless Gradle build is what CI runs; nothing in the build changes. No runtime network access is added to any tool. |
| Workflow | PASS. Work is on `features/005-continuous-integration` in its own worktree and reaches `develop` through a pull request with a merge commit. What only `develop` can show is listed in quickstart.md under "After the merge", not as tasks (research R11). |
| Merge rule ("the headless build and the full test suite pass") | PASS. This feature is what lets someone other than the author check it. With no branch protection the rule stays the maintainer's to apply. |
| Quality above everything else | PASS. No compiler involved; the workflow must run without deprecation annotations from GitHub. |

Re-checked after Phase 1: unchanged. No deviations, so no complexity tracking.

## Project Structure

### Documentation (this feature)

```text
specs/005-continuous-integration/
├── continuous-integration.spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── ci-interface.md
├── checklists/
│   └── requirements.md
└── tasks.md                 # from /speckit-tasks
```

### Source Code (repository root)

```text
.github/
├── workflows/
│   └── build.yml            # extended: tag trigger, version mark check, reports and skip summary on every run,
│                            # unwrapped plug-in upload, licence pass-through, development-build and release jobs
└── scripts/
    └── skipped-tests.py     # new: skipped tests and their reasons, from the JUnit XML to the job summary
README.md                    # install from the Releases page; a Releasing section; what a failed download looks like
```

**Structure Decision**: everything lives in the one existing workflow, one new script beside it, and the readme. `build.gradle.kts`, `gradle.properties` and the modules are not changed.

## Approach

1. **Skip reasons and reports** (R6; FR-004, FR-005). Add `skipped-tests.py`. In both test jobs, run it and keep the test reports whenever the job was not cancelled. Pass `ADP_IDEA_LICENSE` from the secret of that name to the real-IDE tests (R9).
2. **The run download** (R7; FR-008, FR-009). Upload the plug-in with `archive: false`.
3. **The development build** (R3, R4; FR-010 to FR-013). Add the `development-build` job: needs both check jobs, runs on `refs/heads/develop`, `contents: write`, downloads the plug-in, compares its commit with the `development` tag, and creates or updates the pre-release, moving the tag last.
4. **Versioned releases** (R5; FR-014 to FR-018). Add the `v*` tag trigger, the version mark check as the `build` job's first step on a tag run, and the `release` job: needs both check jobs, `contents: write`, `gh release create` with generated notes from the previous `v*` tag.
5. **Trust** (R10; FR-019). Check that the workflow's default stays `contents: read`, that only the two publishing jobs raise it, and that neither condition can hold for a pull request.
6. **Readme and comments** (FR-020). Rewrite "Install from disk" around the Releases page, add "Releasing", keep "Build", and bring the workflow's header comment up to date.
7. **Validate** with quickstart.md: the pull request's own run, the rehearsed development build, the refused version mark.

## Risks

- **`archive: false`** is recent. If the direct upload misbehaves, research R7 names the fallback, and the publishing jobs' download step changes with it.
- **The rehearsal publishes a real pre-release** for a few minutes on a public repository. Its title and tag say "rehearsal", and it is deleted in the same task.
- **A development build and the later release of the same number look alike in the IDE** (research R3). The readme's release steps raise the version right after a release.
- **Generated notes list pull requests, not commits.** Changes that reached `develop` without a pull request would be missing, but the workflow rule forbids those.
- **Runner queues.** Two recent runs waited long enough for a runner to take 56 minutes in all, close to SC-004's 60. Nothing in this plan adds to the work; the two publishing jobs take under a minute.
