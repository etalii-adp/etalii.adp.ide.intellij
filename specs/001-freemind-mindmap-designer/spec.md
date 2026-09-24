# Feature Specification: FreeMind Mind Map Designer

**Feature Branch**: `001-freemind-mindmap-designer`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "A simple Eclipse plug-in that adds multiple new visual designers for
text-based files. It should embrace all features from Eclipse, like the undo-redo stack, and the
designers should also be registered correctly for the specific file extensions. Use the FreeMind
XML file format as the first visual designer."

**Refinement**: The plug-in is named **A Different Perspective (ADP)**. FreeMind is the first of
many file formats ADP will visualise; each visual designer, such as the FreeMind one, is referred
to as a *supported file format* of ADP.

## Context

This is the first feature of the A Different Perspective (ADP) plug-in and delivers its first
supported file format, FreeMind mind maps. More supported file formats, each with its own visual
designer, will follow. The behaviours this feature defines for *how a supported file format lives
inside Eclipse* (registration, undo/redo, dirty state and save, the paired text view, preserving
what it does not understand) are the ones every later ADP supported file format will be expected
to share; only the mind-map-specific parts are unique to this feature.

A FreeMind mind map is a single XML text file (extension `.mm`) holding one tree of nodes under a
central root, plus optional per-node detail such as notes, icons, colours, fonts, hyperlinks,
attributes and arrow links drawn between arbitrary nodes.

## Clarifications

### Session 2026-09-24

- Q: Beyond node text, folding and arrow links, which node details does the first version cover?
  → A: Display icons, colours, fonts, hyperlinks and notes; edit only structure and text
  (FR-018). Everything else is preserved and reachable through the text view.
- Q: How are the plug-in and its designers named? → A: The plug-in is *A Different Perspective*
  (ADP); FreeMind is the first of many ADP supported file formats, each with its own designer.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Open and view a mind map (Priority: P1)

A developer keeps a project mind map (`roadmap.mm`) in their repository. They double-click it in
the Project Explorer and it opens in the ADP mind map designer, showing the map as FreeMind would:
the root in the centre, its branches spread to the left and right, collapsed branches collapsed.
They can expand and collapse branches, pan and zoom, and switch to a text view of the very same
file. Closing it without changes leaves the file exactly as it was.

**Why this priority**: Seeing a map in its natural form, straight from the workbench, is the
smallest thing that is useful on its own, and it proves the registration and the non-destructive
reading every other story depends on.

**Independent Test**: Open each vendored real-world example map by double-clicking it; confirm
the designer is the editor used, the tree matches the map's structure, and that saving
without edits leaves every file byte-identical.

**Acceptance Scenarios**:

1. **Given** a workspace containing a FreeMind `.mm` file, **When** the user double-clicks it,
   **Then** it opens in the mind map designer, and the designer is listed under *Open With* and
   selectable as the default editor for `.mm` files like any built-in editor.
2. **Given** an open map, **When** the user looks at it, **Then** every node's text is shown in
   a tree around the central root, each first-level branch on the side the file records, and
   branches the file records as folded are shown collapsed.
3. **Given** an open map, **When** the user expands or collapses a branch, pans or zooms,
   **Then** the view updates accordingly.
4. **Given** an open map, **When** the user switches to the text view, **Then** the file's text
   is shown, and switching back returns to the visual view.
5. **Given** a map opened and closed (or saved) without any edit, **When** the file is compared
   with its original, **Then** it is byte-identical.

---

### User Story 2 - Edit a mind map visually, with Eclipse undo/redo and save (Priority: P2)

The developer adds ideas to the map: a child under a node, a sibling next to it, renames a node
in place, deletes a node, moves a node up, down or under another node, and folds a branch away.
Each change can be undone and redone with Edit > Undo/Redo and the usual keyboard shortcuts, the
editor shows the unsaved-changes marker, and Save writes the map so it still opens in FreeMind
with everything else in the file intact.

