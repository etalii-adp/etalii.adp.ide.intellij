# Implementation Plan: Diagram Designer Framework

**Branch**: `003-diagram-designer-framework` | **Date**: 2026-09-25 | **Spec**: [diagram-designer-framework.spec.md](diagram-designer-framework.spec.md)

**Input**: Feature specification from `specs/003-diagram-designer-framework/diagram-designer-framework.spec.md`, the library survey beside it, and the clarification of FR-031 (all three proving designers).

> **Scale note**: this change adds about 45 production files to `core`, rebuilds the FreeMind designer's view layer on them (about 10 files replaced, 8 changed), adds a `drawio` module (about 8 files) and a test-only sample designer, and extends the test kit. Roughly 60 production files and 40 test files. Watch three seams. First, the FreeMind migration must keep every spec 001/002 test and reference replay green: the tests are the safety net, so their expectations do not change, only how they read the view (research R19). Second, the draw.io module is built last and must not change `core`. If it needs to, that is a framework gap and is fixed there first, with the sample designer re-verified (principle III). Third, SC-005 performance with routing on 1,500 connections (R12). Each gets an early proving task.

## Summary

A designer author declares a diagram designer in Java: element types, connection types, anchors, text slots, property declarations, sectors, view options and rules. They also supply a mapping that reads the file into a diagram and turns each diagram change into range-exact text changes. The framework in `core` does the rest. That covers a Java2D canvas with drawing, hit-testing, selection, move, resize, connect, in-place editing, pan and zoom, orthogonal routing and refusal feedback, plus a Toolbox tool window and one ADP Properties tool window shared by every designer. Every gesture becomes one named `WriteCommandAction` through the existing `AdpDesignerEditor.execute`, so undo, dirty state, save, reload and read-only stay the platform's.

Three designers prove it (FR-031). A test-only sample designer exercises every capability. The FreeMind designer is migrated onto the framework, with its tree layout plugged in as a layout provider. A new `drawio` module edits uncompressed draw.io files. Following the library survey, no third-party runtime library is added (FR-032). A new Gradle check fails the build if one ever is without an allowed licence (SC-006).

Stack unchanged from spec 002: Java 25, IntelliJ Platform 2026.2 (`since-build` 262, `com.intellij.modules.platform` only), Gradle 9.8 with the IntelliJ Platform Gradle Plugin 2.19, JUnit 5 with vintage and the platform test framework.

## Technical Context

**Language/Version**: Java 25, Gradle Kotlin DSL for build scripts.

**Primary Dependencies**: IntelliJ Platform 2026.2 only. Swing and Java2D for the canvas; `JBColor`, `JBUI`, `JBTable`, `ColorPanel`, `ComboBox`, `DnDManager`, `JBPopupFactory` and `ToolWindowFactory` from the platform. No new third-party runtime library (research R2).

**Storage**: each designer's file, through the platform `Document`. View state (zoom, scroll, selection) in `FileEditorState`, never in the file.

**Testing**: pure JUnit 5 for definitions, geometry, routing, mapping reads and edits; headless `FileEditorManagerTestCase` tests driven by the extended `DesignerDriver`; the existing Starter + Driver integration suite gains one draw.io scenario.

**Target Platform**: every IDE on IntelliJ Platform 2026.2, Windows, Linux, macOS.

**Project Type**: IntelliJ Platform plug-in, Gradle multi-module: `core`, `freemind`, `drawio` (new), `testing`.

**Performance Goals**: SC-005. A 1,000-element, 1,500-connection diagram opens within 2 s. A single edit shows within 100 ms.

**Constraints**: byte-identical round trips and minimal edits (FR-028, principle II); unknown content kept (FR-029); edits never rewrite the file (FR-030, see R6); Apache-2.0-compatible dependencies verified by the build (SC-006); the draw.io module changes nothing in `core` (principle III).

**Scale/Scope**: diagrams up to a few thousand elements; three designers; about 60 production files touched.

## Constitution Check

*GATE: checked before Phase 0 research and re-checked after Phase 1 design (constitution v2.0.0). Both pass, with one justified deviation.*

