# Data Model: FreeMind Mind Map Designer

The document text is the only persistent model (principle II). Everything below is either a
**parse result**, which is immutable, re-derived after each document change and then discarded,
or **view state**, which is transient, per editor and never written. The parse result keeps
source ranges so that edits can be exact.

## Parse result (`etalii.adp.freemind.model`)

### `MindMap`

| Field | Type | Notes |
|---|---|---|
| `version` | `String` | The `map/@version` value as written, for example `1.0.1` or `0.7.1`. Any value is accepted (edge case: other versions and Freeplane). |
| `root` | `MapNode` | The single top-level `node`. A `map` with zero or several top-level nodes is a `FormatProblem`. |
| `nodesByKey` | `Map<NodeKey, MapNode>` | Lookup for edit parts and the Outline (R6). |
| `arrowLinks` | `List<ArrowLink>` | Every `arrowlink` in the file. |
| `indentUnit` | `String` | Detected leading whitespace per depth. Empty when the file has none, as FreeMind writes it. |
| `lineSeparator` | `String` | The file's dominant separator. |

### `MapNode`

| Field | Type | Source in the file | Notes |
|---|---|---|---|
| `key` | `NodeKey` | `@ID`, else index path | Stable across re-parses. |
| `id` | `String?` | `@ID` | |
| `text` | `String` | `@TEXT`, else `richcontent[@TYPE='NODE']` converted by `RichText` | Display text (FR-014). |
| `rich` | `boolean` | a `richcontent[@TYPE='NODE']` child exists | Triggers the FR-020 warning. |
| `created`, `modified` | `long?` | `@CREATED`, `@MODIFIED` | Epoch milliseconds. |
| `side` | `Side?` | `@POSITION` | `LEFT` or `RIGHT`, first level only. When absent, a side is chosen automatically (FR-013). |
| `folded` | `boolean` | `@FOLDED="true"` | |
| `icons` | `List<String>` | `icon/@BUILTIN` | In file order. |
| `color`, `backgroundColor` | `RGB?` | `@COLOR`, `@BACKGROUND_COLOR` | `#rrggbb`. |
| `font` | `FontSpec?` | `font/@NAME @SIZE @BOLD @ITALIC` | |
| `link` | `String?` | `@LINK` | |
| `note` | `String?` | `richcontent[@TYPE='NOTE']` converted by `RichText` | Tooltip text. |
| `hgap`, `vgap`, `vshift` | `int` | `@HGAP`, `@VGAP`, `@VSHIFT` | Layout offsets are honoured, not edited. |
| `children` | `List<MapNode>` | child `node` elements | In document order. |
| `parent` | `MapNode?` | | `null` for the root. |
| `ranges` | `NodeRanges` | | See below. |

Attributes, clouds, edge styles, hooks and any unknown element or attribute are not fields. They
exist only as text inside the node's range and survive every edit by construction (FR-010,
SC-004).

### `NodeRanges`

Character ranges into the document at parse time, as offset and length:

- `element`: from `<node` through the matching `</node>` or `/>`, plus the leading whitespace on
  its line (so delete and move take the whole line).
- `startTag`: the `<node ...>` tag, used to insert attributes.
- `attr(name)`: the value range of each attribute, excluding quotes. Used to replace `TEXT`,
  `MODIFIED` and `FOLDED`.
- `childInsertPoint`: where a new last child goes. For a self-closing node this is the `/>`,
  which is rewritten to `>` + child + `</node>`.
- `richNode`: the `richcontent TYPE="NODE"` element range, when present.

Ranges are valid only against the text they were parsed from. Edits are always built from the
latest parse and applied immediately, on the UI thread, before any other change can happen.

### `ArrowLink`

| Field | Type | Notes |
|---|---|---|
| `source` | `NodeKey` | The node containing the element. |
| `destinationId` | `String` | `@DESTINATION`. A link whose destination is missing is kept in the file and not drawn. |
| `range` | `Range` | The whole element, for removal on delete (FR-021). |
| `startArrow`, `endArrow` | `String` | `@STARTARROW` and `@ENDARROW`, for decorations. |

### `NodeKey`

A value type holding either the `ID` string or the index path from the root (for example
`0/3/1`). Equality is by value. A node without an `ID` changes key when an earlier sibling is
inserted. That only resets its selection, which is acceptable because FreeMind 0.8 and later
always write `ID`.

### `FormatProblem` (core)

`message` and `offset`. The editor turns the offset into a line and column through the document.
Raised for XML that is not well formed (from SAX), a missing `<map>` root, a `map` without
exactly one top-level `node`, and a DOCTYPE declaration (refused at the trust boundary).

## Validation rules (edits)

| Rule | Source | Enforcement |
|---|---|---|
| The root cannot be deleted or moved | FR-021, FR-022, edge cases | Handlers are disabled for the root, and a status line message says why. |
| A node cannot move into its own subtree | FR-022 | The drag policy returns no command. The keyboard move is disabled. |
| Nothing can be edited when the input is read-only | FR-008 | Every edit handler's `isEnabled` is false, and the banner is shown. |
| A rich-text rename needs confirmation | FR-020 | A dialog appears before the `TextEdit` is built. Cancel builds nothing. |
| New IDs are unique | FR-011 | Draw `ID_<random int>` until the ID is absent from `nodesByKey`. |
| Moving to or from the first level fixes `POSITION` | FR-013 | Moving to the first level adds `POSITION` with the side of the drop. Moving away from the first level removes it. |

## Edit catalogue (`MindMapEdits`)

Each entry returns one `TextEdit` (a `MultiTextEdit` when it touches several ranges) plus the
undo label shown in the Edit menu.

| Action | Label | Text effect |
|---|---|---|
| Add child | Add Child Node | Insert a new `<node .../>` at the parent's `childInsertPoint`, expanding `/>` when needed. The parent gets a new `MODIFIED`. |
| Add sibling | Add Sibling Node | Insert after the selected node's `element` range. |
| Rename | Rename Node | Replace the `TEXT` value, or replace `richNode` with a `TEXT` attribute. Set `MODIFIED`. |
| Delete | Delete Node / Delete Nodes | Remove each selected `element` range, and each `arrowlink` targeting a removed ID. |
| Move up or down | Move Node | Remove the element, and insert it before or after the neighbouring sibling. |
| Re-parent (drag, indent, outdent) | Move Node | Remove the element, insert it at the target's `childInsertPoint` or at a sibling position, and fix `POSITION`. |
| Fold or unfold | Fold Branch / Unfold Branch | Insert `FOLDED="true"`, or remove the attribute with its leading space. |

## View state (`ViewState`, per editor, never persisted)

- `expanded: Set<NodeKey>`: transient overrides of `FOLDED` for display (R7).
- `selection: List<NodeKey>`: restored after each re-parse by key.
- `zoom` and scroll position: owned by GEF's `ZoomManager` and viewport.

## State transitions: the editor's visual page

```text
            parse ok                          parse fails
 [open] ───────────────▶ VISUAL ◀──────┐   ┌──────────────▶ PROBLEM
                           │            │   │                 (panel + "Show text";
            document       │  parse ok  │   │ parse fails      text page activated on open)
            changed        ▼            │   │
                        (re-parse) ─────┴───┘
 Read-only input: VISUAL and PROBLEM behave the same, with editing disabled and the banner shown.
```

Save, dirty state, revert and external changes belong to the nested text editor and file buffer.
The visual page only reacts to the document changes they cause.
