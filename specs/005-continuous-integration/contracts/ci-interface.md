# Contract: the CI's interface

What contributors, maintainers and users can rely on. Changing anything here is a change to the feature, not a refactoring.

## Workflow

`.github/workflows/build.yml`, named **Build**. It is the only workflow that checks or publishes.

| Trigger | Runs |
|---|---|
| `pull_request` into `develop` | `build`, `real-ide-tests`, `terminology` |
| `push` to `develop` | `build`, `real-ide-tests`, then `development-build` |
| `push` of a tag matching `v*` | `build` (with the version mark check first), `real-ide-tests`, then `release` |
| `workflow_dispatch` | `build`, `real-ide-tests`; on `develop` also `development-build` |

`concurrency`: group `build-<ref>`; an older run is cancelled only for pull requests.

## Jobs

| Job | Decides the result | Token | What it does |
|---|---|---|---|
| `build` | yes | `contents: read` | `./gradlew build -x integrationTest` under `xvfb-run`: compile, headless tests, `verifyPlugin`, `verifyDependencyLicences`. Offers the plug-in. |
| `real-ide-tests` | yes | `contents: read` | `./gradlew integrationTest` under `xvfb-run`. |
| `terminology` | yes | `contents: read` | Unchanged (etalii.adp spec 002). |
| `development-build` | yes | `contents: write` | `needs: [build, real-ide-tests]`; only on `refs/heads/develop`. |
| `release` | yes | `contents: write` | `needs: [build, real-ide-tests]`; only on `refs/tags/v*`. |

Both test jobs, on every run that was not cancelled: write the skipped tests and their reasons to the job summary, and keep their test reports.

## Downloads from a run

| Artifact | When | Content |
|---|---|---|
| `etalii-adp-<version>.zip` | the build step succeeded | the installable plug-in itself, not wrapped |
| `test-reports` | every `build` job that was not cancelled | `**/build/reports/` |
| `real-ide-test-reports` | every `real-ide-tests` job that was not cancelled | `**/build/reports/` |
| `real-ide-logs` | `real-ide-tests` failed | each IDE's log folder |

Retention: GitHub's default, 90 days.

## Job summary

A heading **Skipped tests**, then a table with the columns Test and Reason, one row per skipped test, or the sentence "No tests were skipped." Written by `.github/scripts/skipped-tests.py`, which exits 0 whatever it finds.

## Development build

| Item | Value |
|---|---|
| Tag | `development` (the job's `DEVELOPMENT_TAG`) |
| Title | `Development build <version> (<short sha>, <yyyy-mm-dd>)` |
| Flags | pre-release, not latest |
| Asset | one `etalii-adp-<version>.zip` |
| Replaced when | the run's commit is ahead of the commit the tag points at |
| Left alone when | a check job failed or was cancelled, or the commit is the same or older; the job then says so in a notice and succeeds |

## Versioned release

| Item | Value |
|---|---|
| Version mark | tag `v<version>`, for example `v0.1.0` |
| Refused when | `v` + `pluginVersion` is not the tag; the commit is not on `develop`; a check job fails; the release already exists |
| Refusal message | an `::error::` line naming the tag and the version it found, or that the commit is not on `develop` |
| Title | `<version>` |
| Notes | GitHub's generated notes since the previous `v*` tag |
| Asset | `etalii-adp-<version>.zip` |

## Secrets

| Secret | Used by | Without it |
|---|---|---|
| `ADP_IDEA_LICENSE` (optional) | `real-ide-tests` | the licensed runs are skipped, with the reason in the summary |
| `GITHUB_TOKEN` | the two publishing jobs | provided by GitHub |

Pull requests from forks receive no secrets and a read-only token; no job that publishes can run for a pull request.

## Readme

The readme's "Install from disk" section links to `https://github.com/etalii-adp/etalii.adp.ide.intellij/releases` and names the development build and versioned releases; its "Build" section stays valid for building locally; a "Releasing" section gives the maintainer's steps.
