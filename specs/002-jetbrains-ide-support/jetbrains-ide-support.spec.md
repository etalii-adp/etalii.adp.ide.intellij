# Feature Specification: JetBrains IDE Support

**Feature Branch**: `002-jetbrains-ide-support`
**Created**: 2026-09-24
**Status**: Draft
**Input**: "Enhance the codebase so that it also runs in IntelliJ, JetBrains Rider and other related IDE's."
**Scope refinement**: This is not an additional plug-in. The IntelliJ Platform plug-in replaces the
Eclipse plug-in completely; when this feature is done, no trace of Eclipse remains in the product
or the codebase.

## Context

A Different Perspective (ADP) today ships as an Eclipse plug-in with a FreeMind mind map designer
(spec 001). Many of the developers who keep mind maps next to their code work in JetBrains IDEs:
IntelliJ IDEA for Java and Kotlin, Rider for .NET, WebStorm, PyCharm and the rest of the family.
This feature moves ADP to those IDEs. The JetBrains plug-in offers the same designer, keeps the
same promises about the file, and feels native in its host.

The Eclipse plug-in is retired, not maintained alongside: its code, build, packaging, tests,
documentation and naming are removed or replaced. The behaviour defined by spec 001 is carried
over; only the host changes.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Open and view a mind map in a JetBrains IDE (Priority: P1)

A developer using IntelliJ IDEA or Rider installs the ADP plug-in and double-clicks a FreeMind
`.mm` file in the Project view. It opens in the visual designer and shows the map around its root:
branches on their recorded side, folding, arrow links, icons, colours, fonts, links and notes. The
file can also be shown as plain text in the IDE's own text editor. Other `.mm` files, such as
Objective-C++ sources, open as they would without the plug-in.

**Why this priority**: seeing a map is the smallest useful thing, and it proves the plug-in
installs, registers and draws in the new host.

**Independent Test**: install the plug-in into a clean IntelliJ IDEA and a clean Rider, open each
vendored example map, and check that what is shown matches the display rules of spec 001.

**Acceptance Scenarios**:

1. **Given** the plug-in is installed in IntelliJ IDEA, **When** the user opens a FreeMind `.mm`
   file, **Then** it opens in the visual designer and shows the tree as spec 001 defines it.
2. **Given** the plug-in is installed in Rider, **When** the user opens the same file, **Then** the
   result is the same as in IntelliJ IDEA.
3. **Given** a `.mm` file that is not a FreeMind map, **When** the user opens it, **Then** it opens
   in whatever editor the IDE would use without the plug-in.
4. **Given** a FreeMind file the designer cannot interpret, **When** the user opens it, **Then** it
   opens as text with a clear explanation, and nothing in the file changes.
5. **Given** the IDE uses a dark theme, **When** a map is shown, **Then** all of it is legible.

---

### User Story 2 - Edit a map with the IDE's own undo, redo and save (Priority: P2)

The developer adds, renames, deletes, moves, drags and folds nodes from the keyboard or the context
menu. Each change is one named step in the IDE's own Undo and Redo, shared with the text view. The
file is saved the way the IDE saves every other file, and the IDE's Local History and version
control see exactly the lines that changed.

**Why this priority**: editing is what makes a designer worth installing; it depends on Story 1.

**Independent Test**: in IntelliJ IDEA and in Rider, perform every editing action on an example
map, undo each through the Edit menu and its shortcut, save, and diff the file against the
reference result recorded for the same actions under spec 001.

**Acceptance Scenarios**:

1. **Given** an open map, **When** the user adds a child node, **Then** it appears in the map and
   Edit > Undo names and reverts that one change.
2. **Given** a map opened and closed without edits, **When** it is saved, **Then** the file is
   byte-identical.
3. **Given** the same map and the same sequence of edits, **When** they are made in a JetBrains
   IDE, **Then** the saved file is byte-identical to the reference result recorded under spec 001.
4. **Given** an edit made on the visual view, **When** the user switches to the text view and
   presses Undo, **Then** the edit is undone there too.
5. **Given** the file changes on disk while it is open, **When** the IDE notices, **Then** the
   designer reloads or asks, exactly as the IDE's text editor does.
6. **Given** a read-only file, **When** it is opened, **Then** the map is shown and editing
   actions are unavailable, with the reason stated.
