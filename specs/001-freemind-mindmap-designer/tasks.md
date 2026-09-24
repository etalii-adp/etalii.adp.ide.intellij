# Tasks: FreeMind Mind Map Designer

**Input**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/)

> **Scale note**: 87 tasks over about 75 files in five areas: the Tycho build, the core designer
> framework, the FreeMind text layer (scanner, parser, edits), the FreeMind visual page, and the
> test bundles with vendored example maps. Two things decide whether the rest holds: the undo
> bridge (T014, T025, research R2) is gated before any editor code, and the offset-exact scanner
> (T018, T029, research R4) is what every edit is built on.

**Tests**: required. Constitution principle IV says tests come first and are seen failing. Every
phase lists its tests before its implementation. Run them and record the failure before starting
the phase's implementation waves.

**Format**: `- [ ] **T###** [P?] [US#] Description · path`. `[P]` means independent within its
wave. `[US#]` maps to a user story in the spec.

**Path roots used below**:

- `core/` = `bundles/etalii.adp.core/src/etalii/adp/core/`
- `fm/` = `bundles/etalii.adp.freemind/src/etalii/adp/freemind/`
- `core-tests/` = `tests/etalii.adp.core.tests/src/etalii/adp/core/`
- `fm-tests/` = `tests/etalii.adp.freemind.tests/src/etalii/adp/freemind/`

**Two ownership decisions**, so each file has one owner phase:

- `plugin.xml` is Phase 2. It declares the whole [workbench contract](contracts/workbench-contributions.md)
  at once, including classes later phases create (handlers, wizard, outline adapter factory).
  Until those phases land, the platform logs a missing-class error only when such a command is
  invoked.
- The Outline reaches the editor through an adapter factory (US4), not a hook in `MindMapEditor`,
  so US4 adds files and edits none. The contracts were updated to match.

---

## Phase 1: Setup

Files: `pom.xml`, `.mvn/extensions.xml`, `.gitattributes`, `releng/**`, `features/**`, every
`META-INF/MANIFEST.MF` and `build.properties`, `tests/etalii.adp.freemind.tests/examples/**`.

**Wave 1 — independent (different files):**

- [x] **T001** [P] Tycho 5 pomless build extension (`tycho-build`) · `.mvn/extensions.xml`
- [x] **T002** [P] Root reactor: Tycho 5, Java 21, modules `bundles/*`, `tests/*`, `features/*`, `releng/*`, target platform reference, `tycho-surefire-plugin` with `useUIHarness`, `useUIThread` and JUnit 5 · `pom.xml`
- [x] **T003** [P] Add `*.mm -text` so example bytes survive Windows checkouts (R9) · `.gitattributes`
- [x] **T004** [P] Target platform pinned to the Eclipse 2026-09 release repository: Platform, `org.eclipse.ui.editors`, `org.eclipse.ui.ide`, `org.eclipse.jface.text`, `org.eclipse.core.filebuffers`, GEF Classic (`org.eclipse.gef`, `org.eclipse.draw2d`), JUnit 5 · `releng/etalii.adp.target/etalii.adp.target.target`
- [x] **T005** [P] p2 update site with category "A Different Perspective (ADP)" · `releng/etalii.adp.site/category.xml`
- [x] **T006** [P] Feature "A Different Perspective (ADP)" including `etalii.adp.core` and `etalii.adp.freemind`, EPL-2.0 · `features/etalii.adp.feature/feature.xml`, `features/etalii.adp.feature/build.properties`
- [x] **T007** [P] Core bundle manifest: exports only `etalii.adp.core`, requires the platform bundles and GEF, `Bundle-RequiredExecutionEnvironment: JavaSE-21` · `bundles/etalii.adp.core/META-INF/MANIFEST.MF`, `bundles/etalii.adp.core/build.properties`
- [x] **T008** [P] FreeMind bundle manifest (singleton, requires `etalii.adp.core` and GEF; `plugin.xml` in `bin.includes`) · `bundles/etalii.adp.freemind/META-INF/MANIFEST.MF`, `bundles/etalii.adp.freemind/build.properties`
- [x] **T009** [P] Test kit bundle manifest (plain bundle, requires `etalii.adp.core`, `org.eclipse.ui.ide`, `org.eclipse.core.resources`, GEF) · `tests/etalii.adp.testing/META-INF/MANIFEST.MF`, `tests/etalii.adp.testing/build.properties`
- [x] **T010** [P] Core test fragment manifest (host `etalii.adp.core`, requires `etalii.adp.testing` and JUnit 5; `fragment.xml` in `bin.includes`) · `tests/etalii.adp.core.tests/META-INF/MANIFEST.MF`, `tests/etalii.adp.core.tests/build.properties`
- [x] **T011** [P] FreeMind test fragment manifest (host `etalii.adp.freemind`, requires `etalii.adp.testing` and JUnit 5; `examples/` in `bin.includes`) · `tests/etalii.adp.freemind.tests/META-INF/MANIFEST.MF`, `tests/etalii.adp.freemind.tests/build.properties`
- [x] **T012** [P] Source and vendor at least five published real `.mm` maps under CC0, CC-BY, MIT or Apache-2.0, never share-alike and never FreeMind's GPL samples. Together they cover a 0.7.1-era file, a 1.0.1-era file, rich-content nodes and notes, arrow links, icons, non-ASCII text as numeric references, a Freeplane file and a large map. Each `<map>.mm` gets a `<map>.LICENSE` with the licence text and source URL (R9, SC-001) · `tests/etalii.adp.freemind.tests/examples/`

