# Implementation Plan: FreeMind Mind Map Designer

**Branch**: `001-freemind-mindmap-designer` | **Date**: 2026-09-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-freemind-mindmap-designer/spec.md`

> **Scale note**: this is a greenfield build of about 45 files across four areas: the Tycho
> build and target platform, the shared ADP designer framework, the FreeMind supported file
> format, and the test bundles with vendored example maps. Watch two seams above all: the undo
> bridge between visual edits and the text editor's undo history (research R2), and the
> offset-exact XML scanner that makes minimal text edits possible (research R4). Every other
> part leans on those two.

## Summary

A Different Perspective (ADP) gets its first supported file format: a visual designer for
FreeMind `.mm` mind maps that is a real Eclipse editor. The editor has two pages over one
`IDocument`. The text page is the platform's own `TextEditor`, so saving, dirty state, revert,
external-change handling and undo come from the platform unchanged. The visual page is a GEF
Classic (Draw2d) viewer that never holds its own copy of the map. It re-reads the map from the
document text after every change, and every visual edit is a small `TextEdit` against that
text, run as one labelled operation on the workbench operation history. Byte-identical saves
and "change only what was edited" therefore come from the design itself, not from a careful
serializer.

The code splits into a format-agnostic framework bundle (`etalii.adp.core`) and a FreeMind
bundle (`etalii.adp.freemind`), so later supported file formats plug in without touching either.
The new stack choices are Java 21, Maven/Tycho 5 (pomless), a target platform pinned to the
Eclipse 2026-09 Simultaneous Release, and GEF Classic from that release train as the only
dependency beyond the platform.

## Technical Context

**Language/Version**: Java 21, the minimum for the Eclipse 2026-09 release.

**Primary Dependencies**: Eclipse Platform 2026-09 (`org.eclipse.ui.editors`,
`org.eclipse.ui.ide`, `org.eclipse.jface.text`, `org.eclipse.core.filebuffers`,
`org.eclipse.core.commands`), plus GEF Classic (`org.eclipse.gef`, `org.eclipse.draw2d`) from the
same release repository. Both are EPL-2.0.

**Storage**: the `.mm` file itself, through the platform's file buffers. No other state is
persisted.

**Testing**: JUnit 5 in Tycho test fragments, run by `tycho-surefire-plugin` in a UI harness
(`useUIHarness`, `useUIThread`). This is the "headless workbench" the constitution asks for: no
person watches the screen, and Linux CI runs it under Xvfb.

**Target Platform**: Eclipse IDE 2026-09 on Windows, Linux and macOS, recorded in
`releng/etalii.adp.target/etalii.adp.target.target`.

**Project Type**: Eclipse plug-in: two bundles, one feature, one p2 update site.

**Performance Goals**: a 1,000-node map opens and draws within 2 s. Adding, renaming, folding or
deleting a node in it shows the result within 0.1 s (SC-003).

**Constraints**: byte-identical save without edits (FR-009), edits limited to the edited content
(FR-010), no network access at runtime, EPL-2.0-compatible dependencies only.

**Scale/Scope**: maps of up to a few thousand nodes. One supported file format now, designed so
the next one reuses the framework bundle unchanged.

## Constitution Check

*GATE: checked before Phase 0 research and re-checked after Phase 1 design. Both pass.*

| Principle | Assessment | How the design meets it |
|---|---|---|
| I. Native Eclipse Citizenship | PASS | Registered through `org.eclipse.ui.editors` with a content-type binding. Edits are `IUndoableOperation`s on the workbench `IOperationHistory` under the text editor's undo context. Save, Save As, Revert, dirty state and external-change detection are the nested `TextEditor`'s own. Outline uses `IContentOutlinePage`; selection goes through the editor site's `ISelectionProvider`. |
| II. Text Is the Source of Truth | PASS | The document is the only model. The visual model is re-derived from it and discarded. Edits are range-exact `TextEdit`s, so untouched bytes are never rewritten. Files that fail to parse open on the text page with an explanation and are not touched. |
| III. One Framework, Many Designers | PASS | `etalii.adp.core` knows nothing about FreeMind. `etalii.adp.freemind` supplies only parsing, text edits, edit parts, figures and its contributions. A second format is a new bundle that depends on core alone. |
| IV. Test-First, Against Real Files | PASS | Round-trip tests run over vendored, permissively licensed real maps, with each licence beside its file. Registration and undo/redo tests run in the UI harness. Tasks put every test before its code. |
| V. Simplicity | PASS | One added dependency (GEF Classic, release train) for selection, drag and drop, zoom and keyboard navigation that FR-017, FR-022, FR-024 and FR-027 need now. No EMF model and no serializer. A small in-house scanner is used because no JDK or platform parser reports exact attribute offsets (R4). |
| Platform and technology constraints | PASS | Tycho headless build with a checked-in target definition. Java 21. EPL-2.0 dependencies from the release train. No network access. |

No violations, so there is no Complexity Tracking table.

## Project Structure

### Documentation (this feature)

```text
specs/001-freemind-mindmap-designer/
├── spec.md
├── plan.md               # this file
├── research.md           # Phase 0 decisions
├── data-model.md         # Phase 1: parsed model, text conventions, view state
└── contracts/
    ├── workbench-contributions.md   # extension IDs, commands, key bindings
    ├── designer-framework.md        # the API a supported file format codes against
    └── test-kit.md                  # the driver automated visual tests use (SC-007)
