# Tasks: JetBrains IDE Support

**Input**: [plan.md](plan.md), [jetbrains-ide-support.spec.md](jetbrains-ide-support.spec.md),
[research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/),
[quickstart.md](quickstart.md)

> **Scale note**: about 130 files are created, ported or removed across three Gradle modules, the
> plug-in descriptor, the integration suite and the previous host's whole tree. Watch three things:
> Phase 1 must record the reference results and be committed before anything is removed (R10);
> Phase 2 proves the undo bridge and Structure view sync with a fake format before FreeMind code
> depends on them (R7, R8); the Rider integration test in Phase 3 decides whether the R12 fallback
> is needed. Tests come first in every phase (constitution IV): write them, see them fail, then
> implement.

**Format**: `- [ ] **T###** [P?] [US#] Description · exact/file/path`. `[P]` means independent
within its wave. Paths are relative to the repository root.

**Descriptor split**: every story owns its own descriptor fragment. `plugin.xml` (Phase 1)
includes `adp-core.xml`, `adp-freemind-editor.xml`, `adp-freemind-editing.xml` and
`adp-freemind-new.xml` with `xi:include` and an empty `xi:fallback`, so no story edits another's
registrations.

**Seams that keep stories file-disjoint**:
- `NodeView` lives in `core`, not `freemind/ui`: the test kit returns it and must know no format.
- Structure view sync is generic in `core` (`AdpStructureView`, proved in Phase 2); FreeMind's
  element tree (`MindMapStructureView`) is foundational so `MindMapDesigner` can return it.
- `AdpDesignerEditor.installActions(view, groupId)` wires the context menu and the canvas-local
  shortcut sets from an action group id, if that group is registered. US1 passes
  `etalii.adp.freemind.DesignerPopup`; US2 registers the group.
- Mouse gestures that edit (drag-and-drop move, starting an in-place rename) are attached by US2's
  `EditingInstaller`, a `FileEditorManagerListener`, so US1's canvas stays view-only.

---

## Phase 1: Setup (baseline recording, then the new build skeleton)

The previous build is still in place. Record from it first, commit, then add the Gradle build
beside it. The previous build is not run again after T004.

**Wave 1 — independent (different files):**

- [x] **T001** [P] Write the scenario list: for each of the 7 vendored example maps, one scenario per editing action type (add child, add sibling, rename, rename rich node, delete, delete several, move up, move down, indent, outdent, drag re-parent to first level and away, fold, unfold), with target node keys, arguments, fixed seed and fixed clock · `freemind/testdata/reference/scenarios.json`
- [x] **T002** [P] Add a recorder to the previous test suite that parses each example, runs every scenario through `MindMapEdits` with the injected `now` and `RandomGenerator` overloads, applies the result through the previous text-edit application, and writes the bytes to `freemind/testdata/reference/<map>/<nn>-<action>.mm` · `tests/etalii.adp.freemind.tests/src/etalii/adp/freemind/edit/ReferenceRecorder.java`
- [x] **T003** [P] Write the spec 001 test inventory: one row per test method in `tests/**/*Test.java` with the behaviour it verifies and the new test class and method that will cover it (names as in this task list) · `freemind/testdata/reference/spec001-test-inventory.md`

**⟶ Wait for Wave 1 to finish, then:**

- [x] **T004** Run the recorder with the previous build (`mvn verify` restricted to `ReferenceRecorder`), check one result per action type by eye against its source map, then `mvn clean` · `freemind/testdata/reference/<map>/*.mm`

**⟶ Wait for T004, then:**

- [x] **T005** Move the example maps and their licence files with `git mv` from `tests/etalii.adp.freemind.tests/examples/` and commit the recorded baseline (scenarios, results, inventory, moved examples) · `freemind/testdata/examples/`

**⟶ Wait for T005, then Wave 2 — independent (different files):**

- [x] **T006** [P] Add the Gradle 9 wrapper · `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`
- [x] **T007** [P] `rootProject.name = "EtAlii.Adp.IntelliJ"`, include `core`, `freemind`, `testing` · `settings.gradle.kts`
- [x] **T008** [P] Plug-in version, platform version `2026.2.3`, `sinceBuild=262`, Java 21 · `gradle.properties`
- [x] **T009** [P] Root build: IntelliJ Platform Gradle Plugin 2.19, `intellijIdea("2026.2.3")` with no bundled plug-ins, `pluginComposedModule` for `core` and `freemind`, JUnit 5 Jupiter plus Vintage, the `integrationTest` source set (Starter + Driver, depends on `buildPlugin`, wired into `check`), `verifyPlugin` against IntelliJ IDEA, Rider, WebStorm, PyCharm, CLion, GoLand, PhpStorm and RubyMine from build 262, archive name `etalii-adp-<version>.zip` · `build.gradle.kts`
- [x] **T010** [P] `core` module: `org.jetbrains.intellij.platform.module`, platform test framework for tests · `core/build.gradle.kts`
- [x] **T011** [P] `freemind` module: depends on `core`, test dependency on `testing`, `testdata/` on the test classpath · `freemind/build.gradle.kts`
- [x] **T012** [P] `testing` module: depends on `core` and the platform test framework · `testing/build.gradle.kts`
- [x] **T013** [P] Plug-in descriptor per contracts/plugin-contributions.md (id `etalii.adp`, name, vendor, `since-build="262"`, depends only on `com.intellij.modules.platform`, description naming Apache-2.0) with the four `xi:include`s and empty fallbacks · `src/main/resources/META-INF/plugin.xml`
- [x] **T014** [P] Plug-in icon · `src/main/resources/META-INF/pluginIcon.svg`
- [x] **T015** [P] Rewrite for Gradle: `build/`, `.gradle/`, `.intellijPlatform/`, `out/`, `*.class`, `.idea/` workspace files, keep the Python, agent-local and OS entries, drop the previous host's entries · `.gitignore`

