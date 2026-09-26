# EtAlii.Adp.IntelliJ Constitution

## Core Principles

### I. Native IntelliJ Platform Citizenship (NON-NEGOTIABLE)

Every visual designer MUST be a real editor of the IntelliJ Platform and MUST use the platform's
own mechanism wherever the platform provides one, never a parallel one:

- Registration MUST go through the platform's file editor and file type registration for the
  file extensions (and, where applicable, file content) the designer handles, so that choosing
  an editor, default-editor selection and file type associations behave exactly as for
  built-in editors.
- Every user-visible change MUST be an undoable command on the platform's undo manager, so
  Edit > Undo/Redo, their keyboard shortcuts and Local History work unchanged.
- Modified state, saving, closing, reloading after an external change and read-only handling
  MUST behave as they do in the platform's text editor, through the platform's document and
  virtual file system.
- Selection, the Structure view, actions, keymap settings, find and themes (light, dark, font
  scale) MUST use the standard platform services when a designer offers the corresponding
  capability.

Rationale: users adopt a designer only if it feels like part of their IDE. Every re-implemented
platform mechanism is a place where it behaves differently, and a maintenance burden.

### II. The Text File Is the Source of Truth

The designers edit text-based files; the file's text remains the single authoritative model.

- Opening and saving a file without edits MUST leave it byte-identical.
- An edit MUST change only the part of the file it concerns. Content, attributes, comments,
  ordering and formatting the designer does not understand MUST be preserved.
- Each designer MUST offer a text view of the same file, sharing one document and one undo
  history with the visual view, so users can switch freely and never lose work.
- A file the designer cannot fully interpret MUST still open (falling back to the text view
  with a clear explanation), never be silently rewritten or refused.

Rationale: these files live in version control and are shared with other tools. A designer that
reformats or drops content produces diffs nobody asked for and destroys trust.

### III. One Framework, Many Designers

Designer infrastructure (editor lifecycle, text/visual synchronisation, undo integration,
registration) MUST be shared. Each file format MUST be a separate, self-contained module that
supplies only what is specific to that format: reading, writing, its visual model and its
visual representation.

- Adding a new format MUST NOT require changing the shared infrastructure or another format.
- A format module MUST NOT depend on another format module.

Rationale: the goal is many designers; the second one must cost a fraction of the first.

### IV. Test-First, Against Real Files

- Tests MUST be written before the behaviour they cover, and MUST be seen failing first.
- Every format MUST have round-trip tests (principle II) against real, published example files,
  not only hand-written samples. Example data MUST carry a permissive licence, vendored beside
  it; share-alike licences are refused.
- Editor registration and undo/redo behaviour (principle I) MUST be covered by automated tests
  running in a headless IDE instance.

Rationale: the failures that matter most here (lost content, broken undo, wrong editor) are
invisible in a quick manual try and obvious to users within a day.

### V. Simplicity

Start with the smallest designer that is genuinely useful, and grow it by specification.
Features, abstractions and dependencies MUST be justified by a current requirement, not an
anticipated one. When the platform already offers a capability, use it rather than adding a
library.

Rationale: a small designer that honours principles I and II beats a large one that does not.

## Platform and Technology Constraints

- The deliverable is one installable IntelliJ Platform plug-in targeting the current IntelliJ
  Platform release at planning time, with its compatible build range declared in the plug-in
  descriptor. Older releases are supported only when a specification says so.
- Single host: the IntelliJ Platform is ADP's only host. The plug-in MUST depend only on the
  platform's common modules, not on a language- or product-specific one, so that it runs in
  every IDE built on the platform (IntelliJ IDEA, Rider, WebStorm, PyCharm and the rest).
  Adding another host requires amending this constitution first.
- The JVM language level follows the minimum required by that platform release.
- The build MUST run headlessly from the command line (Gradle with the IntelliJ Platform Gradle
  Plugin) and produce the same result as the IDE, including running all tests.
- ADP is licensed under Apache-2.0. Third-party dependencies MUST be Apache-2.0-compatible and
  SHOULD be ones the IntelliJ Platform already bundles.
- No network access at runtime is required or performed by any designer.

## Development Workflow

- Work follows GitHub Spec Kit: constitution, specify, (clarify), plan, tasks, implement.
  Specifications state *what* and *why* and stay free of implementation choices; plans state
  *how*.
- Each feature is developed on its own branch, `features/<number>-<name>` after its
  specification, in its own worktree. The one exception is `claude/<name>`, which Claude's
  cloud sessions are handed by their harness.
- A feature reaches `develop`, the integration branch, only when all its tasks are done, and
  only through a pull request merged with a merge commit. A feature branch is never merged
  locally into `develop`, and nothing is pushed to `develop` directly. When the pull request
  is merged or closed, the branch is deleted locally and on `origin`, and the worktree removed.
- Every plan MUST include a Constitution Check against these principles; any deviation MUST be
  recorded with its justification in the plan's complexity-tracking section.
- A change is mergeable only when the headless build and the full test suite pass.

## Governance

This constitution supersedes other practices in this repository. Amendments are made through
`/speckit-constitution`, recorded in version control, and versioned semantically: MAJOR for
removing or redefining a principle, MINOR for adding a principle or materially expanding
guidance, PATCH for clarifications. Reviews of plans and changes MUST verify compliance with
the principles above; runtime guidance for agents lives in `CLAUDE.md`.

**Version**: 2.1.0 | **Ratified**: 2026-09-24 | **Last Amended**: 2026-09-26

## Quality above everything else.

- Any compiler warnings need to be solved. Not worked around but interpreted and adequately fixed. People that focus on building compilers and analytical tools will for sure have wider understanding of what good coding practices are. Take especially attention of the information provied by tools from Jetbrains. 
