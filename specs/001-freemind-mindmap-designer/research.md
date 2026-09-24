# Research: FreeMind Mind Map Designer

Phase 0 decisions for [plan.md](plan.md). The spec has no open `NEEDS CLARIFICATION` markers.
Each entry below settles an unknown the codebase could not, because there is no code yet.

## R1. Editor shape: a multi-page editor around the platform text editor

**Decision**: `AdpDesignerEditor` extends `MultiPageEditorPart`. Page 0 is the visual view, a
GEF viewer control. Page 1 is a stock `TextEditor` added with `addPage(IEditorPart, input)` on a
`TextFileDocumentProvider`. The editor delegates `doSave`, `doSaveAs`, `isDirty`,
`isSaveAsAllowed` and revert to the text editor.

**Rationale**: FR-005 and FR-006 require the platform's exact save, dirty, revert and
external-change behaviour, plus one document for both views. Nesting the platform's own text
editor inherits all of that instead of re-implementing it (principle I). File buffers are shared
per file, so the "same file open in two editors" edge case works with no extra code.

**Alternatives considered**: a single `EditorPart` with its own document provider handling
(re-implements save and external changes, which principle I forbids). A GEF `GraphicalEditor`
with a separate text editor on the same file (two undo histories, which FR-006 forbids).

## R2. Undo: labelled operations whose document changes are absorbed into one history entry

**Decision**: each visual edit is a `DocumentEditOperation` (an `AbstractOperation` whose label
describes the change, for example "Add Child Node"). Its undo context is the document's
`IDocumentUndoManager` context from `DocumentUndoManagerRegistry`. The editor runs it with
`history.openOperation(new TriggeredOperations(op, history), EXECUTE)`, then `execute`, then
`closeOperation`. Inside, the operation applies its `TextEdit` between `beginCompoundChange` and
`endCompoundChange` on the document undo manager, so the text change that manager records
becomes a child of the triggered operation. That gives one history entry carrying the edit's
label. This is the same pattern the LTK refactoring framework uses to put a labelled multi-edit
change on a text editor's undo stack.

On the visual page, `AdpActionBarContributor` installs `UndoActionHandler` and
`RedoActionHandler` for that same context, so Edit > Undo and Ctrl+Z behave the same on both
pages, and edits from both views interleave in one history (US3-AS2). The document undo manager
restores modification stamps on undo, so the file buffer clears its dirty flag when every change
is undone (US2-AS4).

**Rationale**: FR-004 asks for labelled entries in the standard history. FR-006 asks for one
history shared with the text view. Only the document undo manager's own records satisfy the
second, and only a wrapping operation satisfies the first.

**Alternatives considered**: a GEF `CommandStack` (a second history, which principle I forbids).
Plain document changes with no wrapper (these land as "Typing" entries and split one edit into
several steps). An operation doing its own undo through `UndoEdit` while the document undo
manager also records (two entries per edit).

**Risk**: how `TriggeredOperations` and `DocumentUndoManager` interact is the least documented
mechanism in this plan. The first test in `etalii.adp.core.tests` proves label, single-step
undo, redo and the return to clean. If it fails, the fallback is to keep the triggered-operation
wrapper and make the trigger's own undo and redo no-ops that return OK, leaving the document
changes to the absorbed children.

## R3. Registration: a content type decides which `.mm` files are FreeMind maps

**Decision**: the plug-in declares content type `etalii.adp.freemind.mindmap`. Its base type is
`org.eclipse.core.runtime.xml`, its file extension is `mm`, and its describer is
`XMLRootElementContentDescriber2` with root element `map`. The editor is registered in
`org.eclipse.ui.editors` with `<contentTypeBinding>` to that type and `default="true"`, and with
no `extensions="mm"`.

**Rationale**: FR-002 requires the designer to be the default only for `.mm` files that are mind
maps. Objective-C++ `.mm` files do not describe as `<map>` XML, so they fall through to whatever
editor would otherwise apply. The *Open With* menu lists the designer for matching files (FR-001)
and always lists the Text Editor (FR-003). Editor-per-content-type preferences work unchanged.

**Alternatives considered**: `extensions="mm"` (this claims every `.mm` file, which FR-002
forbids).

**Amended during implementation**: the platform's `XMLRootElementContentDescriber2` answers
INDETERMINATE, not INVALID, for text that is not XML, so an Objective-C++ `.mm` still matched our
content type (the only one for `*.mm`). A small `MindMapContentDescriber` now answers INVALID unless
the first element is `<map>`, and reports the XML declaration's encoding.

## R4. Reading: SAX validates, an in-house scanner records exact offsets

**Decision**: parsing is two passes over the document text.