**⟶ Wait for Wave 2 to finish, then:**

- [x] **T016** Run `./gradlew build` (green with no sources yet) and `./gradlew runIde` (sandbox IDE starts with the empty plug-in installed) · (no file)

---

## Phase 2: Foundational (blocks every story)

Core framework, the carried-over format layer, the test kit and the two proving tests. No story
work starts until T050 is green.

Files: `core/src/main/java/etalii/adp/core/**`, `core/src/main/resources/META-INF/adp-core.xml`,
`core/src/test/java/etalii/adp/core/**`, `freemind/src/main/java/etalii/adp/freemind/{model,parse,edit}/**`,
`freemind/src/main/java/etalii/adp/freemind/ui/FreeMindIcons.java`,
`freemind/src/main/java/etalii/adp/freemind/ui/MindMapStructureView.java`,
`testing/src/main/java/etalii/adp/testing/**`, and the format tests below.

### Tests (write first, they fail)

**Wave 1 — independent (different files):**

- [x] **T017** [P] Port `MindMapAsserts` as `key(...)`, `example(...)`, `reference(...)` over `freemind/testdata/` · `freemind/src/test/java/etalii/adp/freemind/FreeMindAsserts.java`
- [x] **T018** [P] A minimal second format for core tests (a line-per-item `.adpfake` file with its own provider, sniffer and designer), registered per test through `ExtensionTestUtil`. It also shows a format needs no core change (FR-019) · `core/src/test/java/etalii/adp/core/FakeFormat.java`
- [x] **T019** [P] Port `DocumentEditOperationTest`: inserts, deletes, replaces, same-offset order, overlap rejection, `applyTo(String)` · `core/src/test/java/etalii/adp/core/TextChangesTest.java`

**⟶ Wait for Wave 1 to finish, then Wave 2 — independent (different files):**

- [x] **T020** [P] Implement `Rgb`, `TextChange`, `TextChanges` (`applyTo(String)`, `applyTo(Document)` highest offset first), `FormatProblem(message, offset)` per data-model.md · `core/src/main/java/etalii/adp/core/Rgb.java`, `TextChange.java`, `TextChanges.java`, `FormatProblem.java`
- [x] **T021** [P] Port `XmlScannerTest` · `freemind/src/test/java/etalii/adp/freemind/parse/XmlScannerTest.java`
- [x] **T022** [P] Port `MindMapParserTest` (colours as `Rgb`, `lineSeparator` always `\n`) · `freemind/src/test/java/etalii/adp/freemind/parse/MindMapParserTest.java`
- [x] **T023** [P] Port `RichTextTest` · `freemind/src/test/java/etalii/adp/freemind/parse/RichTextTest.java`
- [x] **T024** [P] Port `FreeMindConventionsTest` · `freemind/src/test/java/etalii/adp/freemind/edit/FreeMindConventionsTest.java`
- [x] **T025** [P] Port `MindMapEditsTest` against `TextChanges` · `freemind/src/test/java/etalii/adp/freemind/edit/MindMapEditsTest.java`
- [x] **T026** [P] Import scan: `model`, `parse`, `edit` import nothing from `com.intellij`, `java.awt`, `javax.swing` · `freemind/src/test/java/etalii/adp/freemind/FormatPurityTest.java`
- [x] **T027** [P] Replay every scenario in `scenarios.json` through the ported format layer and compare with the recorded bytes, after converting the example's separators as the platform `Document` would and restoring them on write (FR-008, SC-003); also open-and-write without edits for every example (SC-002, format level) · `freemind/src/test/java/etalii/adp/freemind/edit/ReferenceReplayTest.java`
- [x] **T028** [P] **Proving test (R7)**, port of `OperationHistoryCommandStackTest`: open a fake-format file, `execute("Rename Item", changes)` with the designer focused, assert `UndoManager` offers "Undo Rename Item", undo restores the text, redo reapplies, one command is one step, `execute` on a read-only document is a no-op · `core/src/test/java/etalii/adp/core/UndoBridgeTest.java`
- [x] **T029** [P] Port `AdpDesignerEditorTest` as provider and designer tests: accept only with extension and sniff, sniff reads at most 4 KB, `HIDE_DEFAULT_EDITOR`, composite is `TextEditorWithPreview` in preview layout, a `FormatProblem` opens in text layout with the problem panel and line and column and writes nothing, read-only banner follows writability, coalesced re-parse, `getState`/`setState` keep zoom and selection · `core/src/test/java/etalii/adp/core/AdpEditorProviderTest.java`
- [x] **T030** [P] **Proving test (R8)**: with the fake format, selecting a Structure view element reveals and selects the item in the designer, and selecting in the designer makes it the model's current element; the composite's `getStructureViewBuilder` is the designer's, not the text side's · `core/src/test/java/etalii/adp/core/StructureSyncTest.java`
- [x] **T031** [P] Trailing-space stripping is `NOT_ALLOWED` for accepted files and untouched for others, with "strip on save" enabled · `core/src/test/java/etalii/adp/core/StripTrailingSpacesTest.java`

### Implementation

**⟶ Wait for Wave 2 to finish, then Wave 3 — independent (different files):**

