# Data Model: JetBrains IDE Support

The document text stays the only persistent model (constitution principle II). This feature
changes the host, not the model. The parse result, node keys, validation rules and the edit
catalogue carry over from spec 001's behaviour unchanged; only their host-specific types are
replaced. This file is now the authoritative data model for the FreeMind designer. Spec 001's
host-specific `data-model.md` is removed with the rest of its implementation artifacts (FR-017a).

## What changes from the previous host

| Previous type | Replacement | Why |
|---|---|---|
| Host graphics `RGB` on `MapNode.color` and `backgroundColor` | `etalii.adp.core.Rgb` (record `r, g, b`) | The format layer must not depend on a UI toolkit. The designer converts to `java.awt.Color` when it paints. |
| Host text-edit tree (`TextEdit`, `MultiTextEdit`, `InsertEdit`, `DeleteEdit`, `ReplaceEdit`) | `etalii.adp.core.TextChange` and `TextChanges` | Same semantics (non-overlapping ranges against one text), no host dependency. Applied by core through the platform `Document` (R4). |
| `GraphicalViewer`, edit parts, figures | `MindMapCanvas` (a Swing `JComponent`) and `NodeView` layout boxes | GEF is not available (R6). |
| Zoom and scroll owned by GEF | `ViewState.zoom`, and the editor's `JBScrollPane` | Same user-facing behaviour. |

The packages `etalii.adp.freemind.model`, `.parse` and `.edit` import nothing from the IntelliJ
Platform, AWT or Swing. A unit test that scans their imports enforces it.

## Parse result (`etalii.adp.freemind.model`)

Unchanged from spec 001's design except for the colour type.

### `MindMap`

| Field | Type | Notes |
|---|---|---|
| `version` | `String` | `map/@version` as written (`1.0.1`, `0.7.1`, `freeplane 1.11.5`, ...). Any value is accepted. |
| `root` | `MapNode` | The single top-level `node`. Zero or several top-level nodes is a `FormatProblem`. |
| `nodesByKey` | `Map<NodeKey, MapNode>` | Lookup for the canvas, actions and the Structure view. |
| `arrowLinks` | `List<ArrowLink>` | Every `arrowlink` in the file. |
| `indentUnit` | `String` | Detected leading whitespace per depth. Empty when the file has none. |
| `lineSeparator` | `String` | Always `"\n"` in this host: the platform `Document` holds text with `\n` separators and restores the file's own separator on save (R5). Kept so edits never hard-code a separator. |

### `MapNode`

| Field | Type | Source in the file | Notes |
|---|---|---|---|
| `key` | `NodeKey` | `@ID`, else index path | Stable across re-parses. |
| `id` | `String?` | `@ID` | |
| `text` | `String` | `@TEXT`, else `richcontent[@TYPE='NODE']` via `RichText` | Display text. |
| `rich` | `boolean` | a `richcontent[@TYPE='NODE']` child exists | Triggers the rich-text rename warning. |
| `created`, `modified` | `long?` | `@CREATED`, `@MODIFIED` | Epoch milliseconds. |
| `side` | `Side?` | `@POSITION` | `LEFT` or `RIGHT`, first level only; chosen automatically when absent. |
| `folded` | `boolean` | `@FOLDED="true"` | |
| `icons` | `List<String>` | `icon/@BUILTIN` | In file order. |
| `color`, `backgroundColor` | `Rgb?` | `@COLOR`, `@BACKGROUND_COLOR` | `#rrggbb`. |
| `font` | `FontSpec?` | `font/@NAME @SIZE @BOLD @ITALIC` | |
| `link` | `String?` | `@LINK` | |
| `note` | `String?` | `richcontent[@TYPE='NOTE']` via `RichText` | Tooltip text. |
| `hgap`, `vgap`, `vshift` | `int` | `@HGAP`, `@VGAP`, `@VSHIFT` | Honoured when laying out, never edited. |
| `children` | `List<MapNode>` | child `node` elements | Document order. |
| `parent` | `MapNode?` | | `null` for the root. |
| `ranges` | `NodeRanges` | | See below. |

Attributes, clouds, edge styles, hooks and unknown elements or attributes are not fields; they
exist only as text inside a node's range and survive every edit by construction.

### `NodeRanges`, `ArrowLink`, `NodeKey`

