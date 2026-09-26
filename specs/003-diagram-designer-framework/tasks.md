# Tasks: Diagram Designer Framework

**Input**: [plan.md](plan.md), [diagram-designer-framework.spec.md](diagram-designer-framework.spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

> **Scale note**: about 60 production files and 40 test files across `core`, `freemind`, a new `drawio` module, `testing` and the build. Watch three things. First, Phase 7 moves a working designer: every existing FreeMind test and reference replay must pass unchanged, and they are run before any new FreeMind test. Second, Phase 8 must not change any file under `core/`. T112 checks this. Third, the performance tests in Phases 3 and 4 decide whether R6's in-memory re-read and R12's router hold SC-005. They run as soon as their code exists, not at the end. Tests come first in every phase (constitution IV): write them, see them fail, then implement.

**Format**: `- [ ] **T###** [P?] [US#] Description · exact/file/path`. `[P]` means independent within its wave. Paths are relative to the repository root. `core/…/diagram/` is short for `core/src/main/java/etalii/adp/core/diagram/`, and `core/test/…/diagram/` for `core/src/test/java/etalii/adp/core/diagram/`.

**Seams that keep phases file-disjoint**:
- Each story registers its canvas tools and layers as a `DiagramFeature` from its own descriptor fragment. `plugin.xml` includes every fragment with an empty fallback from Phase 1, so no story edits another's registrations.
- `NodeView`, `AdpDesignerEditor` and `DesignerDriver` do not change (R19). Diagram tests use the new `DiagramDriver`, which US1 writes in full. Its toolbox and panel methods find those tool windows by id, so they compile before US2 and US3 exist.
- The sample designer's definition, rules and mapping are foundational. The mapping is pure and tested without an IDE. Only its provider waits for US1.
- Sector membership is decided in `DiagramCommands` (foundational), so US2's move tool and US4's lanes never share a file.
- `DiagramCommands` records the last refusal on the designer's user data. `DiagramDesigner.lastRefusal()` (US1) only reads it.

---

## Phase 1: Setup

**Wave 1 — independent (different files):**

- [x] **T001** [P] Include the `drawio` module · `settings.gradle.kts`
- [x] **T002** [P] `drawio` module: `org.jetbrains.intellij.platform.module`, `implementation(project(":core"))`, test dependency on `testing`, `testdata/` on the test classpath, platform test framework, JUnit 5 with vintage · `drawio/build.gradle.kts`
- [x] **T003** [P] Root build: `pluginComposedModule(implementation(project(":drawio")))`, and a `verifyDependencyLicences` task wired into `check`. The task resolves the runtime classpath of the composed modules, excluding the IntelliJ Platform, and fails on any external artifact not listed in the allowlist with Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause or CC0-1.0 (R21, SC-006) · `build.gradle.kts`
- [x] **T004** [P] Licence allowlist, `group:artifact=licence`, with no third-party entries and a header comment explaining its use · `gradle/allowed-licences.properties`
- [x] **T005** [P] Include `adp-diagram.xml`, `adp-diagram-editing.xml`, `adp-diagram-properties.xml`, `adp-diagram-navigation.xml` and `adp-drawio.xml`, each with an empty `xi:fallback`. Add draw.io to the description (contracts/plugin-contributions.md) · `src/main/resources/META-INF/plugin.xml`
- [x] **T006** [P] Put `testdata/` on the `core` test classpath · `core/build.gradle.kts`
- [x] **T007** [P] Include the four `adp-diagram*.xml` fragments in the core test descriptor · `core/src/test/resources/META-INF/plugin.xml`
- [x] **T008** [P] Test descriptor for `drawio`, including `adp-core.xml`, the four diagram fragments and `adp-drawio.xml` · `drawio/src/test/resources/META-INF/plugin.xml`

**⟶ Wait for Wave 1 to finish, then:**

- [x] **T009** Run `./gradlew check`: green, and `verifyDependencyLicences` passes. Then add a GPL test artifact to `drawio`'s runtime classpath locally, see the task fail and name it, and remove it again · (no file)

---

## Phase 2: Foundational (blocks every story)

XML helpers, the definition API, the diagram model, the mapping contract, diff, commands and the sample designer's pure parts. No story work starts until T029 is green.

Files: `core/src/main/java/etalii/adp/core/xml/**`; in `core/…/diagram/`: the definition types, `DiagramDefinition`, `DiagramMapping`, `DiagramLayout`, the request records, `DiagramRules`, `Verdict`, `DiagramListener`, `DiagramChange`, `model/**`, `edit/DiagramDiff.java` and `edit/DiagramCommands.java`; `core/test/…/diagram/sample/{SampleDefinition,SampleRules,SampleMapping,SampleFiles}.java`; `core/testdata/sample/**`; the scanner's old place and importers in `freemind` (`parse/XmlScanner.java` removed, `parse/MindMapParser.java`, `src/test/.../parse/XmlScannerTest.java` moved, `src/test/.../FormatPurityTest.java`).

### Tests

**Wave 1 — independent (different files):**

- [x] **T010** [P] `XmlTree`: element ranges, start tags, attributes with offsets and quotes, insert points, indentation, text content, CDATA and comments skipped, malformed input raises `FormatProblem` with offset · `core/src/test/java/etalii/adp/core/xml/XmlTreeTest.java`
- [x] **T011** [P] `XmlEdits`: set, insert and remove attributes keeping the quote style; insert a child with the parent's indentation and line separator (LF and CRLF); remove an element with its own line when alone on it; set escaped content · `core/src/test/java/etalii/adp/core/xml/XmlEditsTest.java`
- [x] **T012** [P] Definition validation: every rule in data-model.md yields one message naming its declaration; several problems are reported together; a valid definition builds; the defaults are as documented (FR-002) · `core/test/…/diagram/DefinitionValidationTest.java`
- [x] **T013** [P] `DiagramDiff`: each `DiagramChange` kind; reconnect is disconnected then connected; unchanged diagrams give no changes; order is deterministic (FR-004) · `core/test/…/diagram/edit/DiagramDiffTest.java`
- [x] **T014** [P] `DiagramCommands` against a fake designer: the order is permissions, then rules, then mapping; the first refusal wins and nothing is executed; deleting an element includes its connections; a multi-item property edit is one command; command labels as in the contract; the sector is resolved from the element's centre in diagram space, and in viewport coordinates for view-space sectors; the last refusal is recorded (FR-003, FR-017, FR-018, FR-024, FR-026) · `core/test/…/diagram/edit/DiagramCommandsTest.java`
- [x] **T015** [P] Sample mapping: reads every sample file; every edit kind changes only its ranges; a byte-identical round trip; an unknown type is read with its own id and survives every edit around it; a new id is unique (FR-027 to FR-029) · `core/test/…/diagram/sample/SampleMappingTest.java`
- [x] **T016** [P] Hand-written sample files: `two-tasks`, `flow-dashed-curved`, `invisible-anchors`, `lanes`, `legend-view-space`, `unknown-type`, `broken` (not well-formed), and `crlf` (CRLF line ends) · `core/testdata/sample/*.adpsample`

### Implementation

**⟶ Wait for the tests above, then:**

- [x] **T017** Move `XmlScanner` and its test with `git mv` to `etalii.adp.core.xml`. Update its importers and let the format-purity test accept `etalii.adp.core.xml`. Run the full FreeMind suite: it must stay green · `core/src/main/java/etalii/adp/core/xml/XmlScanner.java`, `core/src/test/java/etalii/adp/core/xml/XmlScannerTest.java`, `freemind/src/main/java/etalii/adp/freemind/parse/MindMapParser.java`, `freemind/src/test/java/etalii/adp/freemind/FormatPurityTest.java`

**⟶ Wait for T017, then Wave 2 — independent (different files):**

- [x] **T018** [P] `XmlTree`, `XmlElement`, `XmlAttribute` on top of `XmlScanner` · `core/src/main/java/etalii/adp/core/xml/XmlTree.java`, `XmlElement.java`, `XmlAttribute.java`
- [x] **T019** [P] `XmlEdits` · `core/src/main/java/etalii/adp/core/xml/XmlEdits.java`
- [x] **T020** [P] Definition value types: `Outline` (predefined shapes with their `Shape` for given bounds, plus `custom`), `Tone` (JBColor light and dark triples, plus `custom`), `LineStyle`, `Dash`, `ArrowHead`, `Resize`, `EditorKind`, `SlotPosition`, `LabelSlot`, `Direction`, `Orientation`, `Space`, `Placement`, `Sizing` · `core/…/diagram/Outline.java`, `Tone.java`, `LineStyle.java`, `Dash.java`, `ArrowHead.java`, `Resize.java`, `EditorKind.java`, `SlotPosition.java`, `LabelSlot.java`, `Direction.java`, `Orientation.java`, `Space.java`, `Placement.java`, `Sizing.java`
- [x] **T021** [P] Diagram model records · `core/…/diagram/model/Diagram.java`, `Element.java`, `Connection.java`, `End.java`, `Sector.java`, `StyleOverride.java`
- [x] **T022** [P] `Verdict`, `DiagramRules` (default allow), `DiagramListener`, `DiagramChange` (sealed, eight records) · `core/…/diagram/Verdict.java`, `DiagramRules.java`, `DiagramListener.java`, `DiagramChange.java`

**⟶ Wait for Wave 2 to finish, then Wave 3 — independent (different files):**

- [x] **T023** [P] `DiagramDefinition` with its builders and `build()` validation, plus `ElementType`, `ConnectionType`, `Anchor`, `TextSlot`, `PropertyDecl`, `SectorDecl`, `ViewOptions` and `DefinitionException` (R4) · `core/…/diagram/DiagramDefinition.java`, `ElementType.java`, `ConnectionType.java`, `Anchor.java`, `TextSlot.java`, `PropertyDecl.java`, `SectorDecl.java`, `ViewOptions.java`, `DefinitionException.java`
- [x] **T024** [P] `DiagramMapping`, `AddRequest`, `BoundsChange`, `EndSide`, `DiagramLayout` · `core/…/diagram/DiagramMapping.java`, `AddRequest.java`, `BoundsChange.java`, `EndSide.java`, `DiagramLayout.java`
- [x] **T025** [P] `DiagramDiff` · `core/…/diagram/edit/DiagramDiff.java`

**⟶ Wait for Wave 3 to finish, then Wave 4 — independent (different files):**

- [x] **T026** [P] `DiagramCommands`: add, remove with cascade, set bounds with sector resolution, drop, connect, reconnect and set property. Each is checked against permissions and rules, asks the mapping, and runs through `AdpDesignerEditor.execute` with the contract's label. A refusal is stored under a user-data key (R9, R17) · `core/…/diagram/edit/DiagramCommands.java`
- [x] **T027** [P] Sample designer definition, rules and mapping, per data-model.md. The rule refuses deleting the last `task` with "a diagram needs at least one task". The mapping is built on `XmlTree` and `XmlEdits` · `core/test/…/diagram/sample/SampleDefinition.java`, `SampleRules.java`, `SampleMapping.java`
- [x] **T028** [P] `SampleFiles.generate(elements, connections, seed)`: a laid-out grid of tasks with flows, deterministic by seed · `core/test/…/diagram/sample/SampleFiles.java`

**⟶ Wait for Wave 4 to finish, then:**

- [x] **T029** Run the `core` and `freemind` suites: the foundational tests pass, and every FreeMind test still passes · (no file)

---

## Phase 3: User Story 1 — Define a designer and see a file drawn from its definition (P1) 🎯 MVP

**Goal**: a designer made of a definition and a mapping opens files and draws them exactly as declared.

**Independent test**: open the sample files with the sample designer and compare every element and connection view with the declaration.

Files: `core/…/diagram/DiagramFeature.java`, `core/…/diagram/view/**`, `core/src/main/resources/META-INF/adp-diagram.xml`, `core/test/…/diagram/sample/SampleProvider.java`, `testing/src/main/java/etalii/adp/testing/DiagramDriver.java`, and the US1 tests below.

### Tests

**Wave 1 — independent (different files):**

- [x] **T030** [P] [US1] `TextBlock`: wrapping to a width, ellipsis without wrapping, never taller than the given bounds, measured size for auto-sizing, all nine positions plus above and below (FR-007, FR-008) · `core/test/…/diagram/view/TextBlockTest.java`
- [x] **T031** [P] [US1] Anchor and outline geometry: fixed anchors on the bounds, perimeter intersection for every predefined outline and for a custom one (FR-005, FR-009) · `core/test/…/diagram/view/AnchorGeometryTest.java`
- [x] **T032** [P] [US1] `Router`: direct route when clear, one-bend and Z routes around one obstacle, grid search around several, the simplest route when fully blocked, and cache invalidation only for routes near a moved element (FR-015, R12) · `core/test/…/diagram/view/RouterTest.java`
- [x] **T033** [P] [US1] Acceptance US1-1, US1-2 and US1-3 on the sample files, checked through `ElementView` and `ConnectionView`: outline, wrapped title, auto size; dashed curved line, open arrow and middle label at the declared anchors; invisible anchors not drawn but still used · `core/test/…/diagram/ViewDiagramTest.java`
- [x] **T034** [P] [US1] Acceptance US1-4 and edge cases: `broken` opens in the text view with the problem panel; `unknown-type` shows placeholders that are selectable, preserved and not movable; a definition that fails `build()` fails the provider's construction with the declaration named · `core/test/…/diagram/PlaceholderAndProblemTest.java`
- [x] **T035** [P] [US1] Every predefined tone keeps text contrast of at least 4.5:1 in the light and dark themes. File colours too close to the canvas get the plate (FR-006) · `core/test/…/diagram/ToneContrastTest.java`
- [x] **T036** [P] [US1] Open a generated 1,000-element, 1,500-connection file and paint it once within 2 s, median of five after a warm-up (SC-005) · `core/test/…/diagram/OpenPerformanceTest.java`
- [x] **T037** [P] [US1] Change notification: an edit made in the text view reaches the listener as the right `DiagramChange`s; so do undo and an external change on disk (FR-004) · `core/test/…/diagram/ChangeNotificationTest.java`

### Implementation

**⟶ Wait for the tests above, then Wave 2 — independent (different files):**

- [x] **T038** [P] [US1] `DiagramDriver` with every method in contracts/test-kit.md. It wraps `DesignerDriver` and dispatches real mouse, key and drag events. It finds the `ADP Toolbox` and `ADP Properties` content by tool window id · `testing/src/main/java/etalii/adp/testing/DiagramDriver.java`
- [x] **T039** [P] [US1] `TextBlock` with `LineBreakMeasurer` · `core/…/diagram/view/TextBlock.java`
- [x] **T040** [P] [US1] `AnchorGeometry` and `ElementMeasure` (auto, fixed and from-diagram sizing, with `JBUI` scaling) · `core/…/diagram/view/AnchorGeometry.java`, `ElementMeasure.java`
- [x] **T041** [P] [US1] `Router` with the route cache (R12) · `core/…/diagram/view/Router.java`
- [x] **T042** [P] [US1] Arrowhead shapes for every `ArrowHead` · `core/…/diagram/view/ArrowHeads.java`
- [x] **T043** [P] [US1] `ElementView` and `ConnectionView` records · `core/…/diagram/view/ElementView.java`, `ConnectionView.java`
- [x] **T044** [P] [US1] `CanvasLayer`, `CanvasTool` and `DiagramFeature`, and the `etalii.adp.diagramFeature` extension point · `core/…/diagram/view/CanvasLayer.java`, `core/…/diagram/view/CanvasTool.java`, `core/…/diagram/DiagramFeature.java`, `core/src/main/resources/META-INF/adp-diagram.xml`

**⟶ Wait for Wave 2 to finish, then Wave 3 — independent (different files):**

- [x] **T045** [P] [US1] `ElementPainter`: outline, tone or file colours with the contrast plate, text slots, visible anchors, placeholder · `core/…/diagram/view/ElementPainter.java`
- [x] **T046** [P] [US1] `ConnectionPainter`: straight, orthogonal and curved lines, dashes, thickness, tone, arrowheads, the three labels, waypoints, placeholder (FR-012 to FR-014) · `core/…/diagram/view/ConnectionPainter.java`

**⟶ Wait for Wave 3 to finish, then:**

- [x] **T047** [US1] `DiagramCanvas`: zoom transform, clip culling, sectors, then connections, then elements in document order. It paints registered layers in their spaces, dispatches events to tools in order, converts coordinates, and hit-tests elements and connections · `core/…/diagram/view/DiagramCanvas.java`

**⟶ Wait for T047, then:**

- [x] **T048** [US1] `DiagramDesigner`: parses through the mapping; lays out from file bounds, a `DiagramLayout` or measurement; after each re-read, diffs, updates routes and notifies the listener. It installs every registered `DiagramFeature` and provides `elementView`, `connectionView`, `viewOf` (a `NodeView` derived from the element view), `allKeys`, `reveal`, `lastRefusal` and `runCommand` · `core/…/diagram/view/DiagramDesigner.java`

**⟶ Wait for T048, then Wave 4 — independent (different files):**

- [x] **T049** [P] [US1] `DiagramEditorProvider`: builds the definition in its constructor, so a `DefinitionException` fails loading; `createDesigner` returns a `DiagramDesigner` · `core/…/diagram/view/DiagramEditorProvider.java`
- [x] **T050** [P] [US1] `DiagramStructureView`: elements by sector, connections under their source, synced both ways through `AdpStructureView` · `core/…/diagram/view/DiagramStructureView.java`

**⟶ Wait for Wave 4 to finish, then:**

- [x] **T051** [US1] `SampleProvider` (`.adpsample`, sniff `<sample`, editor type id `etalii.adp.sample`) and its registration helper for tests · `core/test/…/diagram/sample/SampleProvider.java`

**⟶ Wait for T051, then:**

- [x] **T052** [US1] Run the US1 tests, including `OpenPerformanceTest`. If SC-005 is missed, profile before moving on · (no file)

**Checkpoint**: a declared designer opens and draws files. US1 is independently testable.

---

## Phase 4: User Story 2 — Build a diagram from the toolbox and edit it on the canvas (P1)

**Goal**: create, connect, select, move, resize and delete, within the definition and rules, each as one undo step.

**Independent test**: script the sample designer through add, connect, move, resize and delete; undo all of it; diff the file.

Files: `core/…/diagram/edit/{SelectionTool,MoveTool,ResizeTool,HandlesLayer,ConnectTool,RefusalFeedback,DeleteAction,EditingFeature}.java`, `core/…/diagram/toolbox/**`, `core/src/main/resources/META-INF/adp-diagram-editing.xml`, and the US2 tests below.

### Tests

**Wave 1 — independent (different files):**

- [x] **T053** [P] [US2] Toolbox, written and made to pass first as the proving test for the tool window and DnD seams. It lists exactly the declared entries in toolbox order. It follows the selected editor, including a switch to a non-ADP file (empty state) and to another designer. A drag lands on the preview side of `TextEditorWithPreview` at the drop point. Enter adds at the centre of the visible canvas (FR-016) · `core/test/…/diagram/toolbox/ToolboxTest.java`
- [x] **T054** [P] [US2] Acceptance US2-1 to US2-5 on the sample designer, including US2-5's byte-identical file after undoing everything · `core/test/…/diagram/EditingScenariosTest.java`
- [x] **T055** [P] [US2] Selection: click, Ctrl-click, Shift-click, marquee, selecting connections, non-selectable types ignored, Select All (FR-017) · `core/test/…/diagram/edit/SelectionTest.java`
- [x] **T056** [P] [US2] Refusals: anchor direction, undeclared connection type at an anchor, a rule refusal with its reason in a balloon, a refused drop target shown during the drag, a placeholder delete refused, and Escape during a drag changing nothing (FR-018) · `core/test/…/diagram/edit/RefusalTest.java`
- [x] **T057** [P] [US2] Undo lifecycle: every gesture is one step with its contract label; redo; the modified flag; a read-only file blocks every gesture; an external change keeps the selection of items that still exist; deleting an element takes its connections in the same step (FR-020, SC-004) · `core/test/…/diagram/edit/EditUndoTest.java`
- [x] **T058** [P] [US2] A single move on the generated 1,000/1,500 file shows on screen within 100 ms (SC-005, R6) · `core/test/…/diagram/EditPerformanceTest.java`

### Implementation

**⟶ Wait for the tests above, then Wave 2 — independent (different files):**

- [x] **T059** [P] [US2] `SelectionTool`: click, modifiers and marquee · `core/…/diagram/edit/SelectionTool.java`
- [x] **T060** [P] [US2] `HandlesLayer` and `ResizeTool`: only the handles the type's `Resize` allows, grid snap, minimum size · `core/…/diagram/edit/HandlesLayer.java`, `ResizeTool.java`
- [x] **T061** [P] [US2] `MoveTool`: moves the selection with a 3 px threshold and grid snap. It drops onto `droppableOnto` targets with before, after and into bands. Feedback is shown while dragging, and Escape cancels · `core/…/diagram/edit/MoveTool.java`
- [x] **T062** [P] [US2] `ConnectTool`: drag from an anchor, with the armed toolbox type or the first type the anchor accepts. Allowed targets are highlighted and refused ones show the not-allowed cursor. Also drags a connection end to reconnect it · `core/…/diagram/edit/ConnectTool.java`
- [x] **T063** [P] [US2] `RefusalFeedback`: a `JBPopupFactory` balloon near the pointer with the refusal reason · `core/…/diagram/edit/RefusalFeedback.java`
- [x] **T064** [P] [US2] `DeleteAction` (`etalii.adp.core.Delete`, the `$Delete` shortcut, only with canvas focus) · `core/…/diagram/edit/DeleteAction.java`
- [x] **T065** [P] [US2] Toolbox tool window: factory, panel (entries with outline and tone icons, grouped, keyboard Enter), `DnDManager` drag source, and arming a connection type · `core/…/diagram/toolbox/ToolboxToolWindowFactory.java`, `ToolboxPanel.java`, `ToolboxDragSource.java`

**⟶ Wait for Wave 2 to finish, then:**

- [x] **T066** [US2] `EditingFeature`: installs the tools, the handles layer and the canvas `DnDTarget` for toolbox drops, and the `etalii.adp.core.DiagramPopup` context menu · `core/…/diagram/edit/EditingFeature.java`

**⟶ Wait for T066, then:**

- [x] **T067** [US2] Register the editing feature, `ADP Toolbox`, `etalii.adp.core.Delete` and `etalii.adp.core.DiagramPopup` · `core/src/main/resources/META-INF/adp-diagram-editing.xml`

**⟶ Wait for T067, then:**

- [x] **T068** [US2] Run the US1 and US2 tests, including `EditPerformanceTest` · (no file)

**Checkpoint**: the sample designer is a working editor. US2 is independently testable.

---

## Phase 5: User Story 3 — Edit values in a central property panel and in place (P2)

**Goal**: one ADP Properties tool window for every designer, and in-place editing of every visible editable text.

**Independent test**: edit each editor kind in the panel and each editable text on the canvas; both views agree; each edit is one undo step; read-only values refuse edits.

Files: `core/…/diagram/properties/**`, `core/src/main/resources/META-INF/adp-diagram-properties.xml`, and the US3 tests below.

### Tests

**Wave 1 — independent (different files):**

- [x] **T069** [P] [US3] Property panel: acceptance US3-1, US3-3, US3-4 and US3-5. Each editor kind round-trips its value. Input that does not fit the kind is refused before any edit. Mixed values show empty with a hint. The panel clears when its item is removed, including with a cell editor open (FR-021 to FR-024) · `core/test/…/diagram/properties/PropertyPanelTest.java`
- [x] **T070** [P] [US3] In-place editing: acceptance US3-2. Enter commits and Escape cancels. A multi-line slot commits with Ctrl+Enter. F2 opens the first editable text. An item that disappears while editing applies nothing. A panel edit and an in-place edit agree (FR-019) · `core/test/…/diagram/properties/InPlaceEditingTest.java`

### Implementation

**⟶ Wait for the tests above, then Wave 2 — independent (different files):**

- [x] **T071** [P] [US3] Renderers and editors for text, multi-line, number, yes/no, choice and colour, with input validation (R14) · `core/…/diagram/properties/PropertyEditors.java`
- [x] **T072** [P] [US3] `PropertyTableModel`: rows are the declarations the selection shares, by category, with mixed values and read-only rows · `core/…/diagram/properties/PropertyTableModel.java`
- [x] **T073** [P] [US3] `InPlaceEditor`: `JBTextField` or `JBTextArea` over the slot, scaled font, commit through `DiagramCommands.setProperty`, cancel on disappearance (R15) · `core/…/diagram/properties/InPlaceEditor.java`

**⟶ Wait for Wave 2 to finish, then Wave 3 — independent (different files):**

- [x] **T074** [P] [US3] `PropertyPanel` (`JBTable`, follows the selected editor, its selection and its model) and its tool window factory · `core/…/diagram/properties/PropertyPanel.java`, `PropertiesToolWindowFactory.java`
- [x] **T075** [P] [US3] `EditInPlaceAction` (F2), `ShowPropertiesAction` (Alt+Shift+P), and `PropertiesFeature`, which opens in-place editing on double-click · `core/…/diagram/properties/EditInPlaceAction.java`, `ShowPropertiesAction.java`, `PropertiesFeature.java`

**⟶ Wait for Wave 3 to finish, then:**

- [x] **T076** [US3] Register the properties feature, `ADP Properties`, `etalii.adp.core.EditInPlace` and `etalii.adp.core.ShowProperties`, the last two added to `etalii.adp.core.DiagramPopup` · `core/src/main/resources/META-INF/adp-diagram-properties.xml`

**⟶ Wait for T076, then:**

- [x] **T077** [US3] Run the US1 to US3 tests · (no file)

**Checkpoint**: properties are editable in the panel and in place. US3 is independently testable.

---

## Phase 6: User Story 4 — Navigate large diagrams and organise them in swimlanes (P3)

**Goal**: pan and zoom per designer, and lanes in diagram or view space, with membership written to the file.

**Independent test**: enable pan, zoom and lanes in the sample designer, move elements between lanes, and check the file and the scaling.

Files: `core/…/diagram/navigation/**`, `core/src/main/java/etalii/adp/core/actions/ZoomActions.java`, `core/src/main/resources/META-INF/adp-diagram-navigation.xml`, and the US4 tests below.

### Tests

**Wave 1 — independent (different files):**

- [x] **T078** [P] [US4] Acceptance US4-1 and US4-2: zooming scales elements, connections, texts and diagram-space lanes, and view-space sectors stay fixed. With zoom off, the zoom actions are disabled and the Ctrl+wheel does nothing. With pan off, Space-drag and middle-drag do nothing (FR-025) · `core/test/…/diagram/navigation/NavigationTest.java`
- [x] **T079** [P] [US4] Acceptance US4-3: dragging between diagram-space lanes changes membership in the file as one undo step labelled `Move to <lane>`. Membership also works for a view-space sector after scrolling. The listener receives `SectorChanged` (FR-026) · `core/test/…/diagram/navigation/SectorTest.java`

### Implementation

**⟶ Wait for the tests above, then Wave 2 — independent (different files):**

- [x] **T080** [P] [US4] `SectorLayer`: diagram-space lanes under the zoom and view-space sectors in viewport coordinates, with labels · `core/…/diagram/navigation/SectorLayer.java`
- [x] **T081** [P] [US4] `PanTool` (Space-drag, middle-drag) and `WheelZoomTool` (Ctrl+wheel around the pointer), both honouring `ViewOptions` · `core/…/diagram/navigation/PanTool.java`, `WheelZoomTool.java`
- [x] **T082** [P] [US4] The zoom actions are disabled when the selected designer is a `DiagramDesigner` whose view options turn zoom off · `core/src/main/java/etalii/adp/core/actions/ZoomActions.java`

**⟶ Wait for Wave 2 to finish, then:**

- [x] **T083** [US4] `NavigationFeature` and its registration · `core/…/diagram/navigation/NavigationFeature.java`, `core/src/main/resources/META-INF/adp-diagram-navigation.xml`

**⟶ Wait for T083, then:**

- [x] **T084** [US4] Run every `core` test · (no file)

**Checkpoint**: all four stories work on the sample designer. The framework is complete.

---

## Phase 7: FreeMind on the framework (FR-031)

**Goal**: the FreeMind designer runs on the shared canvas, toolbox and property panel with no change in behaviour or saved bytes.

**Independent test**: the unchanged FreeMind suite and reference replay pass, then the new toolbox and panel tests.

Files: `freemind/src/main/java/etalii/adp/freemind/ui/**` (including the deletions), `freemind/src/main/java/etalii/adp/freemind/edit/MindMapEdits.java`, `freemind/src/main/resources/META-INF/adp-freemind-editing.xml`, `freemind/src/test/java/etalii/adp/freemind/edit/MindMapEditsTest.java`, and the new FreeMind tests below.

### Tests

**Wave 1 — independent (different files):**

- [x] **T085** [P] New edits: `setAttribute` (insert, replace, remove), `addArrowLink` and `removeArrowLink`, each changing only its range · `freemind/src/test/java/etalii/adp/freemind/edit/MindMapEditsTest.java`
- [x] **T086** [P] `FreeMindMapping`: the diagram read from each example (root, nodes, branches from the tree, arrow links, properties, rich nodes read-only); each request maps to the right `MindMapEdits` call · `freemind/src/test/java/etalii/adp/freemind/ui/FreeMindMappingTest.java`
- [x] **T087** [P] `FreeMindDefinition.DEFINITION` builds without problems · `freemind/src/test/java/etalii/adp/freemind/ui/FreeMindDefinitionTest.java`
- [x] **T088** [P] Toolbox on FreeMind: a node dropped onto a node adds a child; one dropped on empty canvas is refused with "a node needs a parent"; an arrow link connects two nodes; disconnecting a branch is refused with "remove the node to remove its branch" · `freemind/src/test/java/etalii/adp/freemind/ui/FreeMindToolboxTest.java`
- [x] **T089** [P] Property panel on FreeMind: text, colour, background colour, folded and link are edited as one undo step each; ID is read-only; a rich node's text is read-only · `freemind/src/test/java/etalii/adp/freemind/ui/FreeMindPropertiesTest.java`

### Implementation

**⟶ Wait for the tests above, then Wave 2 — independent (different files):**

- [x] **T090** [P] `setAttribute`, `addArrowLink`, `removeArrowLink` · `freemind/src/main/java/etalii/adp/freemind/edit/MindMapEdits.java`
- [x] **T091** [P] `FreeMindDefinition`: `root`, `node` (fork and bubble outlines), `branch`, `arrowLink`, text slots for text, icons, indicators and the fold marker, the properties, the rules (R19) · `freemind/src/main/java/etalii/adp/freemind/ui/FreeMindDefinition.java`
- [x] **T092** [P] `MindMapLayout` implements `DiagramLayout`, honouring fold state from `ViewState` · `freemind/src/main/java/etalii/adp/freemind/ui/MindMapLayout.java`

**⟶ Wait for Wave 2 to finish, then:**

- [x] **T093** `FreeMindMapping` over `MindMapParser` and `MindMapEdits`; `drop` maps to `move` with its placement · `freemind/src/main/java/etalii/adp/freemind/ui/FreeMindMapping.java`

**⟶ Wait for T093, then:**

- [x] **T094** `MindMapDesigner` extends `DiagramDesigner`. It keeps folding and tree navigation (overriding `navigate`). It adds a canvas tool for link and note indicators through `LinkOpener`. Its `viewOf` fills icons, link, note and folded from the slots. `MindMapEditorProvider` extends `DiagramEditorProvider` · `freemind/src/main/java/etalii/adp/freemind/ui/MindMapDesigner.java`, `MindMapEditorProvider.java`, `LinkOpener.java`

**⟶ Wait for T094, then Wave 3 — independent (different files):**

- [x] **T095** [P] FreeMind actions use the framework: Delete through `DiagramCommands`, Rename through the in-place editor, and the rest through `runCommand` · `freemind/src/main/java/etalii/adp/freemind/ui/actions/*.java`
- [x] **T096** [P] Remove the `EditingInstaller` listener registration and keep every action id and shortcut · `freemind/src/main/resources/META-INF/adp-freemind-editing.xml`

**⟶ Wait for Wave 3 to finish, then:**

- [x] **T097** Delete `MindMapCanvas`, `NodePainter`, `DragMove`, `InPlaceRename` and `EditingInstaller` · `freemind/src/main/java/etalii/adp/freemind/ui/`

**⟶ Wait for T097, then:**

- [x] **T098** Run the full FreeMind suite with no test file changed except T085 to T089: reference replay, round trips, every UI test, and both performance tests. Then run the new tests · (no file)

**Checkpoint**: FreeMind runs on the framework with identical behaviour and bytes.

---

## Phase 8: draw.io designer (FR-031)

**Goal**: a second, real diagram format built only on the framework, with no change to `core`.

**Independent test**: round trips and scripted edits on the vendored templates, the registration tests, and the code-size test.

Files: `drawio/src/main/**`, `drawio/src/test/java/**`, `drawio/testdata/**`, `src/integrationTest/java/etalii/adp/it/OpenDrawioTest.java`.

**Before starting**, record the commit this phase starts from for T111.

### Tests

**Wave 1 — independent (different files):**

- [x] **T099** [P] Vendor the examples. From `jgraph/drawio` `src/main/webapp/templates` take `flowcharts/flowchart_1`, `flowcharts/cross_functional_flowchart_1`, `flowcharts/data_flow_1`, `flowcharts/workflow_1` and two UML templates. Decompress each page with a recorded, repeatable tool into `<name>.drawio`. Keep `flowchart_2` compressed as `compressed.drawio`. Each file gets a `.LICENSE` naming the source URL, CC-BY-4.0 and the modification (R20) · `drawio/src/test/java/etalii/adp/drawio/DecompressTemplates.java`, `drawio/testdata/examples/*.drawio`, `drawio/testdata/examples/*.LICENSE`
- [x] **T100** [P] Sniffer: claims `mxfile` and `mxGraphModel` after a BOM, XML declaration, comments and whitespace; rejects other XML, binaries and empty files; never reads more than 4 KB; never throws · `drawio/src/test/java/etalii/adp/drawio/DrawioSnifferTest.java`
- [x] **T101** [P] `Style`: reading keys and flags; setting, adding and removing one key keeps order, unknown keys and a trailing `;` · `drawio/src/test/java/etalii/adp/drawio/StyleTest.java`
- [x] **T102** [P] `DrawioMapping` on every example: element types, swimlane sectors, middle and end labels, waypoints, absolute bounds from relative ones; byte-identical round trip; every edit kind changes only its ranges; moving between lanes rewrites `parent` and relative coordinates; unknown shapes and groups are placeholders and survive; a compressed page raises `FormatProblem` naming the "Compressed" setting; new ids are unique · `drawio/src/test/java/etalii/adp/drawio/DrawioMappingTest.java`
- [x] **T103** [P] Designer in a headless IDE: the provider claims `.drawio` files that sniff, and not `.xml` or non-draw.io `.drawio`; examples open; the US2 script on `flowchart_1` undone to identical bytes; the property panel changes fill colour and edge style; `compressed.drawio` opens in the text view with the explanation · `drawio/src/test/java/etalii/adp/drawio/DrawioDesignerTest.java`
- [x] **T104** [P] SC-001: non-blank, non-comment lines of `drawio/src/main/java` and of the sample designer's main parts (`SampleDefinition`, `SampleRules`, `SampleMapping`, `SampleProvider`) are each at most 342, a tenth of the 3,423-line FreeMind baseline of 2026-09-25. Neither references `Graphics2D`, `UndoManager`, `JTable` or `MouseListener` (R22) · `drawio/src/test/java/etalii/adp/drawio/CodeSizeTest.java`
- [x] **T105** [P] No network access while opening and editing examples, as in `freemind` · `drawio/src/test/java/etalii/adp/drawio/NoNetworkAccessTest.java`

### Implementation

**⟶ Wait for the tests above, then Wave 2 — independent (different files):**

- [x] **T106** [P] `Style` · `drawio/src/main/java/etalii/adp/drawio/Style.java`
- [x] **T107** [P] `DrawioSniffer` and `DrawioFileType` · `drawio/src/main/java/etalii/adp/drawio/DrawioSniffer.java`, `DrawioFileType.java`
- [x] **T108** [P] `DrawioDefinition`: vertex shapes, `swimlane` sector, `edge` type with style properties, properties per data-model.md · `drawio/src/main/java/etalii/adp/drawio/DrawioDefinition.java`

**⟶ Wait for Wave 2 to finish, then:**

- [x] **T109** `DrawioMapping` on `XmlTree`, `XmlEdits` and `Style` · `drawio/src/main/java/etalii/adp/drawio/DrawioMapping.java`

**⟶ Wait for T109, then Wave 3 — independent (different files):**

- [x] **T110** [P] `DrawioEditorProvider` (editor type id `etalii.adp.drawio`, name `draw.io Designer`) and its registration · `drawio/src/main/java/etalii/adp/drawio/DrawioEditorProvider.java`, `drawio/src/main/resources/META-INF/adp-drawio.xml`
- [x] **T111** [P] Integration scenario in real IntelliJ IDEA: open `flowchart_1.drawio`, add a shape from the toolbox, undo, and the file is unchanged · `src/integrationTest/java/etalii/adp/it/OpenDrawioTest.java`

**⟶ Wait for Wave 3 to finish, then:**

- [x] **T112** Run the `drawio` suite. `git diff --stat <phase start> -- core/` must be empty. If `core` needed a change, stop: fix it as a framework gap in its own commit and re-run the `core` suite · (no file)

**Checkpoint**: draw.io files open and edit in the IDE. The second designer used the framework unchanged.

---

## Phase 9: Polish

**Wave 1 — independent (different files):**

- [x] **T113** [P] Designer author guide: from an empty module to the two-element, one-connection designer. It covers the definition, a mapping with `XmlTree` and `XmlEdits`, the provider, registration, tests with `DiagramDriver`, rules, listener and layout, and links the contract (SC-002, R23) · `docs/diagram-designer-guide.md`
- [x] **T114** [P] README: draw.io as the second format, the ADP Toolbox and ADP Properties tool windows, and a link to the guide · `README.md`

**⟶ Wait for Wave 1 to finish, then:**

- [x] **T115** Rehearse SC-002: follow only the guide to build a two-element designer in a scratch module, fix every gap found in the guide, then delete the scratch module · `docs/diagram-designer-guide.md`
- [x] **T116** Run `./gradlew check` (every suite, integration tests, Plugin Verifier and the licence check) and check SC-001 to SC-006 against the results · (no file)
- [ ] **T117** Manual: the quickstart walkthrough in the light and dark themes and at 200 % scale, and the SC-002 timed run by a developer new to the framework, with its time recorded in the pull request · (no file)

---

## Dependencies & Execution Order

- **Setup (Phase 1)** → **Foundational (Phase 2)** → **US1 (Phase 3)** → **US2 (Phase 4)** → **US3 (Phase 5)** → **US4 (Phase 6)** → **FreeMind (Phase 7)** → **draw.io (Phase 8)** → **Polish (Phase 9)**.
- US2, US3 and US4 own disjoint files and register through their own fragments. After US1 they could run in parallel, but each story's acceptance tests use the earlier ones' gestures (US3 selects with US2's tool; US4 drags with it), so they are done in priority order.
- FreeMind (Phase 7) needs all four stories, because it uses the toolbox, the panel and in-place editing. draw.io (Phase 8) comes after it, so the framework is final before `core` is frozen.
- Phase 1: Wave 1 (T001–T008) → T009.
- Phase 2: test wave (T010–T016) → T017 → Wave 2 (T018–T022) → Wave 3 (T023–T025) → Wave 4 (T026–T028) → T029.
- Phase 3: test wave (T030–T037) → Wave 2 (T038–T044) → Wave 3 (T045–T046) → T047 → T048 → Wave 4 (T049–T050) → T051 → T052.
- Phase 4: test wave (T053–T058, T053 first to pass) → Wave 2 (T059–T065) → T066 → T067 → T068.
- Phase 5: test wave (T069–T070) → Wave 2 (T071–T073) → Wave 3 (T074–T075) → T076 → T077.
- Phase 6: test wave (T078–T079) → Wave 2 (T080–T082) → T083 → T084.
- Phase 7: test wave (T085–T089) → Wave 2 (T090–T092) → T093 → T094 → Wave 3 (T095–T096) → T097 → T098.
- Phase 8: test wave (T099–T105) → Wave 2 (T106–T108) → T109 → Wave 3 (T110–T111) → T112.
- Phase 9: Wave 1 (T113–T114) → T115 → T116 → T117.