- [x] **T032** [P] Port the model package with `Rgb` for colours: `MindMap`, `MapNode`, `ArrowLink`, `NodeKey`, `NodeRanges`, `Range`, `Side`, `FontSpec`, `AttributeRange` · `freemind/src/main/java/etalii/adp/freemind/model/*.java`
- [x] **T033** [P] `ViewState` (expanded, selection, zoom 0.25 to 4.0 in steps) and `AdpDataKeys` (`ADP_DESIGNER`) · `core/src/main/java/etalii/adp/core/ViewState.java`, `AdpDataKeys.java`
- [x] **T034** [P] `NodeView` record: key, bounds, text, foreground, background, font, icon glyphs, link and note indicators, folded flag · `core/src/main/java/etalii/adp/core/NodeView.java`
- [x] **T035** [P] Problem panel: message, line, column, "Show Text" link (replaces `MessagePanel`) · `core/src/main/java/etalii/adp/core/ui/ProblemPanel.java`
- [x] **T036** [P] Read-only banner as an `EditorNotificationPanel` stating the reason · `core/src/main/java/etalii/adp/core/ui/ReadOnlyBanner.java`
- [x] **T037** [P] Port the builtin-icon to Unicode glyph table · `freemind/src/main/java/etalii/adp/freemind/ui/FreeMindIcons.java`
- [x] **T038** [P] Port `DropPosition`; `Layout` enum (DESIGNER, TEXT, SPLIT) replacing `Page` · `testing/src/main/java/etalii/adp/testing/DropPosition.java`, `Layout.java`

**⟶ Wait for Wave 3 to finish, then Wave 4 — independent (different files):**

- [x] **T039** [P] Port `XmlScanner`, `MindMapParser`, `RichText` unchanged in logic · `freemind/src/main/java/etalii/adp/freemind/parse/*.java`
- [x] **T040** [P] `AdpDesignerEditor<M>` per contracts/designer-framework.md: `FileEditor` plus `DocumentReferenceProvider`, coalesced parse on open and on document change, `modelChanged`, problem panel and switch to text layout, read-only banner and `isEditable()`, `execute(label, changes)` as one `WriteCommandAction`, `DataContext` (`ADP_DESIGNER`, `SELECTED_ITEMS`), selection in `ViewState` with listeners, abstract `reveal(key)` and `viewOf(key)`, zoom and `JBScrollPane`, `installActions(view, groupId)`, `getState`/`setState`, `dispose` · `core/src/main/java/etalii/adp/core/AdpDesignerEditor.java`

**⟶ Wait for Wave 4 to finish, then Wave 5 — independent (different files):**

- [x] **T041** [P] Port `MindMapEdits` and `FreeMindConventions` returning `Edit(label, TextChanges, created)` · `freemind/src/main/java/etalii/adp/freemind/edit/*.java`
- [x] **T042** [P] `AdpEditorProvider` (`DumbAware`): `accept` = extension and 4 KB sniff via `VirtualFile.getInputStream`, `createEditor` returns a `TextEditorWithPreview` subclass (preview-only default, text layout on a problem, `getStructureViewBuilder` delegated to the designer), policy `HIDE_DEFAULT_EDITOR` · `core/src/main/java/etalii/adp/core/AdpEditorProvider.java`
- [x] **T043** [P] Generic Structure view: `TreeBasedStructureViewBuilder` and model base over a designer, navigate calls `reveal`, designer selection drives the current element and fires model change · `core/src/main/java/etalii/adp/core/AdpStructureView.java`
- [x] **T044** [P] Zoom In, Zoom Out, Actual Size and Select All Nodes actions, enabled only with a focused designer · `core/src/main/java/etalii/adp/core/actions/ZoomActions.java`, `SelectAllAction.java`

**⟶ Wait for Wave 5 to finish, then Wave 6 — independent (different files):**

- [x] **T045** [P] `stripTrailingSpacesFilterFactory` returning `NOT_ALLOWED` when any registered `AdpEditorProvider` accepts the file · `core/src/main/java/etalii/adp/core/AdpStripTrailingSpacesFilterFactory.java`
- [x] **T046** [P] Port `DesignerDriver` to contracts/test-kit.md: `open`/`openText` through the fixture, `run` through `ActionManager` with the designer's `DataContext`, `press` to the focused view, `dragOnto` as mouse events at `viewOf` bounds, `typeInPlace` into the focused in-place field, `undo`/`redo` through `$Undo`/`$Redo`, layouts, text edits, disk change with VFS refresh, read-only, observers, `settle` · `testing/src/main/java/etalii/adp/testing/DesignerDriver.java`
- [x] **T047** [P] FreeMind Structure view elements over `NodeKey`: node text with its first icon glyph, children in document order, built on `AdpStructureView` · `freemind/src/main/java/etalii/adp/freemind/ui/MindMapStructureView.java`

**⟶ Wait for Wave 6 to finish, then:**

- [x] **T048** Register the strip filter and the core actions with their `$default` shortcuts (`Ctrl+A`, `Ctrl+=`, `Ctrl+-`, `Ctrl+0`) · `core/src/main/resources/META-INF/adp-core.xml`

**⟶ Wait for T048, then:**

- [x] **T049** Run `./gradlew :core:test :freemind:test`: T019 to T031 green, including every reference scenario · (no file)

**Checkpoint**: framework, format layer and test kit ready; the undo bridge and Structure view sync are proved for a non-text editor.

---

## Phase 3: User Story 1 — Open and view a mind map (P1) 🎯 MVP

