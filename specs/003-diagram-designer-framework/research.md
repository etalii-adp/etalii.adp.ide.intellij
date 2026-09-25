# Research: Diagram Designer Framework

Phase 0 decisions. The library survey (`library-survey.md`) is the input for R2. The codebase facts come from spec 002's `core` and `freemind` modules as they stand on the `002-jetbrains-ide-support` branch.

## R1. Proving designers and the second real format (FR-031)

**Decision**: three designers, as clarified on 2026-09-25. (1) A sample designer in `core`'s test sources, with its own small XML format (`.adpsample`), declaring two element types, two connection types, lanes and every option. (2) FreeMind migrated onto the framework. (3) A new `drawio` module for uncompressed draw.io files (`.drawio`).

**Rationale**: draw.io is the most widely used open diagram format that is positioned (every shape stores its bounds). It is plain XML, and its concepts map one to one onto the spec: shapes, dashed, curved and orthogonal edges, arrowheads at both ends, edge labels, and swimlanes. Its published templates are CC-BY-4.0, which is permissive and not share-alike, so principle IV's real-file rule can be met.

**Alternatives considered**: GraphML as written by yEd (positions live in yFiles-specific extensions, and published examples have unclear licences). Graphviz DOT (positions are optional and usually computed, and it is not XML, so no reuse of the XML helpers). PlantUML and Mermaid (no positions; they are text languages, not diagrams to edit visually).

## R2. Drawing and interaction library (FR-032)

**Decision**: build the canvas on Swing and Java2D with platform components. No third-party runtime library. JGraphX and GEF Classic are read as design references only.

**Rationale**: the survey found no maintained, Apache-compatible Swing library that does not own the model, undo stack or serialisation, which principles I and II forbid. The JCEF options turn every platform integration into a JavaScript bridge and are not supported on every runtime.

**Alternatives considered**: all of the survey's list; see its Recommendation and Reject sections. ELK stays in reserve for a later automatic-layout spec.

## R3. Where the framework lives

**Decision**: new packages under `etalii.adp.core` in the existing `core` module: `diagram` (definition API), `diagram.model`, `diagram.edit`, `diagram.view`, `diagram.toolbox`, `diagram.properties`, and `xml`. `XmlScanner` moves from `freemind/parse` to `core/xml` with two new helpers, `XmlTree` and `XmlEdits`.

**Rationale**: principle III puts designer infrastructure in the shared module. `core` is only about 1,150 lines today, and the diagram framework builds directly on `AdpDesignerEditor`. Two XML formats now need a scanner that reports attribute offsets, which no JDK parser does. The scanner has no platform imports, so the format-purity rule still holds.

**Alternatives considered**: a separate `diagram` Gradle module (one more module for one consumer layer, with no gain in isolation). Keeping the scanner in `freemind` and copying it into `drawio` (duplication, and a format depending on another format is forbidden).

## R4. How a designer is declared (FR-001, FR-002)

**Decision**: plain Java builders. `DiagramDefinition.builder(id)` takes element types, connection types, the toolbox order, sectors, view options, rules and a listener. `build()` checks the whole definition and throws `DefinitionException` with one message per problem, each naming the declaration, for example `connection type 'flow': anchor 'in' of element 'task' names undeclared connection type 'flw'`. The designer's `DiagramEditorProvider` builds its definition in its constructor. A broken definition therefore fails when the platform instantiates the provider, which logs a plug-in error naming it. Each designer also has a one-line definition test.

**Rationale**: rules and mappings are code anyway (spec assumptions), so a declaration file would add a parser and a schema for no user. Builders give completion and type checks while the author writes them.

**Alternatives considered**: an XML or JSON declaration file (a second language to validate and document). Annotations on model classes (they cannot express anchors, rules and tones readably). A custom extension point per designer (the provider already registers the designer, and the tool windows find the definition through the selected editor, so no new extension point is needed).

## R5. Diagram model and the mapping contract (FR-027, FR-028, FR-029)

