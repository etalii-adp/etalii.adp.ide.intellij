# Feature Specification: Diagram Designer Framework

**Feature Branch**: `003-diagram-designer-framework`
**Created**: 2026-09-24
**Status**: Draft
**Input**: A strong shared core for building future ADP designers: diagram drawing with definable elements and connections, a toolbox, a central property grid panel and central persistence, so that a designer module defines what its diagram contains and how it looks instead of coding it. Also research whether Apache-2.0 or MIT libraries compatible with the IntelliJ editor could take over these responsibilities.

## Context

ADP has one designer today, for FreeMind mind maps (specs 001 and 002). Its canvas, drawing, editing and property handling were written for mind maps alone. Every next designer (class diagrams, state machines, flow charts, architecture sketches) needs the same things: shapes, lines between them, a palette to drag from, a place to edit properties, and reading and writing the file. Constitution principle III asks that the second designer cost a fraction of the first. This feature makes that true for diagram-style designers.

Two roles appear below. A **designer author** is the developer who builds a designer module for one file format. A **user** is the developer who edits a file with that designer in their IDE.

## Clarifications

### Session 2026-09-25

- Q: Which designer proves the framework (FR-031)? → A: All three: an internal sample designer, the FreeMind designer migrated onto the framework, and a new designer for a second, real diagram format.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Define a designer and see a file drawn from its definition (Priority: P1)

A designer author creates a new designer module for a file format. They declare which element types and connection types the diagram has and how each looks: its outline, colour tone, where its texts sit and how they wrap, where its anchors are, and how its connections are drawn. They provide the mapping between the file and the diagram. They write no drawing code. A user opens a file of that format and sees it drawn exactly as declared.

**Why this priority**: drawing a file from a declaration is the foundation every other story builds on, and it proves the central promise: a designer is defined, not coded.

**Independent Test**: build a sample designer that consists only of a definition and a file mapping, open a sample file with it, and compare the drawing against the declared looks.

**Acceptance Scenarios**:

1. **Given** a definition declaring a rounded-rectangle element type with a centred, wrapping title, **When** a user opens a file containing two such elements, **Then** both are drawn as rounded rectangles with their titles wrapped inside, each sized to fit its text.
2. **Given** a definition declaring a dashed, curved connection type with an open arrow at its end and a label in the middle, **When** a file connects the two elements with it, **Then** the line is drawn dashed and curved between the declared anchors, with the arrow and the label.
3. **Given** an element type whose anchors are declared invisible, **When** it is drawn, **Then** no anchor markers show, and connections still attach at the declared locations.
4. **Given** a file the designer's mapping cannot fully interpret, **When** a user opens it, **Then** it opens in the text view with a clear explanation, as for every ADP designer.

---

### User Story 2 - Build a diagram from the toolbox and edit it on the canvas (Priority: P1)

A user drags an element type from the toolbox onto the canvas and it appears where dropped. They drag from one element's anchor to another's to connect them, using a connection type from the toolbox. They select, move, resize and delete elements and connections. Each change can be undone and redone, marks the file modified, and changes only the part of the file it concerns. The designer's own rules decide what is allowed: a connection that the definition or a rule forbids cannot be made, and the user sees why.

**Why this priority**: a designer that only views is of limited use. Editing through the toolbox and canvas is what makes the framework worth adopting.

**Independent Test**: with the sample designer, create, connect, move, resize and delete elements, undo and redo each step, save, and diff the file against the expected minimal change.

**Acceptance Scenarios**:

1. **Given** the toolbox lists only the element and connection types the designer declares, **When** a user drags an element type onto the canvas, **Then** a new element of that type appears at the drop point and the file gains exactly that element.
2. **Given** a connection type allowed only from output anchors to input anchors, **When** a user drags from an input anchor, **Then** no connection is created and the canvas shows that the target is not allowed.
3. **Given** a designer rule that refuses to delete the last element of a kind, **When** a user tries to delete it, **Then** it stays and the user is told the rule's reason.
4. **Given** an element type declared resizable horizontally only, **When** a user selects it, **Then** only horizontal resize handles are offered.
5. **Given** a sequence of add, connect, move and delete, **When** the user undoes all of it, **Then** the file is byte-identical to how it was opened.

---

### User Story 3 - Edit values in a central property panel and in place (Priority: P2)

A user selects an element or connection and its properties appear in a dedicated property panel in the IDE. The designer declares which properties show, which are read-only, and which kind of editor each uses (text, number, yes/no, choice list, colour). Editing a value in the panel updates the canvas at once, and editing a text directly on the canvas updates the panel. Both paths are one undoable change each. The same panel serves every ADP designer.

**Why this priority**: properties beyond the visible text are how real diagrams carry meaning. It builds on stories 1 and 2 but is not needed for a first useful diagram.