**Goal**: a FreeMind `.mm` file opens in the designer in any IntelliJ Platform IDE and is shown as spec 001 defines; other `.mm` files are left alone.

**Independent Test**: install into IntelliJ IDEA and Rider, open each vendored example, compare with spec 001's display rules; open a non-FreeMind `.mm`.

Files: `freemind/src/main/java/etalii/adp/freemind/{FreeMindSniffer,FreeMindFileType,FreeMindFileTypeDetector}.java`,
`freemind/src/main/java/etalii/adp/freemind/ui/{MindMapEditorProvider,MindMapDesigner,MindMapCanvas,MindMapLayout,NodePainter,LinkOpener}.java`,
`freemind/src/main/resources/META-INF/adp-freemind-editor.xml`.

### Tests (write first, they fail)

Test files: `freemind/src/test/java/etalii/adp/freemind/FreeMindSnifferTest.java`,
`freemind/src/test/java/etalii/adp/freemind/ui/{RegistrationTest,LayoutTest,NodeDetailsTest,ViewerInteractionTest,FormatProblemTest,TextVisualSyncTest,TwoEditorsTest,ThemeTest,OpenPerformanceTest}.java`,
`src/integrationTest/java/etalii/adp/it/OpenMapIntegrationTest.java`.

**Wave 1 — independent (different files):**

- [x] **T050** [P] [US1] Sniffer: BOM, XML declaration, comments, `<map version=…>` accepted; Objective-C++ source, empty, unreadable, `<map>` without version, content beyond 4 KB rejected · `freemind/src/test/java/etalii/adp/freemind/FreeMindSnifferTest.java`
- [x] **T051** [P] [US1] Port `RegistrationTest`: every example opens with editor type `etalii.adp.freemind.editor` in preview layout; a non-FreeMind `.mm` is not offered the designer; the text layout shows the same `Document` (FR-002, FR-003) · `freemind/src/test/java/etalii/adp/freemind/ui/RegistrationTest.java`
- [x] **T052** [P] [US1] Port `LayoutTest`: root centred, sides, `HGAP`/`VGAP`/`VSHIFT`, folded branches hidden · `freemind/src/test/java/etalii/adp/freemind/ui/LayoutTest.java`
- [x] **T053** [P] [US1] Port `NodeDetailsTest`: icons, colours, fonts, arrow links, link and note indicators, note tooltip, URL and relative-path links opened through `BrowserUtil` and `FileEditorManager` · `freemind/src/test/java/etalii/adp/freemind/ui/NodeDetailsTest.java`
- [x] **T054** [P] [US1] Port `ViewerInteractionTest`: click and multi-select, keyboard focus, pan, zoom steps and limits, Select All · `freemind/src/test/java/etalii/adp/freemind/ui/ViewerInteractionTest.java`
- [x] **T055** [P] [US1] Port `FormatProblemTest`: malformed XML, no `<map>`, zero or two top nodes, DOCTYPE each open as text with message, line and column, and the file bytes are unchanged (FR-009, AS-4) · `freemind/src/test/java/etalii/adp/freemind/ui/FormatProblemTest.java`
- [x] **T056** [P] [US1] Port `TextVisualSyncTest`: a typed change in the text layout updates the designer; breaking the XML shows the problem panel, fixing it restores the map with selection kept · `freemind/src/test/java/etalii/adp/freemind/ui/TextVisualSyncTest.java`
- [x] **T057** [P] [US1] Port `TwoEditorsTest`: two editors on one file (split) stay in step · `freemind/src/test/java/etalii/adp/freemind/ui/TwoEditorsTest.java`
- [x] **T058** [P] [US1] Themes and scale: under Darcula every default colour has at least 3:1 contrast, a low-contrast file colour gets a plate, sizes follow `JBUI.scale` at 1.0 and 2.0 (FR-016, AS-5) · `freemind/src/test/java/etalii/adp/freemind/ui/ThemeTest.java`
- [x] **T059** [P] [US1] Port the open half of `PerformanceTest`: a generated 1,000-node map opens and lays out within 2 s (SC-004) · `freemind/src/test/java/etalii/adp/freemind/ui/OpenPerformanceTest.java`
- [x] **T060** [P] [US1] **Proving test (R12)**, Starter + Driver, parameterised over IntelliJ IDEA (without and, where the environment has one, with an Ultimate trial, else skipped with a reason), Rider, WebStorm, PyCharm 2026.2: install the built zip, open a project with one example map and one Objective-C++ `.mm`, assert the map opens in the designer and the other does not (FR-001, SC-001 automated part) · `src/integrationTest/java/etalii/adp/it/OpenMapIntegrationTest.java`

### Implementation

**⟶ Wait for Wave 1 to finish, then Wave 2 — independent (different files):**

- [x] **T061** [P] [US1] Content sniffer per contracts/plugin-contributions.md, never throws · `freemind/src/main/java/etalii/adp/freemind/FreeMindSniffer.java`
- [x] **T062** [P] [US1] "FreeMind Mind Map" file type, default extension `mm`, not bound to the extension · `freemind/src/main/java/etalii/adp/freemind/FreeMindFileType.java`
- [x] **T063** [P] [US1] Port `MindMapLayout` to produce `NodeView` boxes, sizes through `JBUI.scale` · `freemind/src/main/java/etalii/adp/freemind/ui/MindMapLayout.java`
- [x] **T064** [P] [US1] Paint a `NodeView`: box, text, file or theme colours with the 3:1 contrast plate, `FontSpec` or `JBFont`, icon glyphs, indicators, connectors, arrow links as cubic curves with arrowheads, selection and focus using `JBColor` · `freemind/src/main/java/etalii/adp/freemind/ui/NodePainter.java`
- [x] **T065** [P] [US1] Port `LinkOpener`: URLs through `BrowserUtil.browse`, relative paths through `FileEditorManager.openFile` · `freemind/src/main/java/etalii/adp/freemind/ui/LinkOpener.java`