**Why this priority**: Editing is what makes a designer more than a viewer; undo/redo and the
save lifecycle are what make it trustworthy inside Eclipse.

**Independent Test**: Perform each editing action on an example map, undo and redo each one,
save, reopen the file in the designer and in FreeMind 1.0.1, and diff it against the original:
only the edited parts differ, and undoing every action restores the original bytes.

**Acceptance Scenarios**:

1. **Given** a selected node, **When** the user adds a child or a sibling, **Then** a new node
   appears in the right place ready for its text to be typed.
2. **Given** a selected node, **When** the user renames, deletes, moves or re-parents it, or
   folds/unfolds it, **Then** the map shows the change and the editor is marked dirty.
3. **Given** any sequence of such changes, **When** the user invokes Undo repeatedly, **Then**
   each change is reverted in reverse order, and Redo re-applies them; the Edit menu labels
   describe the change being undone or redone.
4. **Given** all changes have been undone, **When** the user checks the editor, **Then** it is
   no longer marked dirty.
5. **Given** a dirty editor, **When** the user saves, closes, or reverts, **Then** the platform's
   standard save, unsaved-changes prompt and revert behaviour apply.
6. **Given** a saved map, **When** it is compared with the version before editing, **Then** only
   the edited nodes differ, and all content the designer does not display is unchanged.
7. **Given** the workbench New wizard, **When** the user creates a new mind map file, **Then**
   a valid map with a single root node is created and opened in the designer.

---

### User Story 3 - Move freely between the visual and the text view (Priority: P3)

Some changes are quicker in the text (bulk-editing a note, fixing an attribute the designer does
not expose). The developer edits the text view, switches back, and the visual view reflects the
change; one undo history covers both views. If a teammate's change arrives on disk through
version control while the file is open, Eclipse's usual external-change handling applies.

**Why this priority**: The paired text view guarantees no content is ever out of reach, but the
visual designer is useful without cross-view synchronisation for most users.

**Independent Test**: Edit the same map alternately in both views, undo across the switches, and
modify the file on disk while it is open (clean and dirty), checking each outcome.

**Acceptance Scenarios**:

1. **Given** an open map, **When** the user edits its text and switches to the visual view,
   **Then** the visual view shows the edited map.
2. **Given** edits made in both views, **When** the user invokes Undo repeatedly, **Then** they
   are undone in the order made, regardless of which view they were made in.
3. **Given** the text is edited into something that is not a valid mind map, **When** the user
   switches to the visual view, **Then** the designer explains the problem and where it is,
   keeps the text intact, and offers to return to the text view; no content is discarded.
4. **Given** an open, unmodified map, **When** the file changes on disk, **Then** the editor
   reloads it; **Given** an open, modified map, **When** the file changes on disk, **Then** the
   user is asked whether to replace their changes, as in the platform's text editor.

---

### User Story 4 - Navigate a large map through the Outline view (Priority: P4)

For a map with hundreds of nodes, the developer uses the standard Outline view to see the node
tree and jump to a node, and selecting a node in the designer highlights it in the Outline.

**Why this priority**: Valuable for large maps, but not needed to view or edit a typical map.

**Independent Test**: Open a large example map, navigate via the Outline, and confirm the
selection stays linked in both directions.

**Acceptance Scenarios**:

1. **Given** an open map, **When** the Outline view is shown, **Then** it lists the node tree.
2. **Given** a node selected in the Outline, **When** the selection changes, **Then** the
   designer brings that node into view and selects it, expanding collapsed ancestors in the
   view without marking the editor dirty; and selecting in the designer selects in the Outline.

---

### Edge Cases

- A `.mm` file that is not a FreeMind map (the extension is also used for Objective-C++ source)
  MUST NOT be opened in the designer by default; it opens in whatever editor would otherwise
  apply.
- A file that is malformed or only partly understood opens in the text view with an explanation
  instead of being refused or rewritten.