| Principle | Assessment | How the design meets it |
|---|---|---|
| I. Native IntelliJ Platform Citizenship | PASS | Designers keep `AdpEditorProvider`'s `fileEditorProvider` registration and `TextEditorWithPreview`. Every gesture and every property edit is one `WriteCommandAction` via `execute` (R17). Toolbox and ADP Properties are `ToolWindowFactory` tool windows (R13, R14). Drag from the toolbox uses `DnDManager`. Refusals use `JBPopupFactory` balloons (R9). Actions, shortcuts and the Delete key are registered actions in the keymap. Colours use `JBColor` tones, sizes use `JBUI` (R10). |
| II. Text Is the Source of Truth | PASS | The diagram is re-read from the document after every change and never saved on its own (R5). Mappings return range-exact `TextChanges` built with the shared XML edit helpers (R20). Unknown element and connection types are preserved and drawn as placeholders (R18). Unknown attributes and elements are never touched. A compressed draw.io page or an unreadable file opens in the text view with an explanation. |
| III. One Framework, Many Designers | PASS | The framework lives in `core` and knows no format. `drawio` depends on `core` only and is built last without touching `core`. FreeMind and draw.io do not depend on each other. The XML scanner moves from `freemind` into `core` because two formats now need it (R3). |
| IV. Test-First, Against Real Files | PASS, one deviation | Tests come before code in every phase. FreeMind keeps its real example files and reference replay. draw.io vendors published templates under CC-BY-4.0 (permissive, not share-alike) with their licence (R20). The sample designer is a test fixture, not a shipped format, and uses hand-written files; see Complexity Tracking. Registration, undo and the tool windows are tested headless. |
| V. Simplicity | PASS | No new runtime dependency. Declarations are plain Java builders, not a declaration language (R4). The router is a small grid search, only run when a straight or one-bend route is blocked (R12). The framework stays in `core` rather than a new module (R3). Three designers are what the spec asks for. |
| Platform and technology constraints | PASS | One plug-in, `since-build` 262, platform module only. Java 25. Headless Gradle build runs every test, the licence check and the verifier. No runtime network access; the no-network test is extended to `drawio`. |

### Complexity Tracking

| Violation | Why needed | Simpler alternative rejected because |
|---|---|---|
| The sample designer's round-trip tests use hand-written files, not published ones (principle IV) | The sample designer exists only to exercise every framework capability in isolation, including ones no real format has in one file. It never ships and has no published files. | Using only FreeMind and draw.io files leaves capabilities such as view-space sectors, horizontal-only resize and read-only end labels untested, because neither format has them. |

## Project Structure

### Documentation (this feature)

```text
specs/003-diagram-designer-framework/
├── diagram-designer-framework.spec.md
├── library-survey.md          # input to research R2
├── plan.md                    # this file
├── research.md                # Phase 0 decisions R1–R23
├── data-model.md              # definition, diagram, change and view entities
├── quickstart.md              # validation guide and the ten-minute author walkthrough
├── contracts/
│   ├── diagram-framework.md   # the Java API a designer author codes against
│   ├── plugin-contributions.md# tool windows, actions, draw.io registration
│   └── test-kit.md            # DesignerDriver additions
└── tasks.md                   # Phase 2 (/speckit-tasks)
```

### Source Code (repository root, after the feature)

