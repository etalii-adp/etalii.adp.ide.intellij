# Implementation Plan: JetBrains IDE Support

**Branch**: `002-jetbrains-ide-support` | **Date**: 2026-09-24 | **Spec**: [jetbrains-ide-support.spec.md](jetbrains-ide-support.spec.md)

**Input**: Feature specification from `specs/002-jetbrains-ide-support/jetbrains-ide-support.spec.md`

> **Scale note**: this replaces the host of a working product. About 45 production files and
> 35 test files are carried over or rewritten, and the previous build (about 120 tracked files)
> is removed. Watch three seams above all: the reference results that must be recorded *before*
> anything is removed (research R10), the undo bridge from a non-text editor (R7), and the
> Structure view and Rider behaviour for a custom editor (R8, R12). Each gets a proving task
> early.

## Summary

ADP moves to the IntelliJ Platform and the previous host is removed completely. The FreeMind
designer becomes the preview side of the platform's `TextEditorWithPreview`, whose text side is
the platform's own `TextEditor` on the same `Document`: saving, modified state, reload after an
external change, Local History and read-only handling come from the platform, and switching to
text is the editor's standard layout toggle. An editor provider claims a `.mm` file only when its
content is a FreeMind map, so Objective-C++ `.mm` files are left alone.

The format layer (scanner, parser, rich text, edits, conventions) moves over unchanged apart from
two host types, so every visual edit is still a range-exact text change. It now runs as one named
`WriteCommandAction`, which is one step in the IDE's Undo. The visual view is a Swing canvas over
a ported layout, replacing GEF. Byte-for-byte parity with spec 001 is proved against reference
results recorded from the previous implementation before it is deleted.

Stack: Java 21, Gradle 9 with the IntelliJ Platform Gradle Plugin 2.19, IntelliJ Platform 2026.2
(`since-build` 262), depending only on `com.intellij.modules.platform`. Licence: Apache-2.0.

## Technical Context

**Language/Version**: Java 21 (JBR 21 runtime of IntelliJ Platform 2026.2). Gradle build scripts
in Kotlin DSL; no Kotlin in the plug-in.

**Primary Dependencies**: IntelliJ Platform 2026.2 (`com.intellij.modules.platform` only);
IntelliJ Platform Gradle Plugin 2.19; JUnit 5 (Jupiter and Vintage), the platform test framework,
and the Starter + Driver framework for integration tests. No third-party runtime libraries.

**Storage**: the `.mm` file itself, through the platform `Document` and `FileDocumentManager`.
Per-editor view state (zoom, selection) through `FileEditorState`, never written to the file.

**Testing**: format-layer JUnit 5 tests; headless-IDE `BasePlatformTestCase` tests driven by
`DesignerDriver`; a Starter + Driver integration suite in real IntelliJ IDEA, Rider, WebStorm and
PyCharm; Plugin Verifier against eight IntelliJ Platform products (research R9, R11).

**Target Platform**: every IDE built on IntelliJ Platform 2026.2, on Windows, Linux and macOS.

**Project Type**: IntelliJ Platform plug-in, multi-module Gradle build: `core`, `freemind`,
`testing`, composed into one plug-in zip.

**Performance Goals**: a 1,000-node map opens and draws within 2 s; add, rename, fold or delete
shows the result within 0.1 s (SC-004). The provider's content sniff reads at most 4 KB.

**Constraints**: byte-identical save without edits (FR-006); edits change only the edited content
(FR-007); output byte-identical to spec 001's reference results (FR-008); no runtime network
access (FR-021); Apache-2.0-compatible dependencies; no trace of the previous host (FR-017).

**Scale/Scope**: maps up to a few thousand nodes; one supported format; eight target IDEs.

## Constitution Check

*GATE: checked before Phase 0 research and re-checked after Phase 1 design (constitution
v2.0.0). Both pass.*