- A map written by a FreeMind version newer or older than those supported, or by a compatible
  tool (e.g. Freeplane), opens with everything it contains preserved on save, even where the
  designer cannot show it.
- Node text stored as rich (HTML) content is shown as readable text; it is not flattened or
  rewritten unless the user edits that node's text.
- Characters stored as numeric character references (as FreeMind writes non-ASCII text) stay
  in that form for unedited content.
- The root node cannot be deleted or moved; the user is told why.
- Deleting a node that is the source or target of an arrow link also removes that link, and
  undoing the deletion restores both.
- A read-only file opens for viewing; editing actions are unavailable and the user is told why.
- The same file opened in two editors shows changes made in either one in both.
- Very large maps (thousands of nodes) and deep nesting remain usable (see SC-003).

## Requirements *(mandatory)*

### Functional Requirements

**Workbench integration (shared by all future ADP supported file formats)**

- **FR-001**: The designer MUST be registered with the workbench for the `.mm` file extension as
  that extension's default editor, and MUST appear under *Open With* for it.
- **FR-002**: The designer MUST be selected by default only for `.mm` files whose content is a
  FreeMind mind map; other `.mm` files MUST open with the editor that would otherwise apply.
- **FR-003**: Users MUST be able to open any `.mm` file in the platform's plain text editor
  instead, via *Open With*, and to make either editor their default through the standard
  preference.
- **FR-004**: Every change made through the designer MUST be recorded as an undoable operation in
  the workbench's standard undo/redo history, reachable through Edit > Undo/Redo, their standard
  key bindings, and labelled with a description of the change.
- **FR-005**: The editor MUST report its dirty state, and MUST support Save, Save As, Revert,
  close-with-unsaved-changes prompts and external-change detection with the same behaviour as
  the platform's text editor.
- **FR-006**: The designer MUST offer a text view of the same file, sharing one document and one
  undo history with the visual view, so edits made in either view are visible in the other.
- **FR-007**: A file the designer cannot interpret MUST open in the text view with an explanation
  identifying the problem and its location; the file MUST NOT be modified as a result.
- **FR-008**: Read-only files MUST open for viewing with editing actions disabled and an
  explanation of why.

**Preserving the file**

- **FR-009**: Opening and saving a map without edits MUST produce a byte-identical file.
- **FR-010**: An edit MUST change only the parts of the file that represent the edited content;
  all other content, including elements and attributes the designer does not display, ordering,
  formatting and character encoding style, MUST be preserved.
- **FR-011**: Nodes created by the designer MUST receive the identifier and creation/modification
  timestamps FreeMind itself gives new nodes, and edited nodes MUST have their modification
  timestamp updated, so the file remains a normal FreeMind map.
- **FR-012**: Saved maps MUST open in FreeMind 1.0.1 without error and show the same map.

**Viewing**

- **FR-013**: The designer MUST display the map as a tree around a central root, placing each
  first-level branch on the side (left/right) recorded in the file, or on an automatically chosen
  side when none is recorded.
- **FR-014**: The designer MUST display each node's text, showing rich (HTML) node content as
  readable text.
- **FR-015**: The designer MUST display arrow links between nodes.
- **FR-016**: The designer MUST show branches recorded as folded as collapsed, and users MUST be
  able to expand and collapse any branch.