```text
settings.gradle.kts                   # + include("drawio")
build.gradle.kts                      # + pluginComposedModule(:drawio), + verifyDependencyLicences (R21)
gradle/allowed-licences.properties    # allowlist read by the licence check; empty of third-party entries today
src/main/resources/META-INF/plugin.xml# + xi:include of the four adp-diagram*.xml fragments and adp-drawio.xml
docs/diagram-designer-guide.md        # designer author guide (SC-002, R23)
core/src/main/java/etalii/adp/core/
├── AdpDesignerEditor.java, NodeView.java   # unchanged (R19)
├── actions/ZoomActions.java          # disabled when a diagram designer turns zoom off
├── xml/                              # XmlScanner (moved from freemind), XmlTree, XmlEdits (R3, R20)
└── diagram/
    ├── DiagramDefinition.java        # + builders: ElementType, ConnectionType, Anchor, TextSlot,
    │                                 #   PropertyDecl, SectorDecl, ViewOptions; DefinitionException
    ├── Outline.java, Tone.java, LineStyle.java, Dash.java, ArrowHead.java, Resize.java, EditorKind.java
    ├── DiagramMapping.java           # read + edit factory the designer supplies
    ├── DiagramLayout.java            # optional layout provider (FreeMind)
    ├── DiagramRules.java, Verdict.java, DiagramListener.java, DiagramChange.java
    ├── DiagramFeature.java           # extension point: a story's tools and layers for the canvas
    ├── model/                        # Diagram, Element, Connection, End, Sector (immutable snapshots)
    ├── edit/                         # DiagramCommands (rules → mapping → execute), DiagramDiff,
    │                                 #   selection, move, resize, connect tools, DeleteAction
    ├── view/                         # DiagramDesigner, DiagramEditorProvider, DiagramCanvas,
    │                                 #   ElementPainter, ConnectionPainter, TextBlock, Router,
    │                                 #   ArrowHeads, AnchorGeometry, ElementMeasure, CanvasLayer, CanvasTool, ElementView,
    │                                 #   ConnectionView, DiagramStructureView
    ├── toolbox/                      # ToolboxToolWindowFactory, ToolboxPanel, ToolboxDragSource
    ├── properties/                   # PropertiesToolWindowFactory, PropertyPanel, PropertyTableModel,
    │                                 #   PropertyEditors, InPlaceEditor, EditInPlace and ShowProperties actions
    └── navigation/                   # SectorLayer, PanTool, WheelZoomTool, NavigationFeature
core/src/main/resources/META-INF/
├── adp-diagram.xml                   # the DiagramFeature extension point
├── adp-diagram-editing.xml           # US2: editing feature, Toolbox, Delete, popup group
├── adp-diagram-properties.xml        # US3: properties feature, ADP Properties, EditInPlace, ShowProperties
└── adp-diagram-navigation.xml        # US4: pan, wheel zoom and sectors feature
core/src/test/java/etalii/adp/core/diagram/
├── sample/                           # SampleDefinition, SampleMapping, SampleProvider (test-only designer)
└── ...                               # definition, geometry, router, diff, canvas, toolbox, panel tests
core/testdata/sample/*.adpsample      # hand-written sample files, incl. a generated 1,000/1,500 file
freemind/src/main/java/etalii/adp/freemind/
├── parse/XmlScanner.java             # removed (moved to core/xml)
├── edit/MindMapEdits.java            # + setAttribute, addArrowLink, removeArrowLink
└── ui/
    ├── FreeMindDefinition.java       # new: node, root, branch edge, arrow link types
    ├── FreeMindMapping.java          # new: adapter over MindMapParser and MindMapEdits
    ├── MindMapLayout.java            # implements DiagramLayout
    ├── MindMapDesigner.java          # extends DiagramDesigner; fold and tree navigation stay here
    ├── MindMapEditorProvider.java    # extends DiagramEditorProvider
    └── (removed) MindMapCanvas, NodePainter, DragMove, InPlaceRename, EditingInstaller
drawio/                               # new module, depends on core only
├── build.gradle.kts
├── src/main/java/etalii/adp/drawio/
│   ├── DrawioFileType.java, DrawioSniffer.java, DrawioEditorProvider.java
│   ├── DrawioDefinition.java         # vertex shapes, swimlane sector, edge type
│   ├── DrawioMapping.java            # mxfile/mxGraphModel ↔ diagram, edits via core/xml
│   └── Style.java                    # draw.io style string: read and minimal key edits
├── src/main/resources/META-INF/adp-drawio.xml
├── src/test/java/etalii/adp/drawio/  # round trip, mapping, edits, registration, code size
└── testdata/examples/<template>.drawio + .LICENSE
testing/src/main/java/etalii/adp/testing/DiagramDriver.java    # new: toolbox, connect, resize, panel, refusal
```

**Structure Decision**: the framework is a set of packages in the existing `core` module, next to the editor base it builds on. `drawio` is a new format module shaped like `freemind`. The sample designer lives in `core`'s test sources, because it only exists to test `core`.

## Design in one page