| Principle | Assessment | How the design meets it |
|---|---|---|
| I. Native IntelliJ Platform Citizenship | PASS | Registered through `fileEditorProvider` (with content sniffing) and `fileTypeDetector`. Text view, save, modified state, reload, read-only and Local History are the platform `TextEditor`'s via `TextEditorWithPreview`. Every edit is a named `WriteCommandAction` on the platform undo manager; the designer is a `DocumentReferenceProvider`. Structure view through `getStructureViewBuilder`, actions and shortcuts through `plugin.xml` and the keymap, themes through `JBColor`/`JBUI`. |
| II. Text Is the Source of Truth | PASS | The `Document` is the only model; the visual model is re-derived after each change. Edits are range-exact `TextChanges`. Trailing-space stripping is disabled for these files (R5). Unparseable files open as text with an explanation and are not touched. |
| III. One Framework, Many Designers | PASS | `core` has no format knowledge: provider, designer base, undo bridge, text changes, banners, zoom. `freemind` supplies sniffing, parsing, edits, canvas, actions and structure view. A second format is a new module depending on `core` only. |
| IV. Test-First, Against Real Files | PASS | Every spec 001 test is inventoried and ported before its code; round trips run over the vendored, permissively licensed maps; reference results come from the real previous implementation; registration and undo are tested in a headless IDE and in real IDEs. |
| V. Simplicity | PASS | No runtime dependency beyond the platform. The format layer is carried over rather than rewritten. A single Swing canvas instead of a component per node or a graph library. |
| Platform and technology constraints | PASS | One plug-in, `since-build` 262, platform module only (single host). Java 21. Gradle with the IntelliJ Platform Gradle Plugin, headless, running every test. Apache-2.0. No runtime network access. |

No violations, so there is no Complexity Tracking table.

## Project Structure

### Documentation (this feature)

```text
specs/002-jetbrains-ide-support/
├── jetbrains-ide-support.spec.md
├── plan.md               # this file
├── research.md           # Phase 0 decisions R1–R14
├── data-model.md         # parse model, text changes, reference results, view state
├── quickstart.md         # validation guide
├── contracts/
│   ├── plugin-contributions.md   # plugin.xml: IDs, editor, actions, shortcuts, structure view
│   ├── designer-framework.md     # the API a supported format codes against
│   └── test-kit.md               # DesignerDriver and the integration suite
└── tasks.md              # Phase 2 (/speckit-tasks)
```

### Source Code (repository root, after the feature)

```text
settings.gradle.kts                  # rootProject.name = "EtAlii.Adp.IntelliJ"; includes core, freemind, testing
build.gradle.kts                     # plug-in: platform 2026.2, composed modules, verifier, integrationTest, no-trace check
gradle.properties                    # version, platform version, since-build
gradle/wrapper/                      # Gradle 9 wrapper
LICENSE                              # Apache-2.0
README.md, CLAUDE.md                 # rewritten for the IntelliJ plug-in
src/main/resources/META-INF/
├── plugin.xml                       # contracts/plugin-contributions.md
└── pluginIcon.svg
src/integrationTest/java/etalii/adp/it/   # Starter + Driver suite (research R9)
core/                                # shared designer framework, no format knowledge
├── build.gradle.kts
└── src/
    ├── main/java/etalii/adp/core/
    │   ├── AdpEditorProvider.java           # accept + sniff, TextEditorWithPreview, policy
    │   ├── AdpDesignerEditor.java           # FileEditor + DocumentReferenceProvider base
    │   ├── AdpStripTrailingSpacesFilterFactory.java
    │   ├── AdpDataKeys.java
    │   ├── TextChange.java, TextChanges.java, Rgb.java, FormatProblem.java
    │   ├── ViewState.java                   # zoom, selection, transient expansion
    │   ├── actions/ZoomActions.java, SelectAllAction.java
    │   └── ui/ProblemPanel.java, ReadOnlyBanner.java
    └── test/java/etalii/adp/core/           # TextChanges, undo bridge, provider, banners
freemind/                            # FreeMind supported format
├── build.gradle.kts
├── src/main/java/etalii/adp/freemind/
│   ├── model/  parse/  edit/                # carried over (data-model.md); host-free
│   ├── FreeMindSniffer.java, FreeMindFileType.java, FreeMindFileTypeDetector.java
│   └── ui/                                  # MindMapEditorProvider, MindMapDesigner, MindMapCanvas,
│                                            # MindMapLayout, NodeView, NodePainter, FreeMindIcons,
│                                            # LinkOpener, MindMapStructureView, NewMindMapAction,
│                                            # actions/ (one class per action in the contract)
├── src/test/java/etalii/adp/freemind/       # ported format + platform tests, reference replay
└── testdata/
    ├── examples/<map>.mm + <map>.LICENSE    # moved with git mv from the previous test tree
    └── reference/                           # scenarios.json, <map>/<nn>-<action>.mm, spec001-test-inventory.md
testing/                             # test kit: DesignerDriver, DropPosition, Layout
└── src/main/java/etalii/adp/testing/
```

Removed by this feature: `pom.xml`, `.mvn/`, `bundles/`, `features/`, `releng/`, `tests/`, the
previous host's `.gitignore` entries, and spec 001's `plan.md`, `research.md`, `data-model.md`,
`contracts/` and `tasks.md` (research R13).