**Decision**: `DiagramMapping` has one read method and one factory method per kind of change. `read(CharSequence)` returns an immutable `Diagram` or throws `FormatProblem`. The factory methods return `TextChanges` for adding, removing, moving and resizing, dropping onto a target, connecting, reconnecting, changing a property and changing sector. The framework owns the rest of the cycle: it calls the mapping, runs the result as one command, re-reads, diffs and repaints. Keys are the mapping's own stable objects (an id attribute, or a path when there is none), so selection survives re-reads.

**Rationale**: this is how the FreeMind designer already works (`MindMapEdits` returns `TextChanges`, `AdpDesignerEditor.execute` applies them), so the pattern is proven for byte-identical round trips and minimal edits. The file stays the only model. Undo and external changes need no special path.

**Alternatives considered**: a mutable diagram model that the framework serialises back (breaks principle II). Letting the mapping apply changes itself (duplicates `execute` and risks writes outside a command).

## R6. Edit cost (FR-030) and SC-005

**Decision**: an edit changes only its ranges in the document; nothing is rewritten. After the change the document text, already in memory, is re-read once by the mapping. Repainting, re-layout and re-routing are limited to what changed. The budget is checked by performance tests: open a 1,000-element, 1,500-connection sample file within 2 s, and show one edit within 100 ms.

**Rationale**: the FreeMind designer already re-reads a 1,000-node map well inside 100 ms. An in-memory scan of such a file costs a few milliseconds. Incremental parsing would have to be written for each format, and no requirement needs it. The spec's "full re-read" is read as a re-read from disk.

**Alternatives considered**: incremental re-parse by shifting ranges after each change (complex, per format, and only worth it if the performance tests show a need).

## R7. Where positions come from

**Decision**: element bounds come from the diagram by default: the mapping reads them from the file, and move and resize write them back. A designer may instead supply a `DiagramLayout`, which computes bounds for the elements it wants shown. With a layout, elements are not freely movable. They are declared `droppableOnto` other elements, and a drop becomes the mapping's `drop(keys, target, placement)`. Auto-sized elements get their size from their text slots; the layout or the file gives the position.

**Rationale**: FreeMind positions come from its tree layout, not the file. This one hook lets it keep that layout while everything else is shared.

**Alternatives considered**: storing FreeMind positions in the file (changes files users did not edit). Separate canvases for laid-out and positioned designers (defeats the framework).

## R8. Change notification (FR-004)

**Decision**: after every successful re-read, `DiagramDiff` compares the previous and new `Diagram` by key and emits a `DiagramChange` list: added, removed, moved, resized, connected, disconnected, property changed and sector changed. The designer's `DiagramListener` receives it. The list is also used to decide what to re-route and repaint.

**Rationale**: one path covers edits, undo, redo and external text changes, which are all document changes. Emitting events from gestures would miss the other three.

**Alternatives considered**: events fired by `DiagramCommands` (misses undo and text edits).

## R9. Rules and refusals (FR-003, FR-018)

**Decision**: `DiagramRules` has default-allow methods `canAdd`, `canRemove`, `canConnect`, `canDisconnect` and `canDrop`, each returning `Verdict.allow()` or `Verdict.refuse(reason)`. Definition permissions (anchor directions, resize axes, movable, droppable) are checked first, with framework reasons. During a drag, a refused target shows the platform's "not allowed" cursor and a red target outline. On release, or for a refused delete, a balloon from `JBPopupFactory` near the pointer shows the reason. The test kit reads the last refusal.

**Rationale**: the balloon and cursor are the platform's own feedback idioms. Default-allow keeps a simple designer's rules empty.

**Alternatives considered**: modal dialogs (interrupt drawing). Status-bar text only (easy to miss).

## R10. Outlines, tones and text (FR-005 to FR-008)