7. **Given** the user rebinds a designer action in the IDE's keymap settings, **When** they press
   the new shortcut, **Then** the action runs.

---

### User Story 3 - Create and navigate maps the IDE way (Priority: P3)

The developer creates a new mind map from the IDE's New file menu and moves around a large map
through the IDE's Structure view, with selection kept in step both ways.

**Why this priority**: these complete the everyday workflow but a map can be viewed and edited
without them.

**Independent Test**: create a map through New, check it opens in FreeMind 1.0.1, then open a
large example map and navigate it from the Structure view.

**Acceptance Scenarios**:

1. **Given** a project directory, **When** the user chooses New > FreeMind Mind Map, **Then** a
   valid map with one root node is created and opened in the designer.
2. **Given** an open map, **When** the user selects a node in the Structure view, **Then** the
   same node is selected and brought into view in the designer, and the reverse also holds.

---

### User Story 4 - Maintainers work in a codebase with no Eclipse left (Priority: P3)

A maintainer clones the repository, runs one command-line build, and gets the installable
JetBrains plug-in with every test run. Nothing in the repository refers to, depends on or builds
for Eclipse.

**Why this priority**: carrying a dead host along would double the cost of every later change and
every later format. It matters from the first release but does not deliver user value alone.

**Independent Test**: from a clean checkout, run the build and search the repository, build
output and plug-in package for any Eclipse reference.

**Acceptance Scenarios**:

1. **Given** a clean checkout, **When** the maintainer runs the command-line build, **Then** it
   produces the JetBrains plug-in and runs all its tests, and needs no Eclipse installation,
   target platform or tooling.
2. **Given** the finished feature, **When** the repository, the build output and the installed
   plug-in are searched for Eclipse, **Then** no source, build file, manifest, package, test,
   document, identifier or name refers to it, other than version-control history and this
   spec's own account of the migration.

### Edge Cases

- A user upgrading from the Eclipse plug-in: their existing `.mm` files open in the JetBrains
  plug-in unchanged; no migration of files is needed.
- The IDE indexes or opens a very large map on startup: the IDE stays responsive (see SC-004).
- The plug-in is installed into an IDE release older than the supported one: the IDE refuses the
  install with its standard message rather than failing at runtime.
- The plug-in is disabled or uninstalled while a map is open: the file is left unchanged and
  reopens as text.
- Rider opens a solution whose files are not in a classic project tree: `.mm` files reached through
  any of Rider's file views open in the designer.
- The IDE runs with a non-default font scale or high-DPI screen: text and icons stay sharp and
  legible.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The plug-in MUST install and run in every IDE built on the JetBrains IntelliJ
  Platform at the supported release, including IntelliJ IDEA (Community and Ultimate), Rider,
  WebStorm, PyCharm, CLion, GoLand, PhpStorm and RubyMine.
- **FR-002**: The designer MUST be the default editor for `.mm` files whose content is a FreeMind
  map, and MUST NOT claim any other `.mm` file.
- **FR-003**: Users MUST be able to show the same file in the IDE's plain text editor, sharing one
  document and one undo history with the visual view.
- **FR-004**: Every change made through the designer MUST be one named step in the IDE's own
  Undo/Redo, reachable from the Edit menu and its shortcuts.
- **FR-005**: Modified state, saving, closing, reloading after an external change and Local
  History MUST behave as they do for the IDE's text editor.
- **FR-006**: Opening and saving a map without edits MUST produce a byte-identical file.
- **FR-007**: An edit MUST change only the parts of the file that represent the edited content.
- **FR-008**: The same sequence of edits on the same file MUST produce the byte-identical text
  that spec 001 defines; reference results MUST be recorded before the Eclipse plug-in is removed.
- **FR-009**: A file the designer cannot interpret MUST open as text with an explanation, and MUST
  NOT be changed.
- **FR-010**: Read-only files MUST open for viewing with editing actions disabled and the reason
  stated.
- **FR-011**: The designer MUST display everything spec 001 requires: the tree around its root,
  sides, folding, arrow links, icons, colours, fonts, links and notes, with pan, zoom and
  multi-selection.
- **FR-012**: The designer MUST offer every editing action spec 001 requires (add child,
  add sibling, rename in place, delete, reorder, move, drag, fold), each from the keyboard and from
  a context menu.