- **FR-017**: Users MUST be able to pan and zoom the visual view, and select one or more nodes.
- **FR-018**: The designer MUST display each node's icons, text and background colours, font
  (face, size, bold, italic), hyperlink (as an indicator the user can follow) and note (as an
  indicator, with the note's text readable on hover). These details are displayed only; they are
  not edited through the visual view in this version, and remain editable through the text view.
  Attributes, clouds and edge styles are preserved but not displayed.

**Editing**

- **FR-019**: Users MUST be able to add a child node and add a sibling node to the selected node,
  and type its text immediately.
- **FR-020**: Users MUST be able to edit a node's text in place. For a node with rich (HTML)
  content, the user MUST be warned, before the edit is applied, that its formatting will be
  replaced by plain text, and MUST be able to cancel.
- **FR-021**: Users MUST be able to delete the selected node(s) with their descendants, except the
  root. Arrow links attached to deleted nodes MUST be removed with them.
- **FR-022**: Users MUST be able to reorder a node among its siblings and move it (with its
  descendants) under another node, by keyboard and by dragging; moving a node into its own
  descendants MUST be refused.
- **FR-023**: Users MUST be able to fold and unfold branches as a persisted, undoable edit, as
  FreeMind does.
- **FR-024**: Every editing action MUST be available from the keyboard and from a context menu.
- **FR-025**: Users MUST be able to create a new mind map file through the workbench's New wizard;
  the new file contains a single root node and opens in the designer.

**Standard views**

- **FR-026**: The designer MUST provide the node tree to the standard Outline view, with selection
  linked in both directions.
- **FR-027**: Selecting nodes MUST publish the selection to the workbench, so standard selection-
  driven views and commands work with it.

### Key Entities

- **Mind map**: one `.mm` file; a single tree of nodes under one root, in a FreeMind format
  version.
- **Node**: a point in the tree with text (plain or rich), an identifier, creation and
  modification times, a side (left/right, first-level nodes only), a folded state, and optional
  detail: note, icons, colours, font, hyperlink, attributes, cloud and edge style.
- **Arrow link**: a directed visual connection from one node to another, independent of the tree.
- **Unrecognised content**: any element or attribute the designer does not interpret; carried
  through every edit unchanged.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the vendored real-world example maps open in the designer and, saved
  without edits, are byte-identical to the original.
- **SC-002**: For every editing action type, performing it and then undoing it returns the file to
  its original bytes, and every one of those actions can be undone and redone through the
  standard Edit menu and key bindings.
- **SC-003**: A map of 1,000 nodes opens and is displayed within 2 seconds, and adding, renaming,
  folding or deleting a node in it shows the result within 0.1 seconds, on a typical developer
  workstation.
- **SC-004**: After any sequence of visual edits, 100% of the content the designer does not
  display (notes, icons, attributes, unrecognised elements) is present and unchanged in the saved
  file.
- **SC-005**: 100% of maps saved by the designer open in FreeMind 1.0.1 showing the same tree and
  text as the designer showed before saving.
- **SC-006**: A developer familiar with Eclipse can open an existing map, add five nodes, undo two
  of them and save, on their first attempt and without documentation.
- **SC-007**: 100% of the visual view's behaviours — each node detail it displays (FR-018), each
  editing action (FR-019 to FR-023), and selection, folding, pan and zoom — can be verified by an
  automated test that runs unattended, needs no person watching the screen, and checks the result
  against both the rendered map and the saved file. A developer familiar with the project can add
  such a test for a new diagram behaviour in under 30 minutes, reusing the existing example maps.

## Assumptions

- Target users are developers and architects who keep mind maps alongside code in an Eclipse
  workspace; they know Eclipse editors and may or may not know FreeMind.
- Supported input is the FreeMind map format as written by FreeMind 0.7.1 through 1.0.1. Files
  from compatible tools (Freeplane) and other versions open with their content preserved, but
  only FreeMind 1.0.1 features are displayed.
- Layout is automatic (a classic left/right mind-map tree). Positional offsets recorded in the
  file are honoured when displaying but are not edited by dragging in this version; dragging a
  node changes its place in the tree, not its free position.
- Rich-text editing, visual editing of node details (icons, colours, fonts, notes, hyperlinks,
  attributes), map-level styles, export/print, search across maps, and collaborative editing are
  out of scope for this feature.
- Performance targets assume a typical developer workstation and maps of up to a few thousand
  nodes.
- Real published example maps with a permissive (non-share-alike) licence can be obtained and
  vendored for testing, as the constitution requires.
- This feature establishes the behaviour shared by all ADP supported file formats; additional
  supported file formats are separate, later features.