**Independent Test**: select elements and connections of the sample designer, edit each kind of property in the panel and each editable text on the canvas, and check both views agree, undo works, and read-only values cannot be changed.

**Acceptance Scenarios**:

1. **Given** a selected element with an editable title and a read-only identifier, **When** the property panel shows it, **Then** the title can be edited and the identifier cannot.
2. **Given** a connection whose middle label is declared editable and whose end labels are declared read-only, **When** a user double-clicks each label on the canvas, **Then** only the middle label enters in-place editing.
3. **Given** a user changes a choice-list property in the panel, **When** the change is confirmed, **Then** the canvas reflects it and one undo restores the previous value.
4. **Given** several elements of the same type are selected, **When** the panel shows them, **Then** values they share are shown and editing one applies it to all, as one undoable change.
5. **Given** the panel is open and the user switches to another ADP designer's file, **When** an element is selected there, **Then** the same panel shows that designer's properties.

---

### User Story 4 - Navigate large diagrams and organise them in swimlanes (Priority: P3)

A designer may enable panning and zooming, and may declare swimlanes or other sectors that divide the drawing. Sectors are declared either in diagram space, moving and scaling with the content, or in view space, fixed to the visible window. A user places elements into lanes, and the designer is told which lane an element is in so it can record that in the file.

**Why this priority**: needed for larger process and architecture diagrams, but a first designer can be useful without it.

**Independent Test**: enable pan, zoom and diagram-space lanes in the sample designer, move elements between lanes, and check the file records the lane and the view scales correctly.

**Acceptance Scenarios**:

1. **Given** a designer with zoom enabled, **When** a user zooms in, **Then** elements, connections, texts and diagram-space lanes scale together, and view-space sectors stay fixed.
2. **Given** a designer with zoom disabled, **When** a user uses the zoom actions, **Then** nothing changes.
3. **Given** two diagram-space lanes, **When** a user drags an element from one into the other, **Then** the element's lane membership changes in the file as one undoable change.

---

### Edge Cases

- A file refers to an element or connection type the definition does not declare: the item is preserved in the file, and shown as a neutral placeholder rather than dropped.
- A connection's element is deleted: its connections are deleted with it in the same undoable change, unless a designer rule refuses, in which case nothing is deleted.
- An element is removed while its properties are shown or its text is being edited in place: the panel clears and in-place editing ends without applying a stale value.
- The file is changed outside the designer while it is open: the diagram reloads as the text editor would, keeping selection where the items still exist.
- A text longer than its element with auto-size off: it wraps or is cut off as declared, never drawn outside the element.
- A connection whose route cannot avoid other elements: it is still drawn, along the simplest path, rather than hidden.
- Two users of the same designer on different themes (light, dark): declared colour tones read correctly on both.
- A definition that is itself inconsistent (a connection rule naming an undeclared anchor): the designer author learns this when the designer loads, with the offending declaration named.

## Requirements *(mandatory)*

### Functional Requirements

**Designer definition**

- **FR-001**: A designer author MUST be able to create a working diagram designer by declaring its element types, connection types, toolbox and view options, plus a mapping between the file and the diagram, without writing drawing, hit-testing, selection or undo code.
- **FR-002**: The framework MUST check a designer's definition when the designer loads and report every inconsistency with the declaration it concerns.
- **FR-003**: A designer MUST be able to add rules that decide whether an element or connection may be added, removed, connected or disconnected, and give a reason when it refuses.
- **FR-004**: The framework MUST notify the designer of every diagram change (added, removed, moved, resized, connected, disconnected, property changed), so the designer keeps all diagram-specific logic in its own module.

**Elements**

- **FR-005**: An element type MUST be definable by its outline, chosen from a library of predefined shapes or given as a custom outline.
- **FR-006**: An element type MUST be definable by its colour tone, readable in both light and dark IDE themes.
- **FR-007**: An element type MUST be able to show several texts, each anchored at a declared position within the element, each with optional wrapping.
- **FR-008**: An element type MUST be able to size itself to fit its texts, or keep a fixed or user-set size.
- **FR-009**: An element type MUST declare its anchors: their positions on or around its outline and whether they are drawn or only act as attachment points.
- **FR-010**: An element type MUST declare which connection types may attach to which of its anchors, and in which direction.
- **FR-011**: An element type MUST declare whether its elements can be selected, moved, dropped onto other elements, and resized horizontally, vertically, both or not at all.

**Connections**

- **FR-012**: A connection type MUST be definable by its line: straight, orthogonal or curved, solid or dashed, and its thickness and colour tone.
- **FR-013**: A connection type MUST be definable by an arrowhead style at each end, chosen from a predefined set that includes none.
- **FR-014**: A connection type MUST be able to show a text in the middle and a text at each end, each declared editable or read-only.
- **FR-015**: A connection type SHOULD be able to route around elements it would otherwise cross.