Unchanged: `element` (with its line's leading whitespace), `startTag`, `attr(name)` value ranges,
`childInsertPoint` (a self-closing `/>` is rewritten to `>` + child + `</node>`), `richNode`.
Ranges are offsets into the `Document` text they were parsed from and are valid only against it.
`ArrowLink` holds `source`, `destinationId`, `range`, `startArrow`, `endArrow`. `NodeKey` is the
`ID` or the index path, compared by value.

### `FormatProblem` (core)

`message` and `offset`. The editor converts the offset to line and column through
`Document.getLineNumber` and `getLineStartOffset`. Raised for XML that is not well formed, a
missing `<map>` root, a `map` without exactly one top-level `node`, and any DOCTYPE declaration.

## Text changes (`etalii.adp.core`)

### `TextChange`

`record TextChange(int offset, int length, String replacement)`: replace `length` characters at
`offset` with `replacement`. An insert has `length == 0`; a delete has an empty `replacement`.

### `TextChanges`

An ordered, validated set of `TextChange`s against one text:

- Ranges MUST NOT overlap; two inserts at the same offset keep their construction order.
- `applyTo(String)` returns the new text (used by format-layer unit tests).
- `applyTo(Document)` replaces from the highest offset to the lowest, so earlier offsets stay
  valid, inside the caller's write command (R4).
- Construction with overlapping ranges throws `IllegalArgumentException`; the edit is then not
  applied and the document is untouched.

## Validation rules (edits)

| Rule | Source | Enforcement |
|---|---|---|
| The root cannot be deleted or moved | spec 001 FR-021, FR-022 | The action is disabled for the root; its description (shown in the status bar and tooltip) says why. |
| A node cannot move into its own subtree | spec 001 FR-022 | The drop target is rejected while dragging; keyboard move is disabled. |
| Nothing can be edited when the document is read-only | FR-010 | Every edit action's `update` disables it, and the read-only banner is shown. |
| A rich-text rename needs confirmation | spec 001 FR-020 | A `Messages.showOkCancelDialog` before the change is built; Cancel builds nothing. |
| New IDs are unique | spec 001 FR-011 | `FreeMindConventions.newId` draws until the ID is absent from `nodesByKey`. |
| Moving to or from the first level fixes `POSITION` | spec 001 FR-013 | Added with the drop side when moving to the first level, removed when moving away. |

## Edit catalogue (`MindMapEdits`)

Each entry returns an `Edit(label, TextChanges changes, NodeKey created)`. The label becomes the
command name, which the IDE shows as "Undo <label>" and "Redo <label>".

| Action | Label | Text effect |
|---|---|---|
| Add child | Add Child Node | Insert a new `<node .../>` at the parent's `childInsertPoint`, expanding `/>` when needed. The parent gets a new `MODIFIED`. |
| Add sibling | Add Sibling Node | Insert after the selected node's `element` range. |
| Rename | Rename Node | Replace the `TEXT` value, or replace `richNode` with a `TEXT` attribute. Set `MODIFIED`. |
| Delete | Delete Node / Delete Nodes | Remove each selected `element` range and each `arrowlink` targeting a removed ID. |
| Move up or down | Move Node | Remove the element and insert it before or after the neighbouring sibling. |
| Re-parent (drag, indent, outdent) | Move Node | Remove the element, insert at the target's `childInsertPoint` or a sibling position, fix `POSITION`. |
| Fold or unfold | Fold Branch / Unfold Branch | Insert `FOLDED="true"`, or remove the attribute with its leading space. |
| New map | (not an edit) | `FreeMindConventions.newMapText(id, now)`: the text of a valid map with one root node. |

`FreeMindConventions.newId(taken, random)` and the clock used for `CREATED`/`MODIFIED` are
injectable, so the reference results below are reproducible.

## Reference results (FR-008, SC-003, SC-007)

Recorded once from the previous host's implementation, before it is removed, and compared byte
for byte by the new tests.

| Item | Location | Content |
|---|---|---|
| Scenario list | `freemind/testdata/reference/scenarios.json` | For each vendored example map: the actions to run (action, target node key, arguments), with a fixed random seed and a fixed clock. |
| Result files | `freemind/testdata/reference/<map>/<nn>-<action>.mm` | The exact bytes after the action is applied and saved. |
| Undo check | (no file) | Undoing the action returns the original map's bytes; asserted, not stored. |
| Test inventory | `freemind/testdata/reference/spec001-test-inventory.md` | Every spec 001 test method and the behaviour it verified, each mapped to the new test that covers it (FR-018, SC-007). |

Recording runs through the format layer (parser plus `MindMapEdits` plus the text-edit
application) of the previous implementation with the injected seed and clock. That is the layer
that produces the bytes, so the new host's output is compared against exactly what spec 001's
designer wrote.

## View state (`ViewState`, per editor, never persisted)

- `expanded: Set<NodeKey>`: transient overrides of `FOLDED` for display (for example when the
  Structure view reveals a node inside a folded branch, or when the file is read-only).
- `selection: List<NodeKey>`: restored after each re-parse by key.
- `zoom: double`: 0.25 to 4.0, in steps; the scroll position is the scroll pane's.

## State transitions: the visual editor

```text
            parse ok                          parse fails
 [open] ───────────────▶ VISUAL ◀──────┐   ┌──────────────▶ PROBLEM
                           │            │   │                 (notification panel + "Show text";
            document       │  parse ok  │   │ parse fails      the text tab is selected on open)
            changed        ▼            │   │
                        (re-parse) ─────┴───┘
 Read-only document: VISUAL and PROBLEM behave the same, with editing disabled and a banner.
```

Saving, the modified marker, reloading after an external change and Local History belong to the
platform's `Document` and `FileDocumentManager`. The visual editor only reacts to the document
events they cause.