**⟶ Wait for Wave 1 to finish, then:**

- [x] **T013** Run `mvn -B verify` and confirm the target platform resolves and the empty reactor builds, so later failures are code, not build setup · `pom.xml`

---

## Phase 2: Foundational

Blocks every story. Delivers the format-agnostic framework, the test kit, the FreeMind text
layer (model, parser, edits) and the declarative contributions.

Files: `core/**`, `tests/etalii.adp.testing/**`, `fm/model/**`, `fm/parse/**`, `fm/edit/**`,
`bundles/etalii.adp.freemind/plugin.xml`, `core-tests/**`, `tests/etalii.adp.core.tests/fragment.xml`,
`fm-tests/MindMapAsserts.java`, `fm-tests/parse/**`, `fm-tests/edit/**`.

### Tests (write first, see them fail)

**Wave 1 — independent (different files):**

- [x] **T014** [P] Undo bridge test, the plan's first proof (R2): a `DocumentEditOperation` run through `TriggeredOperations` on a document with a connected `IDocumentUndoManager` gives one history entry with its label, undoes in one step, redoes, and after undo the document's modification stamp is back to the original · `core-tests/DocumentEditOperationTest.java`
- [x] **T015** [P] `OperationHistoryCommandStack` routes a `TextEditCommand` to its executor, refuses other commands, and its own undo and redo stacks stay empty · `core-tests/OperationHistoryCommandStackTest.java`
- [x] **T016** [P] Minimal test designer that proves core is format-agnostic: one node per line, a line `!` raises `FormatProblem`, registered for `*.adptest` in the fragment · `core-tests/LineListEditor.java`, `tests/etalii.adp.core.tests/fragment.xml`
- [x] **T017** [P] Test helpers `example(name)`, `key(id)`, `keyPath(...)`, `generatedMap(nodeCount)` · `fm-tests/MindMapAsserts.java`
- [x] **T018** [P] `XmlScanner` reports exact ranges for start tags, element ends, attribute names and values (both quote styles), comments, CDATA and PIs; rescanning each example reproduces every range's text · `fm-tests/parse/XmlScannerTest.java`
- [x] **T019** [P] `RichText` drops tags, turns block tags and `<br>` into line breaks, and decodes named and numeric entities (FR-014) · `fm-tests/parse/RichTextTest.java`
- [x] **T020** [P] `FreeMindConventions`: escaping writes `&amp; &lt; &gt; &quot;` and numeric references for non-ASCII; new IDs are `ID_<positive int>` and unique in the map; indent unit and line separator detection (FR-011) · `fm-tests/edit/FreeMindConventionsTest.java`

**⟶ Wait for Wave 1 to finish, then:**

**Wave 2 — independent (different files):**

