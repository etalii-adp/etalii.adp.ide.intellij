# Feature Specification: Continuous Integration and Plug-in Downloads

**Feature Branch**: `features/005-continuous-integration` (drafted on `claude/project-thread-8m4drq`)
**Created**: 2026-09-26
**Status**: Draft
**Input**: "Create a spec for the IntelliJ CI. It should make the plugin available as a download in GitHub."

## Context

The plug-in is built and tested only on a developer's own machine today. The constitution makes a change mergeable only when the headless build and the full test suite pass, but nothing checks that for a pull request: a reviewer has to take the author's word for it, and a cloud session cannot build the plug-in at all. The readme tells users to "take a released `etalii-adp-<version>.zip`", but no released zip exists; the only way to try ADP in an IDE is to clone the repository and build it.

This feature adds continuous integration on GitHub that builds, tests and verifies the plug-in for every pull request and every change to `develop`, and makes the installable plug-in available as a download on the repository's GitHub page, so anyone can install it from disk without building it.

Three roles appear below. A **contributor** is a person or agent who opens a pull request. A **maintainer** reviews and merges pull requests and decides when a version is released. A **user** installs the plug-in in their IDE.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Every pull request is built and tested (Priority: P1)

A contributor opens a pull request into `develop`. Without anyone doing anything, the plug-in is built from the pull request's head, every test the constitution requires is run, and the plug-in is verified against the IDEs it claims to support. The result appears on the pull request as a pass or a fail, and a failure shows which step failed and links to its output. When the contributor pushes again, the checks run again on the new head.

**Why this priority**: it is what makes the constitution's merge rule checkable by someone other than the author, and it is the only way a cloud session can learn whether its change builds.

**Independent Test**: open one pull request that changes nothing and one that breaks a test on purpose; the first shows a pass, the second a fail naming the broken test.

**Acceptance Scenarios**:

1. **Given** a pull request into `develop`, **When** it is opened or a new commit is pushed to it, **Then** the checks run on its latest commit and their result is shown on the pull request.
2. **Given** a change that breaks compilation, a test or plug-in verification, **When** the checks run, **Then** they fail and the failure names the step and links to the output that explains it.
3. **Given** a change that breaks nothing, **When** the checks run, **Then** they pass.
4. **Given** checks are running for a commit, **When** a newer commit is pushed to the same pull request, **Then** the older run no longer decides the pull request's result.

---

### User Story 2 - Download the plug-in built from a pull request (Priority: P1)

A maintainer reviewing a pull request wants to try the change in their own IDE. From the pull request's checks they download the installable plug-in that was built and tested for that commit, and install it from disk.

**Why this priority**: reviewing a visual designer means looking at it; downloading the tested build is faster and more trustworthy than checking out and building the branch.

**Independent Test**: from a passing pull request, download the plug-in, install it from disk into a clean IntelliJ IDEA and open a FreeMind example map.

**Acceptance Scenarios**:

1. **Given** checks have passed for a pull request, **When** the maintainer opens the checks, **Then** the installable plug-in for that commit can be downloaded.
2. **Given** the downloaded plug-in, **When** it is installed from disk as the readme describes, **Then** the IDE accepts it and the designers work.
3. **Given** checks failed at the build step, **When** the maintainer opens the checks, **Then** no plug-in is offered for download for that commit.

---

### User Story 3 - Download the latest development build (Priority: P1)

A user who wants to try ADP opens the repository on GitHub, goes to its Releases, and downloads the latest development build: the plug-in built from the current `develop`, after it passed every check. The readme links there. The build's name makes clear that it is a development build and which commit it came from.

**Why this priority**: this is the ask. It gives users a stable place to fetch the plug-in without building it, and it is refreshed by every merge without anyone doing anything.

**Independent Test**: merge a pull request into `develop`; once its checks pass, the development build on the Releases page is replaced by one built from the merge commit, and installs from disk.

**Acceptance Scenarios**:

1. **Given** a change is merged into `develop`, **When** its checks pass, **Then** the development build on the repository's Releases page is replaced by the plug-in built from that commit, within one check run.
2. **Given** a change merged into `develop` fails its checks, **When** a user looks at the Releases page, **Then** the previous development build is still offered, unchanged.
3. **Given** the development build, **When** a user looks at it, **Then** it is clearly marked as a pre-release, and its name or notes identify the commit and date it was built from.
4. **Given** the repository's readme, **When** a user follows its install instructions, **Then** they are pointed to the Releases page to download the plug-in.

---

### User Story 4 - Publish a versioned release (Priority: P2)

A maintainer decides that `develop` is ready to be a version, for example 0.1.0. They mark that commit with the version, and a release named after the version appears on the Releases page, with the plug-in built from that commit attached, its version inside the plug-in matching the release, and notes listing what changed since the previous version.

**Why this priority**: versioned releases are what users settle on and report bugs against, but development builds already make the plug-in downloadable, so this can follow.

**Independent Test**: mark a commit on `develop` as version 0.1.0; a release named 0.1.0 appears with a zip whose plug-in reports version 0.1.0 in the IDE's plug-in list.

**Acceptance Scenarios**:

1. **Given** a commit on `develop` that passed its checks, **When** the maintainer marks it as a version, **Then** a release for that version is published with the plug-in built from that commit attached.
2. **Given** the released plug-in, **When** it is installed, **Then** the IDE shows the same version as the release.
3. **Given** a version mark whose version does not match the version the plug-in declares, **When** the release would be published, **Then** it is not published and the failure says why.
4. **Given** a version mark on a commit whose checks fail, **When** the release would be published, **Then** it is not published.
5. **Given** a published release, **When** later changes are merged, **Then** the release and its download stay unchanged.