**Decision**:
- `Outline` has predefined shapes: rectangle, rounded rectangle, ellipse, diamond, hexagon, parallelogram, cylinder, document, note, and "none". `Outline.custom(Function<Rectangle2D, Shape>)` covers the rest. The perimeter used for floating anchors and connection ends is the outline itself.
- `Tone` is a named palette: neutral, blue, green, yellow, orange, red, purple and grey. Each tone gives a fill, border and text colour as `JBColor` light and dark pairs, chosen to keep a text contrast of at least 4.5:1 on both themes. `Tone.custom(JBColor fill, JBColor border, JBColor text)` covers the rest. Colours read from a file (FreeMind, draw.io) override the tone. They get the contrast plate FreeMind already uses when they are too close to the canvas.
- `TextSlot` has an id, a property it shows, a position (nine positions inside the bounds, plus above and below), wrapping on or off, a font style and editable on or off. Text is laid out with `LineBreakMeasurer`. Without wrapping it is clipped to the bounds with an ellipsis, never drawn outside.
- Sizing is `auto` (fit all slots within a maximum width, then wrap), `fixed(w, h)`, or `fromDiagram` (file or user size, with a minimum).

**Rationale**: covers every acceptance scenario with Java2D primitives. Tones make theme-safe colours the easy default.

**Alternatives considered**: SVG shapes via the bundled JSVG (no current need; custom outlines cover it).

## R11. Anchors and connection geometry (FR-009, FR-010, FR-012 to FR-014)

**Decision**:
- An anchor is a relative point on the bounds (`fx`, `fy` in 0..1, for example the top middle is 0.5, 0), or a floating perimeter anchor. Each anchor has an id, visibility and a set of accepted connection types, each with a direction (in, out or both).
- A connection has a line style (straight, orthogonal or curved), a dash (solid, dashed or dotted), a thickness, a tone and an arrowhead at each end. Arrowheads are none, open, filled, diamond, open diamond, circle and bar.
- A connection has three label slots: middle, source end and target end. Each shows a property and is editable or read-only.
- Curved lines are cubic Béziers through the route points. Waypoints the file stores (draw.io) are honoured when drawing and preserved, but not edited in this feature.

**Rationale**: the minimum set that covers the spec and the two real formats.

**Alternatives considered**: port objects as child elements (heavier; anchors as declarations are enough).

## R12. Routing (FR-015, SHOULD)

**Decision**: straight and curved connections are not routed. For orthogonal ones, the router first tries a direct route, then the two one-bend routes, then the Z-shaped routes. It takes the first that crosses no other element. Only when all are blocked does it run A* on a sparse grid built from the obstacle edges inside the pair's bounding box plus a margin. If that fails, the simplest route is drawn anyway. Routes are cached by connection and recomputed only when an end or an element near the route moves.

**Rationale**: most routes are clear with one bend, so 1,500 connections stay within SC-005. The fallback A* is the "few hundred lines" the survey estimated. libavoid is refused by licence (LGPL) and packaging.

**Alternatives considered**: ELK (EPL, and it lays out whole graphs, which is out of scope). A full visibility-graph router (more code than a SHOULD requirement justifies).

## R13. Toolbox (FR-016)

**Decision**: a `ToolboxToolWindowFactory` tool window, id `ADP Toolbox`, on the right by default. It follows `FileEditorManagerListener.selectionChanged`. When the selected editor's preview is a `DiagramDesigner`, it lists that definition's toolbox entries in declared order, grouped into elements and connections, with each type's outline and tone as its icon. An element entry is dragged onto the canvas through `DnDManager`. A connection entry arms the connection type used by the next drag between anchors. By default, the first connection type the source anchor accepts is used. For keyboard use, Enter on an element entry adds it at the centre of the visible canvas.

**Rationale**: a tool window is the platform's place for a palette, like the GUI Designer's. The Enter path keeps the toolbox accessible without a mouse.

**Alternatives considered**: a palette strip inside the editor (not dockable; each designer would show its own).

## R14. Property panel (FR-021 to FR-024)

**Decision**: a `PropertiesToolWindowFactory` tool window, id `ADP Properties`, on the right, shared by every designer. The panel is a two-column `JBTable` of name and value, grouped by the declaration's category. Each editor kind has its renderer and editor: text (`JBTextField`), multi-line (`ExpandableTextField`), number (`JBTextField` with integer or decimal validation), yes/no (checkbox), choice (`ComboBox`), and colour (`ColorPanel`, stored as `#RRGGBB`). Read-only rows are greyed and not editable. With several items selected, rows are the declarations they all share. A value that differs between them shows as empty with a "different values" hint. An edit applies to all items as one command. The panel listens to the designer's selection and model listeners, and clears when the selection disappears. FreeMind gains the panel too.