- **FR-013**: Designer shortcuts MUST appear in the IDE's keymap settings so users can rebind them.
- **FR-014**: Users MUST be able to create a new mind map from the IDE's New file menu.
- **FR-015**: The designer MUST provide the node tree to the IDE's Structure view, with selection
  kept in step both ways.
- **FR-016**: The designer MUST stay legible under the IDE's light and dark themes and at any font
  scale the IDE supports.
- **FR-017**: The Eclipse plug-in MUST be removed completely: no Eclipse source, build
  configuration, manifest, feature, update site, target platform, dependency, test, document,
  identifier or product naming may remain. Only version-control history and this spec's own
  account of the migration are exempt.
- **FR-017a**: Spec 001's implementation artifacts (plan, research, data model, contracts, tasks
  and spec-tooling state), which describe the Eclipse implementation, MUST be removed or
  rewritten for the JetBrains plug-in when the Eclipse code is removed, not before.
- **FR-018**: Every behaviour spec 001 verifies through its tests MUST be verified by equivalent
  tests of the JetBrains plug-in before the Eclipse tests are removed.
- **FR-019**: Adding a new supported format MUST NOT require changing the shared designer
  infrastructure or another format.
- **FR-020**: One headless command-line build MUST produce the installable plug-in and run all
  its tests, including tests that run it inside a real IDE, without any Eclipse tooling.
- **FR-021**: The plug-in MUST NOT access the network at runtime.
- **FR-022**: The plug-in MUST be relicensed from EPL-2.0 to Apache-2.0.

### Key Entities

- **Plug-in**: the single installable form of ADP, for IDEs on the IntelliJ Platform.
- **Host IDE**: any product on the IntelliJ Platform at the supported release.
- **Supported format**: a text file format with a designer, such as FreeMind. Its reading,
  editing and visual rules are those its spec defines, independent of the host.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The plug-in installs and opens every vendored example map in IntelliJ IDEA Community,
  IntelliJ IDEA Ultimate, Rider and at least two other IntelliJ Platform IDEs: 100% pass.
- **SC-002**: 100% of the vendored example maps, opened and saved without edits, are
  byte-identical.
- **SC-003**: For every editing action type, the same action on the same map yields a file
  byte-identical to the spec 001 reference result, and undoing it returns the file to its prior
  text.
- **SC-004**: A map of 1,000 nodes opens and is displayed within 2 seconds, and adding, renaming,
  folding or deleting a node shows the result within 0.1 seconds, on a typical developer
  workstation.
- **SC-005**: A search of the repository (excluding this spec), the build output and the
  installed plug-in finds zero references to Eclipse.
- **SC-006**: A developer familiar with IntelliJ IDEA or Rider can open an existing map, add five
  nodes, undo two of them and save, on their first attempt and without documentation.
- **SC-007**: 100% of the behaviours covered by spec 001's tests are covered by the plug-in's
  tests, and all pass.

## Assumptions

- "Related IDEs" means the JetBrains products built on the IntelliJ Platform. Android Studio is
  expected to work but is not tested. JetBrains Fleet, JetBrains Gateway thin clients and Visual
  Studio (including ReSharper) are out of scope.
- The supported JetBrains release is the current IntelliJ Platform release at planning time.
  Older releases are supported only if a later spec says so.
- The plug-in is distributed as a file users install from disk. Publishing to the JetBrains
  Marketplace is a later, separate step.
- Feature scope is parity with spec 001, no more: the out-of-scope list of spec 001 applies to
  the JetBrains plug-in too.
- The same example maps and licences vendored for spec 001 serve as test data for the plug-in.
- No Eclipse release follows this feature; existing Eclipse users move to a JetBrains IDE.
- Spec 001's specification and checklists are already host-neutral and stay in `specs/` as the
  behavioural baseline; they are not exempt from the no-trace rule.
- The plugin will be licensed as Apache-2.0. 

## Dependencies

- The constitution names the deliverable "an installable Eclipse plug-in" and makes Native
  Eclipse Citizenship non-negotiable. It must be amended through `/speckit-constitution` before
  planning, so that native citizenship applies to the IntelliJ Platform and Eclipse is removed
  from its principles and platform constraints.
- The project's own naming (repository, `CLAUDE.md`, product and package identifiers) contains
  "Eclipse". It is renamed to `EtAlii.Adp.IntelliJ` as part of this feature's implementation,
  together with the code migration, not before planning.