```

### Source Code (repository root)

```text
pom.xml                                   # root reactor: Tycho 5, pomless, target reference
.mvn/extensions.xml                       # tycho-build extension (pomless bundles)
.gitattributes                            # adds: *.mm -text (keep example bytes exact)
releng/
├── etalii.adp.target/etalii.adp.target.target   # 2026-09 release repo, JUnit 5
└── etalii.adp.site/category.xml                 # p2 update site
features/
└── etalii.adp.feature/feature.xml               # "A Different Perspective (ADP)"
bundles/
├── etalii.adp.core/                     # shared designer framework (no format knowledge)
│   ├── META-INF/MANIFEST.MF
│   └── src/etalii/adp/core/
│       ├── AdpDesignerEditor.java       # multi-page editor: visual page + TextEditor page
│       ├── AdpActionBarContributor.java # undo/redo/zoom global actions per page
│       ├── DocumentEditOperation.java   # labelled operation applying a TextEdit (R2)
│       ├── TextEditCommand.java         # GEF Command carrying a label + TextEdit
│       ├── OperationHistoryCommandStack.java  # routes GEF commands to the history
│       ├── FormatProblem.java           # parse failure: message + offset
│       └── ui/MessagePanel.java         # read-only banner / parse-problem panel
└── etalii.adp.freemind/                 # FreeMind supported file format
    ├── META-INF/MANIFEST.MF
    ├── plugin.xml                       # content type, editor, commands, bindings, menus, wizard
    └── src/etalii/adp/freemind/
        ├── model/                       # MindMap, MapNode, ArrowLink, NodeKey
        ├── parse/                       # XmlScanner, MindMapParser, RichText (HTML to text)
        ├── edit/                        # MindMapEdits (TextEdit builders), FreeMindConventions
        └── ui/                          # MindMapEditor, MindMapLayout, parts/, figures/,
                                         # handlers/, MindMapOutlinePage, NewMindMapWizard,
                                         # FreeMindIcons
tests/
├── etalii.adp.testing/                  # test kit bundle (DesignerDriver), see contracts/test-kit.md
├── etalii.adp.core.tests/               # fragment: operation/undo bridge, command stack
└── etalii.adp.freemind.tests/           # fragment: parser, edits, round trip, registration,
    ├── examples/                        #   undo/redo, visual view, outline, performance
    │   └── <map>.mm + <map>.LICENSE     # vendored real maps, permissive licences only
    └── src/...
```

**Structure Decision**: a standard Tycho layout with `bundles/`, `tests/`, `features/` and
`releng/`, and one bundle per supported file format. Test code lives in fragments of the bundle
under test. The one exception is the reusable test kit, which is a plain bundle so every
format's tests can import it.

## Design in one page

- **Opening.** `MindMapEditor` extends `AdpDesignerEditor<MindMap>`. The text page is a stock
  `TextEditor` on `TextFileDocumentProvider`, so two editors on one file share one file buffer,
  one document and one undo history. The visual page parses the document. On a `FormatProblem`
  it shows the problem panel with the message, line and column, and a "Show text" button, and
  it activates the text page. Nothing is written.
- **Viewing.** Edit parts use `NodeKey`s as their GEF models (R6), so a re-parse after each
  document change updates only the parts that changed and keeps selection and expansion.
  `MindMapLayout` places the root in the centre and first-level branches on their recorded side,
  honouring `HGAP`, `VGAP` and `VSHIFT`. Arrow links are Draw2d connections.
- **Editing.** Handlers and GEF edit policies ask `MindMapEdits` for a `TextEdit` against the
  current text. The editor runs it through `DocumentEditOperation` on the operation history. The
  document change triggers a re-parse, and the view updates. Undo and redo take the same path
  backwards, so there is exactly one route from text to view.
- **Folding.** The fold command is the persisted `FOLDED` edit (FR-023). Transient expansion,
  used when the Outline reveals a node or when the file is read-only, lives in view state and is
  never written (R7).
- **Read-only.** When the input is not modifiable, a banner explains why, and every editing
  handler is disabled through `isEnabled`.

## Risks carried into tasks

- The undo bridge (R2) is the least certain mechanism. The first core test proves it before any
  editing code is written.
- Sourcing real example maps with a permissive, non-share-alike licence is a task of its own
  (R9). FreeMind's own sample maps are GPL and cannot be vendored.
- The FreeMind 1.0.1 compatibility check (FR-012, SC-005) cannot run in CI, because FreeMind is
  GPL and cannot be a build dependency. It is an opt-in test that runs only when `FREEMIND_HOME`
  is set, plus a manual check before merge (R10).