**Rationale**: the platform's own property table is `@ApiStatus.Internal` (survey), and `JBTable` with platform editors is public. Values are strings in the file's notation, so the mapping needs no type conversion layer.

**Alternatives considered**: Kotlin UI DSL forms (no Kotlin in the plug-in, and forms rebuild poorly on every selection). Typed property values (a conversion layer for no requirement).

## R15. In-place editing (FR-019)

**Decision**: generalise FreeMind's `InPlaceRename` into `InPlaceEditor`. Double-clicking an editable text slot or label opens a `JBTextField` over the slot, or a `JBTextArea` for a wrapping or multi-line slot, with the font scaled by the zoom. Enter commits (Ctrl+Enter in a multi-line one). Escape and loss of focus cancel. The editor cancels itself without applying anything when its item disappears on a re-read. A commit is `setProperty` through `DiagramCommands`, the same path as the panel.

**Rationale**: the FreeMind mechanism is proven, including the stale-value edge case.

**Alternatives considered**: `EditorTextField` (a full editor for a label, heavier, and its own undo inside the field is confusing).

## R16. Pan, zoom and sectors (FR-025, FR-026)

**Decision**:
- `ViewOptions` has `zoom` and `pan` flags. Zoom keeps `ViewState`'s levels and the core zoom actions, which are disabled when zoom is off. Pan is the scroll pane plus middle-button or Space-drag panning, disabled when pan is off.
- A `SectorDecl` is a lane kind: its orientation (horizontal or vertical bands) and its space. Sector instances come from the mapping with their bounds. Diagram-space sectors are painted under the zoom transform. View-space sectors are painted in viewport coordinates after the zoomed content, so they stay fixed.
- On a move or drop, the framework takes the sector containing the element's centre. For view-space sectors it is computed in viewport coordinates. When the sector changes, the move's text changes include the mapping's `setSector`, so it stays one command.

**Rationale**: meets both acceptance scenarios with no new mechanism.

**Alternatives considered**: sectors as container elements (draw.io does this; the draw.io mapping reads its swimlane cells as sectors instead, which keeps the framework simpler).

## R17. Compound gestures and undo

**Decision**: every gesture produces one `TextChanges` and one `execute(label, changes, reselect)`. Deleting an element includes its connections. The mapping merges nested or adjacent ranges so the changes do not overlap. Rules are asked for the whole set, and one refusal cancels the whole gesture. Multi-select edits and multi-element moves are one command. The reselect callback runs inside the command, so undo restores the selection.

**Rationale**: FR-020 and SC-004. `UndoBridgeTest` already proves that one `execute` is one undo step.

## R18. Unknown types (edge case)

**Decision**: when a mapping reads an item whose type it recognises as an item but not as a declared type, it returns it with the type id it found. The framework draws an element placeholder as a dashed neutral rectangle labelled with that type, and a connection placeholder as a thin dashed grey line. Placeholders can be selected and show their key as read-only in the panel. They cannot be moved, edited or connected. Deleting one is refused with the reason "unknown type is kept as it is". The file text is never touched for them.

**Rationale**: keeps content (principle II) and shows the user it exists.

## R19. FreeMind migration

**Decision**:
- `FreeMindDefinition` declares these element types: `root` (ellipse, not removable) and `node` (by FreeMind style: fork as the outline "none" with an underline, or bubble as a rounded rectangle). Their text slots are text, icons glyphs, indicators and the fold marker.
- It declares these connection types: `branch` (curved, drawn from the tree, not connectable or disconnectable by the user, refused with "remove the node to remove its branch") and `arrowLink` (curved, arrow at the end, connectable between any nodes).
- `MindMapLayout` becomes the `DiagramLayout`. Folding, tree arrow-key navigation and the existing actions stay in `freemind`.
- `FreeMindMapping` adapts `MindMapParser` and `MindMapEdits`. It gains `setAttribute`, `addArrowLink` and `removeArrowLink` edits.
- Properties: text (multi-line; read-only for rich-text nodes), ID (read-only), folded, link, colour and background colour.
- Toolbox: dropping a node onto a node adds a child. Dropping it on empty canvas is refused with "a node needs a parent".
- `MindMapCanvas`, `NodePainter`, `DragMove`, `InPlaceRename` and `EditingInstaller` are deleted.
- Core's `NodeView` and `AdpDesignerEditor.viewOf` stay as they are. `DiagramDesigner.viewOf` builds a `NodeView` from its `ElementView`, and `MindMapDesigner` fills icons, link, note and folded from its text slots. The FreeMind tests therefore do not change at all. Diagram tests read the richer `ElementView` and `ConnectionView` through `DiagramDriver`.