**Structure Decision**: a Gradle multi-module plug-in. The root project is the plug-in itself
(descriptor, packaging, integration tests); `core` and `freemind` are platform modules composed
into its jar, one module per supported format; `testing` is the reusable test kit. Test data sits
beside the format that owns it.

## Design in one page

- **Opening.** `MindMapEditorProvider.accept` checks the extension and sniffs the first 4 KB
  (R3). It returns a `TextEditorWithPreview` of the platform `TextEditor` and a `MindMapDesigner`
  over the same `Document`, preview-only by default (R2). The designer parses the document text;
  on a `FormatProblem` it shows the problem panel with line and column and a "Show Text" link, and
  the composite opens in text layout. Nothing is written.
- **Viewing.** `MindMapLayout` (ported) computes a `NodeView` box per visible node: root centred,
  first-level branches on their recorded side, `HGAP`/`VGAP`/`VSHIFT` honoured. `MindMapCanvas`
  paints boxes, connectors, arrow links, icons (Unicode glyphs, as in spec 001, because
  FreeMind's artwork is GPL), link and note indicators, and handles selection, keyboard focus,
  drag, zoom and in-place rename (R6). Colours come from the theme except where the file sets
  them; low-contrast file colours get a plate (R8).
- **Editing.** Actions read the designer from the `DataContext`, ask `MindMapEdits` for an
  `Edit` against the current text, and call `designer.execute(label, changes)`: one named
  `WriteCommandAction` (R4, R7). The document change triggers a coalesced re-parse and the view
  updates, keeping selection and expansion by `NodeKey`. Undo and redo take the same path back.
- **Fidelity.** Documents the provider accepts opt out of trailing-space stripping; the platform
  keeps the file's line separator and encoding (R5).
- **Integration.** Structure view via `getStructureViewBuilder` with two-way selection (R8);
  every action in the keymap (R8); New > FreeMind Mind Map (R8); read-only banner from the
  document's writability.
- **Parity.** Reference results and a test inventory are recorded from the previous
  implementation first (R10); the new tests replay and compare them byte for byte.
- **Removal.** Last: delete the previous build and code, spec 001's host-specific artifacts,
  relicense to Apache-2.0, rewrite README and CLAUDE.md, and add a build check that fails on any
  trace of the previous host (R13, R14).

## Implementation phases (input to `/speckit-tasks`)

1. **Record the baseline** (previous build still in place): scenario list, recorder, reference
   results, spec 001 test inventory. Commit.
2. **New build skeleton**: Gradle wrapper, modules, `plugin.xml` with the provider only,
   `runIde` works, `./gradlew build` green alongside the old tree.
3. **Proving tests** (test first): undo bridge from the designer (R7), Structure view both ways
   (R8), Rider integration test opening a map (R12), content sniffing incl. a non-FreeMind `.mm`.
4. **Format layer**: port model/parse/edit with `Rgb` and `TextChanges`; port their tests;
   purity check; reference-result replay green.
5. **User Story 1 (P1)**: provider, designer base, canvas, layout, node details, problem panel,
   themes; ported view tests.
6. **User Story 2 (P2)**: actions, keymap, context menu, drag, rename, fold, undo labels, save
   lifecycle, external change, read-only; ported edit and lifecycle tests.
7. **User Story 3 (P3)**: New file action, Structure view; ported tests.
8. **User Story 4 (P3) and removal**: integration suite across IDEs, Plugin Verifier matrix,
   inventory fully checked off, then remove the previous host, relicense, rewrite docs, no-trace
   check. Manual steps last: SC-001 installs, SC-006 walkthrough, FreeMind 1.0.1 check, and the
   maintainer renaming the repository folder and remote to `EtAlii.Adp.IntelliJ`.

## Risks carried into tasks

- **Undo from a non-text editor** (R7) and **Structure view sync for a custom editor** (R8) are
  documented only for text editors; both are proved by the first platform tests.
- **Rider split mode** (R12) is unconfirmed for 2026.2; the Rider integration test runs in phase
  3, with a frontend content module as the fallback.
- **Reference recording depends on the previous build still running** (R10). Phase 1 must
  finish, and its output be committed, before phase 8 removes anything.
- **Spec wording**: FR-001 and SC-001 name "IntelliJ IDEA Community" and "Ultimate" as separate
  products; since 2025.3 they are one distribution. This plan verifies the unified IDE without and
  with an Ultimate subscription (R1). A `/speckit-clarify` pass can update the spec's wording.
- **Keyboard conflicts**: Tab, Enter, Space and Delete are bound only while the canvas has focus
  (contracts/plugin-contributions.md); a test presses each in a text editor and checks that the
  designer's action does not run.