1. **Validation**: the JDK SAX parser with secure processing on, DOCTYPE disallowed and external
   entities off. It gives an authoritative well-formedness verdict and a line and column for
   FR-007. The DOCTYPE and entity settings matter because this is a trust boundary: the file
   can come from anywhere.
2. **Positions**: `XmlScanner`, about 300 lines, lexes tags, attributes, text, comments, CDATA
   and processing instructions. It records the exact character range of every element's start
   tag, its end, and each attribute name and value. `MindMapParser` builds the immutable
   `MindMap` from that output. It reads only the constructs FreeMind 1.0.1 displays and keeps
   ranges for everything it may edit.

**Rationale**: FR-010 needs edits that replace one attribute value or insert one element. That
needs exact source offsets for each attribute, which neither DOM, SAX nor StAX in the JDK
provides (StAX's `getCharacterOffset` is approximate and has no attribute offsets). A focused
scanner is smaller than any library that does provide them.

**Alternatives considered**: WTP's structured document model (a large non-platform dependency).
DOM plus a serializer (rewrites the whole file, which breaks FR-009 and FR-010). The scanner
alone, without SAX (it would have to re-implement XML's full well-formedness rules to give
trustworthy FR-007 messages).

## R5. Writing: text edits that follow FreeMind's own conventions

**Decision**: `MindMapEdits` produces `org.eclipse.text.edits` edits only. New and changed
content follows what FreeMind 1.0.1 writes, which the vendored 1.0.1 examples confirm:

- A new node is `<node CREATED="ms" ID="ID_n" MODIFIED="ms" TEXT="..."/>`, attributes in
  alphabetical order, with `POSITION="left|right"` for first-level nodes.
- `ID_` is followed by a random positive `int`, checked for uniqueness in the map. Timestamps
  are epoch milliseconds from one clock read per operation.
- Text escaping writes `&amp; &lt; &gt; &quot;`, and writes every character outside printable
  ASCII as a numeric character reference in FreeMind's form. Existing references in unedited
  content are never re-encoded.
- An insertion copies the line separator and leading whitespace of its nearest sibling, or of
  its parent plus the file's detected indent unit, so the file's formatting style survives.
- A move takes the subtree's text verbatim and inserts it at the target.
- An edit to a node updates only its `MODIFIED` value (FR-011).
- Renaming a rich-content node replaces its `richcontent TYPE="NODE"` element with a `TEXT`
  attribute, after the FR-020 confirmation.
- Deleting a node also deletes every `arrowlink` elsewhere whose `DESTINATION` is inside the
  deleted subtree, in the same operation (FR-021).

**Rationale**: FR-011 and FR-012 need the result to be a normal FreeMind map. FR-010 needs
everything else untouched.

**Alternatives considered**: re-serializing the edited element (loses its attribute order,
quoting style and references).

**Simplified**: a moved subtree keeps its original indentation and is not re-indented to its new
depth. FreeMind itself writes no indentation, so this binds only for hand-indented files. If it
does, re-indent the moved lines by the depth difference.

## R6. Visual stack: GEF Classic, with node keys as edit-part models

**Decision**: the visual page is a GEF Classic `ScrollingGraphicalViewer` with a
`ScalableFreeformRootEditPart`, which provides zoom and pan through `ZoomManager` and the standard
zoom actions. It also uses GEF's `GraphicalViewerKeyHandler` for keyboard navigation, a
`ContextMenuProvider`, and drag trackers for reorder and re-parent. The viewer is the editor
site's selection provider on the visual page (FR-027).

Edit parts take a `NodeKey` as their GEF model. The key is the node's `ID`, or its index path
when there is no `ID`. `refreshVisuals` reads the current `MapNode` for that key from the latest
parse. GEF's child diffing therefore keeps existing parts and figures across re-parses and only
touches what changed, which keeps SC-003's 0.1 s budget and preserves selection.

GEF drag gestures produce a `TextEditCommand`. `OperationHistoryCommandStack` executes it by
calling the editor's `execute(label, edit)`, so GEF never keeps its own undo stack.

**Rationale**: FR-017, FR-022, FR-024 and FR-027 need selection, marquee selection, drag
feedback, keyboard navigation, zoom and a context menu now. GEF Classic ships all of these in
the release train under EPL-2.0 (principle V: use what the platform ecosystem offers).

**Alternatives considered**: a raw SWT `Canvas` (re-implements selection, drag and drop, zoom
and accessibility). Zest (graph layouts, no editing). Sirius or GMF (an EMF model competes with
the text as the source of truth). GEF 5 (JavaFX, not in the release train).

## R7. Folding versus transient expansion

**Decision**: collapsing or expanding in the visual view is the persisted `FOLDED` edit (FR-023).
FreeMind also persists folding, and FR-016 is met by the same gesture. `ViewState` keeps a
transient set of expanded node keys that overrides `FOLDED` for display only. It is used when the
Outline reveals a node inside a folded branch (US4-AS2, "without marking the editor dirty") and
when a read-only file is being viewed (FR-008).

**Rationale**: one gesture with one meaning in the common case, and no dirty marker where the
spec forbids one.

**Alternatives considered**: all expansion transient (contradicts FR-023). Revealing through a
fold edit (marks the editor dirty, which US4 forbids).

## R8. Displaying node details without FreeMind's artwork

**Decision**: FreeMind's built-in icons are GPL artwork, so they are not vendored. `FreeMindIcons`
maps each built-in icon name (for example `idea`, `button_ok`, `full-1`, `flag-green`) to a
Unicode emoji drawn with the system font. An unknown or custom icon is drawn as a small text
badge showing its name. Links and notes use glyph indicators. A link opens through
`IWorkbenchBrowserSupport` for URLs, or through `IDE.openEditor` for paths relative to the map.
The note's text is the indicator's tooltip. Rich HTML is turned into plain text by `RichText`:
tags are dropped, block tags and `<br>` become line breaks, and entities are decoded.

**Rationale**: FR-018 asks for the details to be displayed. The constitution refuses
incompatible licences and needs no new dependency.

**Alternatives considered**: bundling FreeMind's PNGs (GPL). Drawing our own icon set (about 60
images of artwork work, deferred until a specification asks for fidelity). Rendering HTML with
the SWT `Browser` (heavy per node, and a network risk).