**Editing**

- **FR-016**: The framework MUST provide a toolbox that lists exactly the element and connection types the active designer declares, from which users drag to create elements and connections.
- **FR-017**: Users MUST be able to select, multi-select, move, resize and delete elements and connections, and create and remove connections between anchors, within what the definition and rules allow.
- **FR-018**: When an action is refused by the definition or a rule, the user MUST see that it is refused, and why when a reason is given.
- **FR-019**: Every value declared editable MUST be editable both in the property panel and, where it is visible on the canvas, in place.
- **FR-020**: Every user change MUST be one undoable step in the IDE's undo history, mark the file modified, and follow the IDE's save, reload and read-only behaviour, as constitution principle I requires.

**Property panel**

- **FR-021**: The framework MUST offer one dedicated property panel in the IDE, shared by all ADP designers, showing the properties of the current selection.
- **FR-022**: For each element and connection type, the designer MUST declare which properties the panel shows, whether each is read-only, and which editor kind it uses.
- **FR-023**: The panel MUST provide at least text, multi-line text, number, yes/no, choice list and colour editors.
- **FR-024**: With several items selected, the panel MUST show their shared properties and apply an edit to all of them as one undoable change.

**View**

- **FR-025**: A designer MUST be able to enable or disable panning and zooming.
- **FR-026**: A designer MUST be able to declare swimlanes or other sectors, each in diagram space or in view space, and learn which sector an element is placed in.

**Persistence**

- **FR-027**: The framework MUST read a file into the diagram and write diagram changes back to the file, using the designer's mapping, as a shared service.
- **FR-028**: Opening and saving a file without edits MUST leave it byte-identical, and each edit MUST change only the part of the file it concerns (constitution principle II).
- **FR-029**: Content of the file that the designer's mapping does not understand MUST be kept.
- **FR-030**: Reading and writing MUST stay fast on large files: an edit MUST NOT cost a full re-read or full rewrite of the file.

**Adoption**

- **FR-031**: Three designers MUST prove the framework: an internal sample designer that exercises every capability, the FreeMind designer migrated onto the framework, and a new designer for a second, real diagram format with published example files.
- **FR-032**: The choice of third-party libraries for drawing, routing or the property panel MUST be made from the library survey (`library-survey.md`) and limited to Apache-2.0-compatible licences.

### Key Entities

- **Designer definition**: everything a designer module declares: its element types, connection types, toolbox, view options, rules and file mapping. One per designer.
- **Element type**: a kind of shape a diagram contains, with its outline, colour tone, text slots, anchors, sizing and interaction permissions, and the properties it exposes.
- **Anchor**: a named attachment point on an element type, with position, visibility and the connection types and directions it accepts.
- **Connection type**: a kind of line between anchors, with its line style, arrowheads, label slots, routing and exposed properties.
- **Property declaration**: one exposed value of an element or connection type: its name, editor kind and whether it is read-only.
- **Sector**: a swimlane or other region of the drawing, in diagram or view space.
- **Diagram**: the elements and connections read from one file, kept in step with that file's text, which stays the source of truth.
- **Rule**: designer logic consulted before an add, remove, connect or disconnect, which allows it or refuses it with a reason.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A second diagram designer, built on the framework, needs no drawing, selection, undo or property panel code of its own, and at most a tenth of the code of the FreeMind designer.
- **SC-002**: A designer author new to the framework gets a two-element, one-connection designer opening a sample file within one working day, using only the framework's documentation.
- **SC-003**: 100% of the sample files open and save without edits byte-identical, and every scripted edit produces only the expected change in the file.
- **SC-004**: Every edit in the acceptance scenarios is undone and redone correctly by the IDE's undo and redo, in 100% of automated test runs.
- **SC-005**: A 1,000-element, 1,500-connection diagram opens within 2 seconds, and a single edit on it shows on screen within 100 milliseconds.
- **SC-006**: Every third-party library the framework ships with has an Apache-2.0-compatible licence, verified by the build.

## Assumptions

- The framework serves diagram-style designers: shapes joined by lines on a canvas. Designers that are not diagrams (tables, forms) are out of scope.
- The file mapping (how elements and connections are read from and written to a specific file format) is necessarily the designer's own code. "No code" in the input means no code for drawing, interaction, undo, the property panel and persistence plumbing.
- Designer rules are code in the designer module, called by the framework at defined points.
- Definitions are made in the designer module itself; a separate visual editor for definitions is out of scope.
- Automatic layout of a whole diagram is out of scope; routing of individual connections is in.
- Printing and image export are out of scope.
- All constitution promises (native editor, shared text view, one undo history, byte-identical round trips, headless tests) apply to every designer built on the framework unchanged.
- The library research the input asks for is recorded in `library-survey.md` beside this spec and is an input to planning, not a requirement in itself.
