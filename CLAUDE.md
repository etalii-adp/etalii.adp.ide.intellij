# EtAlii.Adp.IntelliJ

An IntelliJ Platform plug-in that adds visual designers for text-based files, for every IntelliJ Platform IDE (IntelliJ IDEA, Rider, WebStorm, PyCharm and the rest). Each designer is a proper IDE editor: registered for its file type, on the IDE's undo/redo stack, with the IDE's modified state and save behaviour, and interoperable with the IDE's text editor on the same document. The first designer is for FreeMind mind maps (`.mm`).

## How work is done here: spec-driven development (GitHub Spec Kit)

Every change starts as a specification. Use the Spec Kit skills in `.claude/skills/` in order:

1. `/speckit-constitution` — project principles, in `.specify/memory/constitution.md`. Read it before any other step; plans are checked against it.
2. `/speckit-specify` — a feature spec under `specs/NNN-feature-name/`, on its own `NNN-feature-name` branch (the `git` extension creates it).
3. `/speckit-clarify` — optional, resolves `[NEEDS CLARIFICATION]` markers before planning.
4. `/speckit-plan` — technical plan, research, data model and contracts.
5. `/speckit-tasks` — ordered, testable tasks.
6. `/speckit-analyze` — optional cross-artifact consistency check.
7. `/speckit-implement` — execute the tasks.

Specs say *what* and *why*; plans say *how*. Do not put implementation choices in a spec.

## Conventions

- The build is Gradle with the IntelliJ Platform Gradle Plugin: `./gradlew build` runs everything, `./gradlew test` the headless tests, `./gradlew integrationTest` the real-IDE tests, `./gradlew runIde` a sandbox IDE. Modules: `core` (framework), `freemind` (format), `testing` (test kit).
- One feature per branch, named `features/<number>-<name>` (Spec Kit's `branch_prefix` is set to `features`). The one exception is `claude/<name>`, which Claude's cloud sessions are handed by their harness.
- A feature branch is never merged locally into `develop`. When its tasks are done, push the branch from the worktree it was built in to `origin` and open a pull request into `develop`; nothing reaches `develop` except through a pull request. When the pull request is merged or closed, delete the branch locally and on `origin`, and remove the worktree.
- End commit messages written by an agent with a `Co-Authored-By:` trailer naming the model.
- Shell scripts for Spec Kit are the PowerShell variants (`.specify/scripts/powershell/`).
- When writing markdown files do not split lines to ensure a maximum line length is honored.