## R9. Real example maps

**Decision**: a task sources at least five published `.mm` files under CC0, CC-BY, MIT or
Apache-2.0, never share-alike. Between them they cover: FreeMind 0.7.1-era and 1.0.1-era files,
rich-content nodes and notes, arrow links, icons, non-ASCII text as numeric references, a
Freeplane file, and a large map. Each goes into `tests/etalii.adp.freemind.tests/examples/` with
its licence beside it as `<name>.LICENSE`, recording the source URL. The 1,000-node performance
map is generated by the test, because size is what it measures, not realism.

`.gitattributes` gains `*.mm -text`. Without it, `text=auto` would change the examples' line
endings on Windows checkouts, and byte-identity tests would compare files that are no longer the
published bytes.

**Rationale**: principle IV and SC-001.

**Alternatives considered**: FreeMind's bundled documentation maps (GPL, refused). Only
hand-written samples (principle IV forbids relying on them).

## R10. Verifying FreeMind 1.0.1 compatibility

**Decision**: FR-012 and SC-005 are checked in three ways:

1. Automated structural tests: every construct the designer writes matches the element and
   attribute forms found in the vendored 1.0.1 examples.
2. An opt-in test, skipped unless `FREEMIND_HOME` points at a FreeMind 1.0.1 install. It loads
   each saved map with FreeMind's own map reader in a separate JVM and compares tree and text.
3. A manual check in FreeMind before merge, listed in the tasks.

**Rationale**: FreeMind is GPL and cannot be a build dependency, so CI cannot run it.

**Alternatives considered**: vendoring FreeMind's XSD (GPL). Skipping the check (the spec
requires it).

## R11. Build and test harness

**Decision**: Maven 3.9 with Tycho 5 in pomless mode (`.mvn/extensions.xml`). One root
`pom.xml` references the target definition. Test fragments run with `tycho-surefire-plugin`
(`useUIHarness=true`, `useUIThread=true`, JUnit 5). The reusable `DesignerDriver` lives in the
plain bundle `etalii.adp.testing` (see [contracts/test-kit.md](contracts/test-kit.md)).
Performance assertions allow CI headroom but run in every build.

**Rationale**: the constitution requires a headless command-line build that matches the IDE.
Pomless keeps per-bundle build files at zero.

**Alternatives considered**: PDE build (legacy). A `pom.xml` in every bundle (duplication with
nothing gained). SWTBot (the driver reaches the editor, viewer and command service directly, so
a UI-robot layer adds nothing at this scale).

## R12. Bundle and identifier naming

**Decision**: bundles, packages and extension IDs use the prefix `etalii.adp`, for example
`etalii.adp.core` and `etalii.adp.freemind.editor`. User-facing names say "A Different
Perspective (ADP)" and call FreeMind a supported file format (for example the editor name
"FreeMind Mind Map (ADP)", and the New wizard category "A Different Perspective (ADP)").

**Rationale**: the spec's naming refinement and the repository name.

**Alternatives considered**: `eu.vrenken.*` (ties the plug-in to a person, not the project).