- **Declaring.** A designer builds one `DiagramDefinition` with fluent builders. `build()` checks it and throws a `DefinitionException` that lists every problem with the declaration it concerns (FR-002). The provider builds it in its constructor, so a broken definition fails when the IDE loads the designer, and in the designer's own definition test.
- **Reading.** `DiagramDesigner.parse` calls `mapping.read(text)` and gets an immutable `Diagram`: elements, connections and sectors, each with a stable key, a type id, texts and property values, and for positioned formats their bounds. Unknown type ids become placeholders (R18). A `FormatProblem` shows the text view, as today.
- **Laying out and drawing.** Bounds come from the file, or from the designer's `DiagramLayout` (FreeMind). Auto-sized elements are measured from their text slots. `DiagramCanvas` paints sectors, connections, elements, handles and feedback in unzoomed coordinates under a zoom transform, culled to the clip. Routes are cached per connection and recomputed only for connections near a change (R12).
- **Editing.** Gestures (toolbox drop, drag from an anchor, move, resize, delete, in-place edit, a panel edit) go to `DiagramCommands`. It asks the definition's permissions, then the designer's rules, then the mapping for `TextChanges`, then calls `execute(label, changes, reselect)`. That is one undo step. A refusal changes nothing and shows its reason (R9).
- **Reacting.** After every re-read, `DiagramDiff` compares the old and new diagram by key and tells the designer's `DiagramListener` what changed (FR-004). The same path serves edits, undo, redo and external changes.
- **Tool windows.** Toolbox and ADP Properties follow the selected editor. When it is a `DiagramDesigner` they show its definition's types and the current selection's declared properties. Otherwise they show an empty state.
- **Designers.** The sample designer covers every capability. FreeMind declares node, root, branch and arrow-link types, keeps its tree layout, folding and navigation, and loses its own canvas. draw.io declares its common vertex shapes, swimlanes as diagram-space sectors, and one edge type whose line, arrows and dash come from properties.

## Implementation phases (input to `/speckit-tasks`)

1. **Groundwork**: move `XmlScanner` to `core/xml` with `XmlTree` and `XmlEdits`; FreeMind tests still green. Licence check task. The SC-001 baseline is recorded as a constant (FreeMind main code, 3,423 non-blank, non-comment lines on 2026-09-25).
2. **Proving tests**: router and render timing on the generated 1,000/1,500 sample file (SC-005); a tool window following the selected composite editor; DnD from a tool window onto the preview side of `TextEditorWithPreview` in a headless test.
3. **User Story 1 (P1)**: definition builders and validation, model, mapping contract, layout, painters, text blocks, anchors, connection geometry and arrowheads, placeholders, `ElementView`; the sample designer's definition, mapping and viewing tests.
4. **User Story 2 (P1)**: `DiagramCommands`, rules and refusals, diff and listener, toolbox, gestures (select, marquee, move, drop onto, resize handles, connect from anchors, delete with cascade), undo of every gesture back to byte-identical.
5. **User Story 3 (P2)**: ADP Properties tool window, editor kinds, multi-select, in-place editing of text slots and connection labels.
6. **User Story 4 (P3)**: pan and zoom options, diagram-space and view-space sectors, sector membership on move.
7. **FreeMind migration**: definition, mapping adapter, layout provider, property declarations, toolbox rules; delete the old canvas classes; every existing FreeMind test and reference replay green, then the new FreeMind toolbox and panel tests.
8. **draw.io designer**: vendored examples, sniffer, file type, definition, mapping, round trips, scripted edits, code-size check (SC-001), one integration scenario. No `core` changes.
9. **Close**: designer author guide (SC-002), README, the SC-002 timed walkthrough by a developer new to the framework (manual).

## Risks carried into tasks

- **FR-030 wording.** The framework re-reads the in-memory document after each edit and never rewrites the file. It does not re-read from disk. A literal "no full re-parse" would need incremental parsing for every format, which no current requirement needs beyond SC-005's 100 ms. R6 records this reading. A `/speckit-clarify` pass can tighten the spec wording.
- **SC-001 on draw.io.** The budget is about 342 lines against the recorded baseline. Style strings and relative swimlane coordinates are the pressure points. If the budget is missed, the answer is more generic help in `core/xml`, never an exemption; the code-size test reports the number either way.
- **Another draw.io plug-in.** The community "Diagrams.net Integration" plug-in also claims `.drawio`. Both editors are then offered, as the platform does for any shared extension. Our provider only claims files whose content it sniffs as draw.io XML.
- **Compressed draw.io pages** are common in older files. They open in the text view with an explanation (R20). The vendored templates are compressed, so they are decompressed once by a recorded, repeatable step, and the licence file notes the change as CC-BY-4.0 requires.
- **Branch base.** Spec 003 builds on spec 002's IntelliJ `core`, which is not on `main` yet. The `003-diagram-designer-framework` branch must start from `002-jetbrains-ide-support` or from `main` after 002 merges.