- [x] **T021** [P] Framework behaviour through `LineListEditor` and `DesignerDriver`: text page is a `TextEditor` sharing the document; save, dirty and revert delegate to it; a document change re-parses once per event-loop turn; a `FormatProblem` shows the problem panel with line and column, activates the text page, and leaves the file unmodified; a read-only input shows the banner and `isEditable()` is false; `execute(label, edit)` lands one labelled undo entry and is a no-op when not editable (FR-005, FR-006, FR-007, FR-008) · `core-tests/AdpDesignerEditorTest.java`
- [x] **T022** [P] `MindMapParser`: every example parses; fields match data-model (text, rich, side, folded, icons, colours, font, link, note, gaps, arrow links); unknown content is not a field; `FormatProblem` for malformed XML, a DOCTYPE, a non-`map` root, and zero or several top-level nodes; `NodeKey` is the `ID` or the index path · `fm-tests/parse/MindMapParserTest.java`
- [x] **T023** [P] `MindMapEdits`, one test per catalogue entry, applied to example text: the result differs from the input only in the edited range; labels match the catalogue; add child expands a self-closing parent; delete removes arrow links targeting the subtree; moves to and from the first level fix `POSITION`; fold inserts or removes `FOLDED`; renaming a rich node replaces `richcontent` with `TEXT`; edited nodes get a new `MODIFIED`; written element and attribute forms match the 1.0.1 examples (FR-010, FR-011, FR-012, FR-021, FR-023) · `fm-tests/edit/MindMapEditsTest.java`

### Implementation

**⟶ Wait for the tests to be written and seen failing, then:**

**Wave 3 — independent (different files):**

- [x] **T024** [P] `FormatProblem(message, offset)`, a checked exception · `core/FormatProblem.java`
- [x] **T025** [P] `DocumentEditOperation`: `AbstractOperation` under the document undo context; `execute` applies the `TextEdit` inside `beginCompoundChange`/`endCompoundChange`; bad location or malformed tree returns an error status and leaves the document unchanged (R2) · `core/DocumentEditOperation.java`
- [x] **T026** [P] `TextEditCommand(label, Supplier<TextEdit>)` extending GEF `Command` · `core/TextEditCommand.java`
- [x] **T027** [P] `MessagePanel`: read-only banner and parse-problem panel with message, line, column and a "Show text" button · `core/ui/MessagePanel.java`
- [x] **T028** [P] Immutable parse model: `MindMap`, `MapNode`, `NodeKey`, `NodeRanges`, `ArrowLink`, `Side`, `FontSpec`, `Range` (data-model.md) · `fm/model/`
- [x] **T029** [P] `XmlScanner`, about 300 lines, recording exact ranges for every construct (R4) · `fm/parse/XmlScanner.java`
- [x] **T030** [P] `RichText` HTML-to-text conversion (R8) · `fm/parse/RichText.java`
- [x] **T031** [P] All contributions from the workbench contract: content type with `XMLRootElementContentDescriber2` on `map`; editor bound by content type only, `default="true"`, contributor `AdpActionBarContributor`; context `etalii.adp.freemind.context`; the nine commands with handlers and key bindings; context-menu entries on `popup:etalii.adp.freemind.editor.context` in table order with separators; New wizard category and wizard; `org.eclipse.core.runtime.adapters` factory `MindMapOutlineAdapterFactory` for `MindMapEditor` → `IContentOutlinePage` (FR-001, FR-002, FR-003, FR-024, FR-025, FR-026) · `bundles/etalii.adp.freemind/plugin.xml`

