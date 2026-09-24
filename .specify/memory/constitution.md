# EtAlii.Adp.Eclipse Constitution

## Core Principles

### I. Native Eclipse Citizenship (NON-NEGOTIABLE)

Every visual designer MUST be a real Eclipse editor and MUST use the platform's own mechanism
wherever the platform provides one, never a parallel one:

- Registration MUST go through the workbench editor registry for the file extensions (and,
  where applicable, content types) the designer handles, so that *Open With*, default-editor
  selection, and editor-per-extension preferences behave exactly as for built-in editors.
- Every user-visible change MUST be an undoable operation on the workbench operation history,
  so Edit > Undo/Redo, their keyboard shortcuts, and the undo history limit work unchanged.
- Dirty state, Save, Save As, Revert, close-with-unsaved-changes prompts and external-change
  detection MUST behave as they do in the platform's text editor.
- Selection, Outline, Properties, Problems and Find/Replace integration MUST use the standard
  workbench views and adapters when a designer offers the corresponding capability.

Rationale: users adopt a designer only if it feels like part of Eclipse. Every re-implemented
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
  running in a headless workbench.

Rationale: the failures that matter most here (lost content, broken undo, wrong editor) are
invisible in a quick manual try and obvious to users within a day.

### V. Simplicity

Start with the smallest designer that is genuinely useful, and grow it by specification.
Features, abstractions and dependencies MUST be justified by a current requirement, not an
anticipated one. When the platform already offers a capability, use it rather than adding a
library.

Rationale: a small designer that honours principles I and II beats a large one that does not.

## Platform and Technology Constraints

- The deliverable is an installable Eclipse plug-in (feature + update site) targeting the
  current Eclipse Simultaneous Release at planning time, recorded in a checked-in target
  platform definition. Older releases are supported only when a specification says so.
- Java version follows the minimum required by that Eclipse release.
- The build MUST run headlessly from the command line (Maven/Tycho) and produce the same
  result as the IDE, including running all tests.
- Third-party dependencies MUST be EPL-2.0-compatible and SHOULD come from the Eclipse release
  train or Eclipse Orbit.
- No network access at runtime is required or performed by any designer.

## Development Workflow

- Work follows GitHub Spec Kit: constitution, specify, (clarify), plan, tasks, implement.
  Specifications state *what* and *why* and stay free of implementation choices; plans state
  *how*.
- Each feature is developed on its own branch named after its specification and merged into
  `main` only when all its tasks are done.
- Every plan MUST include a Constitution Check against these principles; any deviation MUST be
  recorded with its justification in the plan's complexity-tracking section.
- A change is mergeable only when the headless build and the full test suite pass.

## Governance

This constitution supersedes other practices in this repository. Amendments are made through
`/speckit-constitution`, recorded in version control, and versioned semantically: MAJOR for
removing or redefining a principle, MINOR for adding a principle or materially expanding
guidance, PATCH for clarifications. Reviews of plans and changes MUST verify compliance with
the principles above; runtime guidance for agents lives in `CLAUDE.md`.

**Version**: 1.0.0 | **Ratified**: 2026-09-24 | **Last Amended**: 2026-09-24