**⟶ Wait for Wave 2 to finish, then Wave 3 — independent (different files):**

- [x] **T066** [P] [US1] `fileTypeDetector` returning `FreeMindFileType` when the sniffer accepts · `freemind/src/main/java/etalii/adp/freemind/FreeMindFileTypeDetector.java`
- [x] **T067** [P] [US1] Swing canvas over the layout: paint, hit-test, click and multi-select, keyboard focus and navigation, pan, zoom, note tooltips, link click through `LinkOpener`, `viewOf(key)`, view-only (no edits) · `freemind/src/main/java/etalii/adp/freemind/ui/MindMapCanvas.java`

**⟶ Wait for Wave 3 to finish, then:**

- [x] **T068** [US1] `MindMapDesigner extends AdpDesignerEditor<MindMap>`: `parse` via `MindMapParser`, `createView` returns the canvas in the scroll pane, `modelChanged` keeps selection and expansion by `NodeKey`, `reveal` expands folded ancestors in view state only and scrolls, `getStructureViewBuilder` returns `MindMapStructureView`, `installActions(canvas, "etalii.adp.freemind.DesignerPopup")` · `freemind/src/main/java/etalii/adp/freemind/ui/MindMapDesigner.java`

**⟶ Wait for T068, then:**

- [x] **T069** [US1] `MindMapEditorProvider extends AdpEditorProvider`: extensions `{mm}`, `FreeMindSniffer`, editor type id `etalii.adp.freemind.editor`, name "FreeMind Mind Map" · `freemind/src/main/java/etalii/adp/freemind/ui/MindMapEditorProvider.java`

**⟶ Wait for T069, then:**

- [x] **T070** [US1] Register the editor provider, file type and detector · `freemind/src/main/resources/META-INF/adp-freemind-editor.xml`

**⟶ Wait for T070, then:**

- [x] **T071** [US1] Run T050 to T059 with `./gradlew test` and T060 with `./gradlew integrationTest`. If Rider does not offer the designer, stop: the R12 fallback (frontend content module) changes `plugin.xml` and `build.gradle.kts`, which Phase 1 owns, so it goes back through the plan · (no file)

**Checkpoint**: User Story 1 works on its own: maps open and display in IntelliJ IDEA, Rider, WebStorm and PyCharm.

---

## Phase 4: User Story 2 — Edit with the IDE's own undo, redo and save (P2)

**Goal**: every spec 001 editing action from keyboard, context menu and mouse, each one named step in the IDE's Undo, byte-identical to the reference results.

**Independent Test**: in IntelliJ IDEA and Rider, perform every action on an example map, undo each through Edit menu and shortcut, save, diff against the recorded reference.

Files: `freemind/src/main/java/etalii/adp/freemind/ui/actions/{MindMapAction,AddChildAction,AddSiblingAction,RenameAction,DeleteAction,MoveUpAction,MoveDownAction,IndentAction,OutdentAction,ToggleFoldAction}.java`,
`freemind/src/main/java/etalii/adp/freemind/ui/{InPlaceRename,DragMove,EditingInstaller}.java`,
`freemind/src/main/resources/META-INF/adp-freemind-editing.xml`.

### Tests (write first, they fail)

Test files: `freemind/src/test/java/etalii/adp/freemind/ui/{AddNodeTest,RenameTest,DeleteTest,MoveNodeTest,DragMoveTest,FoldTest,ContextMenuTest,KeymapTest,UndoRedoTest,InterleavedUndoTest,SaveLifecycleTest,RoundTripTest,PreservationTest,ExternalChangeTest,ReadOnlyTest,EditPerformanceTest,FreeMindCompatibilityTest}.java`,
`src/integrationTest/java/etalii/adp/it/EditUndoIntegrationTest.java`.

**Wave 1 — independent (different files):**

