# EtAlii.Adp.Eclipse

An Eclipse plug-in that adds visual designers for text-based files. Each designer is a
proper Eclipse editor: registered for its file extension, backed by the workbench undo/redo
stack, dirty-state and save lifecycle, and interoperable with the platform's text editor.
The first designer is for FreeMind mind maps (`.mm`).

## How work is done here: spec-driven development (GitHub Spec Kit)

Every change starts as a specification. Use the Spec Kit skills in `.claude/skills/` in order:

1. `/speckit-constitution` — project principles, in `.specify/memory/constitution.md`. Read it
   before any other step; plans are checked against it.
2. `/speckit-specify` — a feature spec under `specs/NNN-feature-name/spec.md`, on its own
   `NNN-feature-name` branch (the `git` extension creates it).
3. `/speckit-clarify` — optional, resolves `[NEEDS CLARIFICATION]` markers before planning.
4. `/speckit-plan` — technical plan, research, data model and contracts.
5. `/speckit-tasks` — ordered, testable tasks.
6. `/speckit-analyze` — optional cross-artifact consistency check.
7. `/speckit-implement` — execute the tasks.

Specs say *what* and *why*; plans say *how*. Do not put implementation choices in a spec.

## Conventions

- One feature per branch; merge back to `main` when its tasks are done.
- End commit messages written by an agent with a `Co-Authored-By:` trailer naming the model.
- Shell scripts for Spec Kit are the PowerShell variants (`.specify/scripts/powershell/`).
- When writing markdown files do not split lines to ensure a maximum line length is honored.