**Rationale**: the tests and reference replay are the safety net for a working designer, so they must not change. Keeping `NodeView` also keeps `AdpDesignerEditor` and `DesignerDriver` unchanged.

**Alternatives considered**: keeping the old canvas alongside (two canvases to maintain, and SC-001 would not reflect reality).

## R20. draw.io mapping subset

**Decision**:
- The provider claims `.drawio` files that sniff as `<mxfile` or `<mxGraphModel`. It does not claim `.drawio.svg` or `.drawio.png`.
- It reads the first `<diagram>` page. Other pages are preserved and not shown. A compressed page (text content instead of an `<mxGraphModel>` child) raises a `FormatProblem` that explains it and names the draw.io setting "Compressed" to switch off. The file then opens in the text view.
- Each `mxCell` with `vertex="1"` is an element. Its type comes from its style: `rounded=0/1`, `ellipse`, `rhombus`, `shape=hexagon`, `shape=parallelogram`, `shape=cylinder3`, `shape=document`, `shape=note`, `text`. Cells with `swimlane` in their style are sectors. Any other shape is a placeholder.
- Each `mxCell` with `edge="1"` is a connection. Its `edgeStyle`, `curved`, `dashed`, `startArrow`, `endArrow` and `strokeWidth` style keys are properties. Its value is the middle label. Child cells with `edgeLabel` style at `x=-1` and `x=1` are the end labels.
- Geometry comes from `mxGeometry`. Children of a swimlane are relative to it, so the mapping converts to and from absolute coordinates.
- Edits use `XmlEdits` for attributes and elements, and `Style` for single-key edits inside the `style` attribute, keeping the key order and every unknown key.
- New cells get ids of the form `adp-<n>`, unique in the file.
- Examples: six flowchart, UML and swimlane templates from `jgraph/drawio` `src/main/webapp/templates` (CC-BY-4.0). They are decompressed to uncompressed `.drawio` once by a recorded script. Each `.LICENSE` file names the source, the licence and the modification. One compressed original is kept to test the text-view fallback.

**Rationale**: covers the shapes and edges the spec lists. Everything else is preserved untouched.

## R21. Licence check (SC-006)

**Decision**: a root Gradle task `verifyDependencyLicences`, wired into `check`. It resolves the runtime classpath of the composed modules, excluding the IntelliJ Platform. It fails on any external artifact that `gradle/allowed-licences.properties` does not list with an Apache-2.0-compatible licence (Apache-2.0, MIT, BSD-2/3, CC0). The allowlist has no third-party entries today, so the task passes by proving there are none.

**Rationale**: about 20 lines of build script. A licence-report plug-in would be a build dependency with no current need.

## R22. Measuring SC-001

**Decision**: a `CodeSizeTest` in `drawio` and one for the sample designer count non-blank, non-comment lines of main sources. Each must be at most one tenth of the FreeMind baseline recorded before the migration (3,423 lines on 2026-09-25, so 342). The same test asserts that neither module contains drawing, selection, undo or property panel code: no `Graphics2D`, `UndoManager`, `JTable` or `MouseListener` references.

**Rationale**: makes the success criterion a failing test rather than a claim.

## R23. Designer author documentation (SC-002)

**Decision**: `docs/diagram-designer-guide.md` walks through building the two-element, one-connection designer from scratch: definition, mapping with `XmlTree`/`XmlEdits`, provider, registration and tests. It links the contract. The sample designer's first version is written from the guide alone, in a task done before the full sample exists, as the SC-002 rehearsal. The timed run by a developer new to the framework is a manual step at the end.