- [x] **T072** [P] [US2] Port `AddNodeTest`: add child (also into a self-closing node) and sibling, new node selected, "Undo Add Child Node" (AS-1) · `freemind/src/test/java/etalii/adp/freemind/ui/AddNodeTest.java`
- [x] **T073** [P] [US2] Port `RenameTest`: F2 and double-click start in-place rename, Enter commits, Escape cancels, rich-text node asks first and Cancel changes nothing · `freemind/src/test/java/etalii/adp/freemind/ui/RenameTest.java`
- [x] **T074** [P] [US2] Port `DeleteTest`: one and several nodes, arrow links to removed IDs removed, root refused with its reason · `freemind/src/test/java/etalii/adp/freemind/ui/DeleteTest.java`
- [x] **T075** [P] [US2] Port `MoveNodeTest`: move up and down, indent, outdent, `POSITION` fixed on entering and leaving the first level · `freemind/src/test/java/etalii/adp/freemind/ui/MoveNodeTest.java`
- [x] **T076** [P] [US2] Port `DragMoveTest`: drag before, after, onto; drop into own subtree rejected; root not draggable · `freemind/src/test/java/etalii/adp/freemind/ui/DragMoveTest.java`
- [x] **T077** [P] [US2] Port `FoldTest`: Space folds and unfolds with `FOLDED` written; on a read-only file it changes view state only · `freemind/src/test/java/etalii/adp/freemind/ui/FoldTest.java`
- [x] **T078** [P] [US2] Port `ContextMenuTest`: `DesignerPopup` contents and order per contract, separators, enablement follows selection · `freemind/src/test/java/etalii/adp/freemind/ui/ContextMenuTest.java`
- [x] **T079** [P] [US2] Keymap: every action in the contract table is in `$default` with its shortcut; a rebound shortcut runs the action (AS-7, FR-013); Tab, Enter, Space and Delete in a plain text editor do not run designer actions · `freemind/src/test/java/etalii/adp/freemind/ui/KeymapTest.java`
- [x] **T080** [P] [US2] Port `UndoRedoTest`: every action type's undo label, undo and redo through `$Undo`/`$Redo` with the designer focused; each reference scenario run through the designer matches its recorded bytes and undo restores the original (FR-004, SC-003) · `freemind/src/test/java/etalii/adp/freemind/ui/UndoRedoTest.java`
- [x] **T081** [P] [US2] Port `InterleavedUndoTest`: edits in the designer and the text layout share one history in order (AS-4) · `freemind/src/test/java/etalii/adp/freemind/ui/InterleavedUndoTest.java`
- [x] **T082** [P] [US2] Port `SaveLifecycleTest`: modified marker, save, close with and without changes, Local History records the change (FR-005) · `freemind/src/test/java/etalii/adp/freemind/ui/SaveLifecycleTest.java`
- [x] **T083** [P] [US2] Port `RoundTripTest`: every example opened and saved without edits is byte-identical through the platform, including CRLF and lone-CR files and "strip trailing spaces" enabled (FR-006, SC-002, AS-2) · `freemind/src/test/java/etalii/adp/freemind/ui/RoundTripTest.java`
- [x] **T084** [P] [US2] Port `PreservationTest`: attributes, clouds, edges, hooks and unknown content survive every action; only the edited ranges differ (FR-007) · `freemind/src/test/java/etalii/adp/freemind/ui/PreservationTest.java`
- [x] **T085** [P] [US2] Port `ExternalChangeTest`: disk change on a clean file reloads, on a modified file the IDE asks, as for a text file (AS-5) · `freemind/src/test/java/etalii/adp/freemind/ui/ExternalChangeTest.java`
- [x] **T086** [P] [US2] Port `ReadOnlyTest`: map shown, banner with reason, every edit action disabled with its reason (FR-010, AS-6) · `freemind/src/test/java/etalii/adp/freemind/ui/ReadOnlyTest.java`
- [x] **T087** [P] [US2] Port the edit half of `PerformanceTest`: on a 1,000-node map, add, rename, fold and delete show within 0.1 s (SC-004) · `freemind/src/test/java/etalii/adp/freemind/ui/EditPerformanceTest.java`
- [x] **T088** [P] [US2] Port `FreeMindCompatibilityTest`: with `FREEMIND_HOME` set, every reference result and a new-map text load in FreeMind 1.0.1's reader; skipped with a reason otherwise · `freemind/src/test/java/etalii/adp/freemind/ui/FreeMindCompatibilityTest.java`
- [x] **T089** [P] [US2] Starter + Driver over the same products as T060: Add Child Node then Undo returns the file to its original bytes · `src/integrationTest/java/etalii/adp/it/EditUndoIntegrationTest.java`

### Implementation

**⟶ Wait for Wave 1 to finish, then Wave 2 — independent (different files):**