**⟶ Gate: T014 passes before Wave 4.** If it does not, apply the R2 fallback in `core/DocumentEditOperation.java` (trigger's own undo and redo return OK, the absorbed document changes do the work) and rerun T014.

**Wave 4 — independent (different files):**

- [x] **T032** [P] `OperationHistoryCommandStack(BiConsumer<String, TextEdit> executor)`: routes `TextEditCommand`s, refuses others, keeps no undo stack · `core/OperationHistoryCommandStack.java`
- [x] **T033** [P] `MindMapParser`: SAX validation pass with secure processing, DOCTYPE disallowed and external entities off, then the scanner pass that builds `MindMap` with ranges and `nodesByKey` (R4, FR-007) · `fm/parse/MindMapParser.java`
- [x] **T034** [P] `FreeMindConventions`: escaping, numeric references, ID drawing, timestamps from one clock read, indent and separator detection (R5) · `fm/edit/FreeMindConventions.java`

**⟶ Wait for Wave 4 to finish, then:**

**Wave 5 — independent (different files):**

- [x] **T035** [P] `AdpDesignerEditor<M>` per the framework contract: visual page (GEF `ScrollingGraphicalViewer`, `ScalableFreeformRootEditPart`, edit domain with `OperationHistoryCommandStack`, `GraphicalViewerKeyHandler`, context menu registered as `<editor id>.context`), nested `TextEditor` on `TextFileDocumentProvider`, save/dirty/revert delegation, coalesced re-parse on document change, problem panel and text page on `FormatProblem`, read-only banner, `execute(label, edit)` through `TriggeredOperations`, context activation while the visual page has focus, selection provider switching (FR-004 to FR-008, FR-027) · `core/AdpDesignerEditor.java`
- [x] **T036** [P] `MindMapEdits`: add child, add sibling, rename (plain and rich), delete (with arrow links), move up and down, re-parent, fold and unfold, each returning a `TextEdit` and its label (R5, data-model edit catalogue) · `fm/edit/MindMapEdits.java`

**⟶ Wait for Wave 5 to finish, then:**

**Wave 6 — independent (different files):**

- [x] **T037** [P] `AdpActionBarContributor`: `UndoActionHandler`/`RedoActionHandler` for the document undo context and zoom and select-all global actions on the visual page; the text editor's actions on the text page (FR-004, FR-024) · `core/AdpActionBarContributor.java`
- [x] **T038** [P] `DesignerDriver` with `Page` and `DropPosition`, exactly as the test-kit contract (SC-007) · `tests/etalii.adp.testing/src/etalii/adp/testing/`

**Checkpoint**: T014 to T023 pass. The framework runs a format end to end (`LineListEditor`), and
the FreeMind text layer parses and edits every example. No FreeMind editor exists yet.

---

## Phase 3: User Story 1 — Open and view a mind map (P1) 🎯 MVP

**Goal**: double-clicking a FreeMind `.mm` opens the designer, which draws the map as FreeMind
does, lets the user fold, pan, zoom and select, and never changes an unedited file.

**Independent Test**: open every example by double-click; the designer is the chosen editor, the
tree matches, and saving without edits is byte-identical.

Files: `fm/ui/MindMapEditor.java`, `fm/ui/ViewState.java`, `fm/ui/MindMapLayout.java`,
`fm/ui/FreeMindIcons.java`, `fm/ui/LinkOpener.java`, `fm/ui/figures/NodeFigure.java`,
`fm/ui/parts/**`, `fm/ui/handlers/NodeHandler.java`, `fm/ui/handlers/ToggleFoldHandler.java`.
Drag-to-move lives here because GEF ties drag gestures to the edit parts. It builds its command
from `MindMapEdits` (Phase 2); US2 adds the keyboard moves.

### Tests (write first, see them fail)

**Wave 1 — independent (different files):**

- [x] **T039** [P] [US1] Registration: a FreeMind `.mm` opens with `etalii.adp.freemind.editor` by default; *Open With* lists it and the Text Editor; an Objective-C++ `.mm` is not claimed and opens with the editor that otherwise applies; the text editor can be made default (FR-001, FR-002, FR-003, US1-AS1) · `fm-tests/ui/RegistrationTest.java`
- [x] **T040** [P] [US1] Round trip over every example: open, save without edits, bytes identical; also after closing without saving (FR-009, SC-001, US1-AS5) · `fm-tests/ui/RoundTripTest.java`
- [x] **T041** [P] [US1] Layout: root centred; first-level branches on their recorded side, automatic when absent; `HGAP`, `VGAP`, `VSHIFT` honoured; arrow links drawn as connections, missing destinations not drawn (FR-013, FR-015, US1-AS2) · `fm-tests/ui/LayoutTest.java`
- [x] **T042** [P] [US1] Node details from `figureOf`: plain and rich text as readable text, icons (known emoji and unknown-name badge), text and background colour, font face, size, bold and italic, link indicator that opens its target, note indicator with the note text as tooltip (FR-014, FR-018) · `fm-tests/ui/NodeDetailsTest.java`
- [x] **T043** [P] [US1] Folding: branches recorded as folded show collapsed; toggling is a labelled, undoable `FOLDED` edit; on a read-only file toggling is view-only and nothing is dirty (FR-016, FR-023, FR-008, US1-AS3, R7) · `fm-tests/ui/FoldTest.java`
- [x] **T044** [P] [US1] Viewer: zoom in and out through the platform commands, pan by scrolling, single, multi and marquee selection, keyboard navigation between nodes, selection published to the site as `NodeKey`-adaptable elements (FR-017, FR-027, US1-AS3) · `fm-tests/ui/ViewerInteractionTest.java`
- [x] **T045** [P] [US1] Drag: dropping a node before, after or onto another node moves it with its subtree as one "Move Node" edit; dropping into its own subtree or dragging the root produces no command (FR-022) · `fm-tests/ui/DragMoveTest.java`

### Implementation

**⟶ Wait for the tests to be written and seen failing, then:**

**Wave 2 — independent (different files):**

- [x] **T046** [P] [US1] `ViewState`: the stable GEF contents object holding the latest `MindMap`, the transient expanded keys (R7) and the selection to restore by key · `fm/ui/ViewState.java`
- [x] **T047** [P] [US1] `MindMapLayout`, a Draw2d layout manager: root in the centre, branches left and right by recorded or automatic side, gaps and shift honoured (FR-013) · `fm/ui/MindMapLayout.java`
- [x] **T048** [P] [US1] `FreeMindIcons`: built-in icon name to emoji, badge for unknown names (R8) · `fm/ui/FreeMindIcons.java`
- [x] **T049** [P] [US1] `LinkOpener`: URLs through `IWorkbenchBrowserSupport`, map-relative paths through `IDE.openEditor` (FR-018) · `fm/ui/LinkOpener.java`

**⟶ Wait for Wave 2 to finish, then:**

**Wave 3 — independent (different files):**

- [x] **T050** [P] [US1] `NodeFigure`: text, icons, colours, font, link and note indicators, note tooltip, folded marker (FR-014, FR-018) · `fm/ui/figures/NodeFigure.java`
- [x] **T051** [P] [US1] `MindMapLayoutPolicy`: drag feedback and move/re-parent `TextEditCommand`s from `MindMapEdits`, no command for the root or into a node's own subtree (FR-022) · `fm/ui/parts/MindMapLayoutPolicy.java`

**⟶ Wait for Wave 3 to finish, then:**

**Wave 4 — independent (different files):**

- [x] **T052** [P] [US1] `MapEditPart`: model `ViewState`, children are the visible `NodeKey`s, `MindMapLayout` on its figure, installs `MindMapLayoutPolicy` (FR-017) · `fm/ui/parts/MapEditPart.java`
- [x] **T053** [P] [US1] `NodeEditPart`: model `NodeKey`, `refreshVisuals` from the current `MapNode`, selection feedback, adapts to `NodeKey`, double-click runs command `etalii.adp.freemind.rename` through `IHandlerService` (FR-017, FR-027, R6) · `fm/ui/parts/NodeEditPart.java`
- [x] **T054** [P] [US1] `ArrowLinkEditPart`: Draw2d connection with start and end decorations (FR-015) · `fm/ui/parts/ArrowLinkEditPart.java`

**⟶ Wait for Wave 4 to finish, then:**

- [x] **T055** [US1] `MindMapEditPartFactory` mapping `ViewState`, `NodeKey` and `ArrowLink` to their parts · `fm/ui/parts/MindMapEditPartFactory.java`

**⟶ Wait for T055, then:**

- [x] **T056** [US1] `MindMapEditor extends AdpDesignerEditor<MindMap>`: `ID`, `parse` through `MindMapParser`, `createEditPartFactory`, `contentsFor` updating `ViewState`, `visualContextId`, `viewState()`, and `reveal(NodeKey)` that expands collapsed ancestors in `ViewState` and selects the node without an edit (FR-001, US4-AS2 groundwork) · `fm/ui/MindMapEditor.java`

**⟶ Wait for T056, then:**

- [x] **T057** [US1] `NodeHandler` base: resolves the active `MindMapEditor` and selected `NodeKey`s, disabled when not editable, status-line message when an action does not apply to the root · `fm/ui/handlers/NodeHandler.java`

**⟶ Wait for T057, then:**

- [x] **T058** [US1] `ToggleFoldHandler`: fold or unfold edit when editable, `ViewState` expansion when read-only (FR-016, FR-023, R7) · `fm/ui/handlers/ToggleFoldHandler.java`

**Checkpoint**: T039 to T045 pass. A FreeMind map opens by double-click, displays with its
details, folds, zooms, selects and drags, and unedited files stay byte-identical. This is the MVP.

---

## Phase 4: User Story 2 — Edit visually, with Eclipse undo/redo and save (P2)

**Goal**: add, rename, delete, move and fold nodes from keyboard and context menu, each undoable
with a label, with the platform's save lifecycle and a New wizard.

**Independent Test**: do each edit on an example, undo and redo each, save, and diff: only the
edited parts differ, and undoing everything restores the original bytes.

Files: `fm/ui/handlers/AddNodeHandler.java`, `fm/ui/handlers/RenameHandler.java`,
`fm/ui/handlers/DeleteHandler.java`, `fm/ui/handlers/MoveNodeHandler.java`,
`fm/ui/NodeRenameManager.java`, `fm/ui/NewMindMapWizard.java`.

### Tests (write first, see them fail)

**Wave 1 — independent (different files):**

- [x] **T059** [P] [US2] Add child and add sibling: the node appears in the right place with an in-place editor open; typing sets its text; new node has FreeMind's `ID`, `CREATED`, `MODIFIED`, and `POSITION` at first level; add sibling is disabled on the root (FR-019, FR-011, US2-AS1) · `fm-tests/ui/AddNodeTest.java`
- [x] **T060** [P] [US2] Rename in place by F2 and double-click; a rich node warns first, Cancel changes nothing, OK replaces the rich content with plain text (FR-020, US2-AS2) · `fm-tests/ui/RenameTest.java`
- [x] **T061** [P] [US2] Delete one and several nodes with descendants; root refused with a message; arrow links to deleted nodes removed and restored by one undo (FR-021, edge cases) · `fm-tests/ui/DeleteTest.java`
- [x] **T062** [P] [US2] Keyboard moves: up, down, indent under previous sibling, outdent a level; enablement per the command table; `POSITION` fixed when crossing the first level (FR-022, US2-AS2) · `fm-tests/ui/MoveNodeTest.java`
- [x] **T063** [P] [US2] Undo and redo through `org.eclipse.ui.edit.undo`/`redo` for every action type: labels match the catalogue, reverse-order undo, redo re-applies, undoing each action returns the original bytes, undoing all clears dirty (FR-004, SC-002, US2-AS3, US2-AS4) · `fm-tests/ui/UndoRedoTest.java`
- [x] **T064** [P] [US2] Save, Save As, Revert and save-on-close behave as the text editor's (FR-005, US2-AS5) · `fm-tests/ui/SaveLifecycleTest.java`
- [x] **T065** [P] [US2] A mixed sequence of edits on each example, then save: notes, icons, attributes, clouds, edge styles and unknown elements are unchanged, and only edited nodes differ (FR-010, SC-004, US2-AS6) · `fm-tests/ui/PreservationTest.java`
- [x] **T066** [P] [US2] Read-only file: banner shows the reason, every edit command is disabled, fold is view-only (FR-008) · `fm-tests/ui/ReadOnlyTest.java`
- [x] **T067** [P] [US2] New wizard creates FreeMind 1.0.1's new-map text with a fresh ID and timestamps and opens it in the designer (FR-025, US2-AS7) · `fm-tests/ui/NewWizardTest.java`
- [x] **T068** [P] [US2] Context menu `etalii.adp.freemind.editor.context` lists every command in table order with the separators; each command is also reachable by its key binding in context `etalii.adp.freemind.context` (FR-024) · `fm-tests/ui/ContextMenuTest.java`

### Implementation

**⟶ Wait for the tests to be written and seen failing, then:**

**Wave 2 — independent (different files):**

- [x] **T069** [P] [US2] `NodeRenameManager`: GEF `DirectEditManager` over the node figure; on commit, confirms rich-text replacement (Cancel builds nothing), then runs the rename edit through `editor.execute` (FR-020) · `fm/ui/NodeRenameManager.java`
- [x] **T070** [P] [US2] `DeleteHandler` extends `NodeHandler`: delete edit for the selected non-root nodes, "Delete Node" or "Delete Nodes" (FR-021) · `fm/ui/handlers/DeleteHandler.java`
- [x] **T071** [P] [US2] `MoveNodeHandler` extends `NodeHandler`, parameterised `up`, `down`, `indent`, `outdent` through `IExecutableExtension`, with the command table's enablement (FR-022) · `fm/ui/handlers/MoveNodeHandler.java`
- [x] **T072** [P] [US2] `NewMindMapWizard`: container and file name page, initial text from the contract with `FreeMindConventions` ID and timestamps, opens the file (FR-025) · `fm/ui/NewMindMapWizard.java`

**⟶ Wait for Wave 2 to finish, then:**

**Wave 3 — independent (different files):**

- [x] **T073** [P] [US2] `RenameHandler` extends `NodeHandler`: opens `NodeRenameManager` on the selected node (FR-020) · `fm/ui/handlers/RenameHandler.java`
- [x] **T074** [P] [US2] `AddNodeHandler` extends `NodeHandler`, parameterised `child` or `sibling`: runs the add edit, selects the new node after re-parse, opens `NodeRenameManager` on it (FR-019) · `fm/ui/handlers/AddNodeHandler.java`

**Checkpoint**: T059 to T068 pass, and US1's tests still pass. Every edit is labelled and undoable,
saved files change only where edited, and new maps come from the New wizard.

---

## Phase 5: User Story 3 — Move freely between the visual and the text view (P3)

**Goal**: both views show the same document, one undo history covers both, invalid text is
explained, and external changes follow the text editor's rules.

**Independent Test**: edit the same map in both views, undo across switches, and change the file
on disk while clean and while dirty.

Files: tests only. The behaviour comes from the core framework (Phase 2, proven generically in
T021) and `MindMapEditor` (US1). These are the FreeMind acceptance tests. A failure is fixed in
the owning phase's file.

### Tests

**Wave 1 — independent (different files):**

- [x] **T075** [P] [US3] Edit the text, switch to the visual page, the view shows the change; switch back and forth keeps selection by key (FR-006, US3-AS1) · `fm-tests/ui/TextVisualSyncTest.java`
- [x] **T076** [P] [US3] Interleave visual edits and typing; repeated undo reverts them in the order made, whichever page is active (FR-004, FR-006, US3-AS2) · `fm-tests/ui/InterleavedUndoTest.java`
- [x] **T077** [P] [US3] Text edited into an invalid map: the visual page shows the problem with line and column and "Show text"; no text is lost; opening an already malformed file starts on the text page and leaves it unmodified (FR-007, US3-AS3, edge cases) · `fm-tests/ui/FormatProblemTest.java`
- [x] **T078** [P] [US3] External change on disk: a clean editor reloads; a dirty editor asks whether to replace, as the text editor does (FR-005, US3-AS4) · `fm-tests/ui/ExternalChangeTest.java`
- [x] **T079** [P] [US3] The same file in two editors: an edit in one shows in the other and shares its undo history (FR-006, edge cases) · `fm-tests/ui/TwoEditorsTest.java`

**Checkpoint**: T075 to T079 pass. Text and visual views are one document with one history.

---

## Phase 6: User Story 4 — Navigate a large map through the Outline view (P4)

**Goal**: the standard Outline lists the node tree, with selection linked both ways.

**Independent Test**: open a large example, select in the Outline and in the designer, and check
both follow.

Files: `fm/ui/MindMapOutlinePage.java`, `fm/ui/MindMapOutlineAdapterFactory.java`.

### Tests (write first, see them fail)

- [x] **T080** [US4] Outline lists the tree by `NodeKey` and updates after edits; selecting in the Outline reveals and selects the node, expanding folded ancestors without making the editor dirty; selecting in the designer selects in the Outline (FR-026, FR-027, US4-AS1, US4-AS2) · `fm-tests/ui/OutlineTest.java`

### Implementation

**⟶ Wait for T080 to be written and seen failing, then:**

- [x] **T081** [US4] `MindMapOutlinePage extends ContentOutlinePage`: tree of `NodeKey`s from `viewState()`, refreshed on re-parse, selection linked through `MindMapEditor.reveal` and the viewer's selection (FR-026) · `fm/ui/MindMapOutlinePage.java`

**⟶ Wait for T081, then:**

- [x] **T082** [US4] `MindMapOutlineAdapterFactory`: `MindMapEditor` → `IContentOutlinePage`, one page per editor (already declared in `plugin.xml`) · `fm/ui/MindMapOutlineAdapterFactory.java`

**Checkpoint**: T080 passes. The Outline works for FreeMind maps.

---

## Phase 7: Polish and cross-cutting validation

Files: `fm-tests/ui/PerformanceTest.java`, `fm-tests/ui/FreeMindCompatibilityTest.java`,
`README.md`, `specs/001-freemind-mindmap-designer/checklists/manual-verification.md`.

**Wave 1 — independent (different files):**

- [x] **T083** [P] Performance on a generated 1,000-node map: open and draw within 2 s; add, rename, fold and delete each show the result within 0.1 s, with CI headroom recorded in the test (SC-003) · `fm-tests/ui/PerformanceTest.java`
- [x] **T084** [P] Opt-in FreeMind 1.0.1 check, skipped unless `FREEMIND_HOME` is set: each map saved after edits is loaded by FreeMind's own reader in a separate JVM, and tree and text match what the designer showed (FR-012, SC-005, R10) · `fm-tests/ui/FreeMindCompatibilityTest.java`
- [x] **T085** [P] README: what ADP is, how to build (`mvn verify`), run the tests, install from the update site, and run the FreeMind check · `README.md`

**⟶ Wait for Wave 1 to finish, then:**

- [x] **T086** Validate against the Success Criteria: run `mvn clean verify` (under Xvfb on Linux) and confirm every test passes, including SC-001 (T040), SC-002 (T063), SC-003 (T083), SC-004 (T065) and SC-007 (every UI test runs unattended through `DesignerDriver`) · no file changes; failures go back to the owning phase

**⟶ Wait for T086, then:**

- [ ] **T087** Manual checks before merge, recorded with date and result: open saved maps in FreeMind 1.0.1 (FR-012, SC-005); a first-time walkthrough of open, add five nodes, undo two, save, without documentation (SC-006) · `specs/001-freemind-mindmap-designer/checklists/manual-verification.md`

---

## Dependencies & Execution Order

**Phases**: Setup → Foundational → US1 → US2 → US3 → US4 → Polish. Foundational blocks every
story. US2 extends US1's editor (its handlers extend `NodeHandler`, rename opens on `NodeEditPart`
figures), US3 tests exercise US1 and US2 behaviour, and US4 uses `MindMapEditor.reveal`, so the
stories run in priority order. They share no files, so each story can go to its own worker once
its predecessor is done.

**Waves per phase**:

- **Phase 1**: Wave 1 (T001–T012) → T013.
- **Phase 2**: tests Wave 1 (T014–T020) → tests Wave 2 (T021–T023) → Wave 3 (T024–T031) → **gate T014** → Wave 4 (T032–T034) → Wave 5 (T035–T036) → Wave 6 (T037–T038).
- **Phase 3 (US1)**: tests Wave 1 (T039–T045) → Wave 2 (T046–T049) → Wave 3 (T050–T051) → Wave 4 (T052–T054) → T055 → T056 → T057 → T058.
- **Phase 4 (US2)**: tests Wave 1 (T059–T068) → Wave 2 (T069–T072) → Wave 3 (T073–T074).
- **Phase 5 (US3)**: tests Wave 1 (T075–T079).
- **Phase 6 (US4)**: T080 → T081 → T082.
- **Phase 7**: Wave 1 (T083–T085) → T086 → T087.