---

### Edge Cases

- Checks for the same pull request are started twice in quick succession: only the latest commit's result counts, and the earlier run may be stopped.
- A pull request comes from a fork: the checks still run, but they have no access to secrets such as licence keys, and nothing is published from them.
- Tests that need something CI does not have (the IntelliJ IDEA Ultimate licence key, a FreeMind installation) are skipped, and the skip is visible in the output rather than silent; they never turn a run red or green on their own.
- A download from JetBrains' servers fails for reasons outside the change: the failure says so in its output, so it is not mistaken for a broken change.
- Two changes are merged into `develop` close together: the development build ends as the one built from the later commit, never the earlier.
- The version in the plug-in is not changed between two versioned releases: the second release is refused, since the version already exists.
- Downloads attached to checks expire after a retention period; development builds and versioned releases do not.

## Requirements *(mandatory)*

### Functional Requirements

**Checks**

- **FR-001**: Every pull request into `develop` MUST be checked on its latest commit automatically, and on every new commit pushed to it.
- **FR-002**: Every change that reaches `develop` MUST be checked the same way.
- **FR-003**: The checks MUST build the plug-in, run the headless test suite and the real-IDE test suite, and verify the plug-in against the IDEs it declares support for, as the constitution's merge rule requires.
- **FR-004**: The result MUST be shown on the pull request as pass or fail. A failure MUST name the step that failed and link to its output, including test reports.
- **FR-005**: Tests that cannot run in CI because a secret or local installation is absent MUST be reported as skipped with the reason, not as passed.
- **FR-006**: A newer commit on the same pull request MUST supersede the result of an older one.
- **FR-007**: A maintainer MUST be able to run the checks again for a commit without pushing a change.

**Downloads from checks**

- **FR-008**: Each successful check run MUST offer the installable plug-in built for that commit as a download from the run, kept for a retention period.
- **FR-009**: A check run in which the plug-in did not build MUST NOT offer a plug-in download.

**Development build**

- **FR-010**: After a change to `develop` passes its checks, the repository's Releases page MUST offer the plug-in built from that commit as the development build, replacing the previous one.
- **FR-011**: The development build MUST be marked as a pre-release and MUST identify the commit and date it was built from, in its name or notes.
- **FR-012**: A change to `develop` that fails its checks MUST leave the existing development build unchanged.
- **FR-013**: When changes reach `develop` close together, the development build MUST end as the one built from the latest of them.

**Versioned releases**

- **FR-014**: A maintainer MUST be able to publish a versioned release by marking a commit on `develop` with its version, without other manual steps.
- **FR-015**: A versioned release MUST carry the plug-in built from the marked commit, and MUST be published only when that commit passes its checks.
- **FR-016**: The version inside the plug-in MUST equal the release's version; a mismatch MUST stop the release with a message saying so.
- **FR-017**: A versioned release MUST include notes listing the changes since the previous versioned release.
- **FR-018**: A published versioned release MUST NOT be replaced or changed by later builds.

**Trust and documentation**

- **FR-019**: Nothing MUST be published from a pull request's checks, and checks for pull requests from forks MUST NOT receive the repository's secrets.
- **FR-020**: The readme's install instructions MUST point to the Releases page for the development build and versioned releases, and its build instructions MUST remain valid for building locally.

### Key Entities

- **Check run**: one run of the checks for one commit, with its steps, their results, their output and test reports, and the plug-in download it produced.
- **Plug-in download**: the installable plug-in zip, as installed from disk, with the version and commit it was built from.
- **Development build**: the single pre-release on the Releases page holding the plug-in from the latest `develop` commit that passed its checks.
- **Versioned release**: a release named after a version, created when a maintainer marks a commit, holding that commit's plug-in and its release notes; never changed afterwards.
- **Version mark**: the marker a maintainer puts on a commit to ask for a versioned release of it.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of pull requests into `develop` opened after this feature lands show a check result before they are merged.
- **SC-002**: A user can go from the repository's front page to the plug-in installed in their IDE in under 3 minutes, without cloning or building anything.
- **SC-003**: The development build is never older than the latest `develop` commit that passed its checks, once that commit's checks finish.
- **SC-004**: A pull request's check result is shown within 60 minutes of its latest push under normal conditions.
- **SC-005**: A maintainer publishes a versioned release with one action on the repository, no local build involved.
- **SC-006**: Every downloadable plug-in, from checks, the development build or a versioned release, installs from disk into a clean IntelliJ IDEA of the supported release and opens a FreeMind example map.

## Assumptions

- "Available as a download in GitHub" means the repository's Releases page, which users can reach without an account, plus downloads attached to each check run for reviewers. Publishing to the JetBrains Marketplace is out of scope and can follow in its own specification.
- The CI is GitHub's own, attached to this repository, because the repository and its pull requests live on GitHub and the org uses no other CI service.
- There is no branch protection in the org (Peter declined the paid plan), so a failing check cannot block a merge; the checks inform the maintainer, who merges. This matches how every repository in the org already delivers.
- The version a release carries comes from the plug-in's declared version, which a maintainer raises in a pull request before marking the release; this feature does not bump versions automatically.
- Signing the plug-in is out of scope; the IDE installs unsigned plug-ins from disk. Signing comes with Marketplace publishing.
- The real-IDE tests that need an IntelliJ IDEA Ultimate licence run only when the repository has the licence key as a secret; without it they are skipped as FR-005 describes. Whether to add that secret is the maintainer's choice.
- The full check run downloads the IntelliJ Platform and several IDEs; runs are allowed to cache those downloads between runs, and SC-004's 60 minutes assumes that cache.
- The same CI approach is expected in the other ADP IDE repositories later; this specification covers only this repository.