- [x] **T090** [P] [US2] Action base: reads `ADP_DESIGNER` and the selection from the `DataContext`, disables outside a focused designer, when not editable, or for the root, with the reason as description; builds the `Edit` and calls `designer.execute(label, changes)` · `freemind/src/main/java/etalii/adp/freemind/ui/actions/MindMapAction.java`
- [x] **T091** [P] [US2] In-place rename overlay (`JBTextField` over the node's `NodeView` bounds), Enter commits, Escape and focus loss cancel, rich-text confirmation with `Messages.showOkCancelDialog` (replaces `NodeRenameManager`) · `freemind/src/main/java/etalii/adp/freemind/ui/InPlaceRename.java`
- [x] **T092** [P] [US2] Drag-and-drop move on the canvas: drop feedback before, after, onto; own subtree and root refused; drop builds `MindMapEdits.move` and executes it · `freemind/src/main/java/etalii/adp/freemind/ui/DragMove.java`

**⟶ Wait for Wave 2 to finish, then Wave 3 — independent (different files):**

- [x] **T093** [P] [US2] Add Child Node · `freemind/src/main/java/etalii/adp/freemind/ui/actions/AddChildAction.java`
- [x] **T094** [P] [US2] Add Sibling Node · `freemind/src/main/java/etalii/adp/freemind/ui/actions/AddSiblingAction.java`
- [x] **T095** [P] [US2] Rename Node, opening `InPlaceRename` · `freemind/src/main/java/etalii/adp/freemind/ui/actions/RenameAction.java`
- [x] **T096** [P] [US2] Delete Node / Delete Nodes · `freemind/src/main/java/etalii/adp/freemind/ui/actions/DeleteAction.java`
- [x] **T097** [P] [US2] Move Node Up · `freemind/src/main/java/etalii/adp/freemind/ui/actions/MoveUpAction.java`
- [x] **T098** [P] [US2] Move Node Down · `freemind/src/main/java/etalii/adp/freemind/ui/actions/MoveDownAction.java`
- [x] **T099** [P] [US2] Move Under Previous Sibling · `freemind/src/main/java/etalii/adp/freemind/ui/actions/IndentAction.java`
- [x] **T100** [P] [US2] Move Up a Level · `freemind/src/main/java/etalii/adp/freemind/ui/actions/OutdentAction.java`
- [x] **T101** [P] [US2] Fold / Unfold Branch: edit when editable, view-state toggle when read-only · `freemind/src/main/java/etalii/adp/freemind/ui/actions/ToggleFoldAction.java`
- [x] **T102** [P] [US2] `FileEditorManagerListener` that attaches `DragMove` and double-click `InPlaceRename` to each opened `MindMapDesigner`'s canvas and detaches on dispose. Prove first that `fileOpened` fires under `DesignerDriver.open` (T073, T076) · `freemind/src/main/java/etalii/adp/freemind/ui/EditingInstaller.java`

**⟶ Wait for Wave 3 to finish, then:**

- [x] **T103** [US2] Register the nine actions with ids, texts and `$default` shortcuts per contract, the `etalii.adp.freemind.DesignerPopup` group (table order, separators after AddSibling, Delete and Outdent, then the core zoom actions), and `EditingInstaller` as a project listener · `freemind/src/main/resources/META-INF/adp-freemind-editing.xml`

**⟶ Wait for T103, then:**

- [x] **T104** [US2] Run T072 to T088 with `./gradlew test` and T089 with `./gradlew integrationTest` · (no file)

**Checkpoint**: User Story 2 works on its own on top of US1: every edit is one named undo step and byte-identical to spec 001.

---

## Phase 5: User Story 3 — Create and navigate maps the IDE way (P3)

**Goal**: New > FreeMind Mind Map, and the Structure view in step with the designer both ways.

**Independent Test**: create a map through New and open it in FreeMind 1.0.1; open a large example and navigate from the Structure view.

Files: `freemind/src/main/java/etalii/adp/freemind/ui/NewMindMapAction.java`,
`freemind/src/main/resources/META-INF/adp-freemind-new.xml`. The Structure view itself was built
and proved in Phase 2 (T030, T043, T047); this phase proves it against the real designer.

### Tests (write first, they fail)

Test files: `freemind/src/test/java/etalii/adp/freemind/ui/{NewMapTest,StructureViewTest}.java`.

**Wave 1 — independent (different files):**

- [x] **T105** [P] [US3] Port `NewWizardTest`: the action is in `NewGroup` after `NewFile`; `.mm` appended when missing; an existing name refused; content matches FreeMind 1.0.1's new-map form with fresh `ID` and timestamps and the project's line separator; the file opens in the designer (FR-014, AS-1) · `freemind/src/test/java/etalii/adp/freemind/ui/NewMapTest.java`
- [x] **T106** [P] [US3] Port `OutlineTest`: the Structure view lists the tree with text and first icon; selecting an element inside a folded branch reveals and selects it without changing the file; designer selection becomes the current element (FR-015, AS-2) · `freemind/src/test/java/etalii/adp/freemind/ui/StructureViewTest.java`

### Implementation

**⟶ Wait for Wave 1 to finish, then:**

- [x] **T107** [US3] New file action: name prompt, `.mm` appended, refusal on existing name, content from `FreeMindConventions.newMapText`, open in the designer (replaces `NewMindMapWizard`) · `freemind/src/main/java/etalii/adp/freemind/ui/NewMindMapAction.java`

**⟶ Wait for T107, then:**

- [x] **T108** [US3] Register `etalii.adp.freemind.NewMindMap` in `NewGroup`, anchor after `NewFile` · `freemind/src/main/resources/META-INF/adp-freemind-new.xml`

**⟶ Wait for T108, then:**

- [x] **T109** [US3] Run T105 and T106 · (no file)

**Checkpoint**: User Story 3 works on its own on top of US1.

---

## Phase 6: User Story 4 — A codebase with no previous host left (P3)

**Goal**: one headless build produces the plug-in and runs every test; nothing refers to the previous host.

**Independent Test**: from a clean checkout, `./gradlew build`, then search the repository, build output and plug-in zip.

Files: `src/integrationTest/java/etalii/adp/it/NoPreviousHostIntegrationTest.java`,
`freemind/src/test/java/etalii/adp/freemind/InventoryCoverageTest.java`, `LICENSE`, `README.md`,
`CLAUDE.md`, `specs/001-freemind-mindmap-designer/.spec-context.json`, and the removal of
`pom.xml`, `.mvn/`, `bundles/`, `features/`, `releng/`, `tests/`,
`specs/001-freemind-mindmap-designer/{plan.md,research.md,data-model.md,contracts/,tasks.md}`.

### Tests (write first, they fail)

**Wave 1 — independent (different files):**

- [x] **T110** [P] [US4] Search the tracked files (excluding `specs/002-jetbrains-ide-support/`), the built zip's entries and contents, and the sandbox's installed plug-in for the previous host's name, case-insensitive; fail on any hit (FR-017, SC-005) · `src/integrationTest/java/etalii/adp/it/NoPreviousHostIntegrationTest.java`
- [x] **T111** [P] [US4] Read `spec001-test-inventory.md` and fail when a row's new test class or method does not exist (FR-018, SC-007) · `freemind/src/test/java/etalii/adp/freemind/InventoryCoverageTest.java`

**⟶ Wait for Wave 1 to finish, then:**

- [x] **T112** [US4] **Removal gate**: `InventoryCoverageTest`, `ReferenceReplayTest`, `UndoRedoTest` and both earlier integration tests green (only T110 may still fail). Commit. Nothing is removed before this passes (FR-008, FR-018) · (no file)

### Implementation

**⟶ Wait for T112, then Wave 2 — independent (different files):**

- [x] **T113** [P] [US4] Delete the previous build and code with `git rm -r`: `pom.xml`, `.mvn/`, `bundles/`, `features/`, `releng/`, `tests/` · (removed paths)
- [x] **T114** [P] [US4] Delete spec 001's host-specific artifacts: `plan.md`, `research.md`, `data-model.md`, `contracts/`, `tasks.md`; keep `spec.md` and `checklists/` (FR-017a) · `specs/001-freemind-mindmap-designer/`
- [x] **T115** [P] [US4] Remove previous-host references from spec 001's context (step summaries, file lists, coverage task ids pointing at deleted tasks) with the companion writer where it has a field for it, keeping the file valid JSON (FR-017a) · `specs/001-freemind-mindmap-designer/.spec-context.json`
- [x] **T116** [P] [US4] Apache-2.0 full text (FR-022) · `LICENSE`
- [x] **T117** [P] [US4] Rewrite for the IntelliJ plug-in: what it is, supported IDEs and release, install from disk, `./gradlew build`/`test`/`integrationTest`/`runIde`, Apache-2.0, and a licence table of every build and test dependency checked for Apache-2.0 compatibility (R14) · `README.md`
- [x] **T118** [P] [US4] Rewrite: title `EtAlii.Adp.IntelliJ`, IntelliJ Platform plug-in description, the Spec Kit flow, conventions (Gradle build, PowerShell Spec Kit scripts, commit trailer) · `CLAUDE.md`

**⟶ Wait for Wave 2 to finish, then:**

- [x] **T119** [US4] Run `git grep -i` for the previous host's name outside `specs/002-jetbrains-ide-support/` (expect none), then `./gradlew integrationTest --tests '*NoPreviousHost*'` green · (no file)

**Checkpoint**: User Story 4 done: a clean checkout builds and tests with Gradle alone, and the previous host is gone.

---

## Phase 7: Polish & validation

**Wave 1 — independent (different files):**

- [x] **T120** [P] No runtime network access: `core` and `freemind` main sources use no `java.net` connection, socket or `HttpClient` API; `BrowserUtil.browse` in `LinkOpener` is the only outward call (FR-021) · `freemind/src/test/java/etalii/adp/freemind/NoNetworkAccessTest.java`
- [x] **T121** [P] Reconcile the plan's source tree with what was built: `NodeView` and `AdpStructureView` in `core`, the four descriptor fragments, `MindMapAction`, `InPlaceRename`, `DragMove`, `EditingInstaller`, and the split performance and integration tests · `specs/002-jetbrains-ide-support/plan.md`

**⟶ Wait for Wave 1 to finish, then:**

- [x] **T122** Run the full `./gradlew build` from a clean checkout: all format, platform and integration tests, `verifyPluginProjectConfiguration` and `verifyPlugin` for the eight IDEs, zip produced. Validate SC-002, SC-003, SC-004, SC-005, SC-007 and FR-020 from the results · (no file)

**⟶ Wait for T122, then:**

- [ ] **T123** Manual SC-001: install the zip from disk into IntelliJ IDEA (without and with an Ultimate trial), Rider, WebStorm and PyCharm; open every example map; run the quickstart §3 table; record results · `specs/002-jetbrains-ide-support/validation.md`

**⟶ Wait for T123, then:**

- [ ] **T124** Manual SC-006 walkthrough with a developer new to the designer, and the FreeMind 1.0.1 hand check of a few saved maps (quickstart §4); append results · `specs/002-jetbrains-ide-support/validation.md`

**⟶ Wait for T124, then:**

- [ ] **T125** Maintainer, outside the build: rename the repository folder and any remote to `EtAlii.Adp.IntelliJ` (R13) · (no file)

---

## Dependencies & Execution Order

**Phases**: Setup → Foundational → US1 → (US2 ∥ US3) → US4 → Polish.

- **US1** depends on Foundational. **US2** and **US3** depend on US1 (they drive `MindMapDesigner`)
  and share no files, so they can run in parallel. **US4** depends on US2 and US3: its removal
  gate needs every inventory row covered. **Polish** depends on all stories.
- **Phase 1**: Wave 1 (T001–T003) → T004 → T005 → Wave 2 (T006–T015) → T016. T004 must finish, and
  T005 commit, before anything else.
- **Phase 2**: Wave 1 (T017–T019) → Wave 2 (T020–T031) → Wave 3 (T032–T038) → Wave 4 (T039–T040) →
  Wave 5 (T041–T044) → Wave 6 (T045–T047) → T048 → T049.
- **Phase 3 (US1)**: Wave 1 tests (T050–T060) → Wave 2 (T061–T065) → Wave 3 (T066–T067) → T068 →
  T069 → T070 → T071.
- **Phase 4 (US2)**: Wave 1 tests (T072–T089) → Wave 2 (T090–T092) → Wave 3 (T093–T102) → T103 → T104.
- **Phase 5 (US3)**: Wave 1 tests (T105–T106) → T107 → T108 → T109.
- **Phase 6 (US4)**: Wave 1 tests (T110–T111) → T112 removal gate → Wave 2 (T113–T118) → T119.
- **Phase 7**: Wave 1 (T120–T121) → T122 → T123 → T124 → T125.
