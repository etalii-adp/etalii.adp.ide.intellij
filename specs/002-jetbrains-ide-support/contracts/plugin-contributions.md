# Contract: Plug-in Contributions (`META-INF/plugin.xml`)

Everything the plug-in registers with the IntelliJ Platform. Tests assert these IDs, names and
shortcuts; changing any of them is a contract change.

## Plug-in descriptor

| Element | Value |
|---|---|
| `id` | `etalii.adp` |
| `name` | A Different Perspective (ADP) |
| `vendor` | EtAlii |
| `idea-version` | `since-build="262"`, no `until-build` |
| `depends` | `com.intellij.modules.platform` only |
| `description` | Visual designers for text-based files; first format: FreeMind mind maps. Apache-2.0. |

## Editor registration (FR-002, FR-003)

| Extension | Value |
|---|---|
| `fileEditorProvider` | `etalii.adp.freemind.ui.MindMapEditorProvider` (extends core `AdpEditorProvider`) |
| editor type id | `etalii.adp.freemind.editor` |
| `accept` | extension `mm` **and** `FreeMindSniffer` recognises the first 4 KB (research R3) |
| policy | `HIDE_DEFAULT_EDITOR` |
| editor | `TextEditorWithPreview` named "FreeMind Mind Map", default layout: preview only; the preview is `MindMapDesigner` |
| `fileTypeDetector` | `etalii.adp.freemind.FreeMindFileTypeDetector` → `FreeMindFileType` ("FreeMind Mind Map", default extension `mm`, not bound to the extension) |
| `stripTrailingSpacesFilterFactory` | `etalii.adp.core.AdpStripTrailingSpacesFilterFactory`: `NOT_ALLOWED` for files an ADP provider accepts (research R5) |

`FreeMindSniffer` accepts: optional UTF-8 BOM, optional XML declaration, then whitespace and
comments, then a root start tag `<map` with a `version` attribute. Anything else, including an
unreadable or empty file, is rejected.

## New file (FR-014)

| Attribute | Value |
|---|---|
| action id | `etalii.adp.freemind.NewMindMap` |
| text | FreeMind Mind Map |
| group | `NewGroup`, anchor after `NewFile` |
| dialog | name prompt; `.mm` is appended when missing; an existing name is refused with the IDE's standard message |

The file content is FreeMind 1.0.1's new-map form with a fresh `ID` and timestamps and root
text "New Mindmap":

```xml
<map version="1.0.1">
<!-- To view this file, download free mind mapping software FreeMind from http://freemind.sourceforge.net -->
<node CREATED="…" ID="ID_…" MODIFIED="…" TEXT="New Mindmap"/>
</map>
```

Line separator: the project's default line separator setting. The file opens in the designer.

## Actions, keymap and context menu (FR-012, FR-013)

Every action is registered in `plugin.xml` with a default shortcut in the `$default` keymap, so
it appears under Settings > Keymap > Plug-ins > A Different Perspective (ADP) and can be
rebound. The canvas also registers each action's current shortcut set on itself
(`registerCustomShortcutSet`), so the shortcut works while the canvas has focus and never
elsewhere; outside the designer each action's `update` disables it, so text editors keep their
own meaning for Tab, Enter, Space and Delete. `Ctrl` is `Cmd` on macOS.

| Action id | Text | Default shortcut | Enabled when |
|---|---|---|---|
| `etalii.adp.freemind.AddChild` | Add Child Node | `Insert`, `Tab` | one node selected, editable |
| `etalii.adp.freemind.AddSibling` | Add Sibling Node | `Enter` | one non-root node selected, editable |
| `etalii.adp.freemind.Rename` | Rename Node | `F2` | one node selected, editable |
| `etalii.adp.freemind.Delete` | Delete Node | `Delete` | non-root nodes selected, editable |
| `etalii.adp.freemind.MoveUp` | Move Node Up | `Ctrl+Up` | one non-root node with a previous sibling, editable |
| `etalii.adp.freemind.MoveDown` | Move Node Down | `Ctrl+Down` | one non-root node with a next sibling, editable |
| `etalii.adp.freemind.Indent` | Move Under Previous Sibling | `Ctrl+Right` | one non-root node with a previous sibling, editable |
| `etalii.adp.freemind.Outdent` | Move Up a Level | `Ctrl+Left` | one node at depth two or deeper, editable |
| `etalii.adp.freemind.ToggleFold` | Fold / Unfold Branch | `Space` | nodes with children selected (view-only when read-only) |
| `etalii.adp.core.SelectAll` | Select All Nodes | `Ctrl+A` | designer focused |
| `etalii.adp.core.ZoomIn` | Zoom In | `Ctrl+=` | designer focused |
| `etalii.adp.core.ZoomOut` | Zoom Out | `Ctrl+-` | designer focused |
| `etalii.adp.core.ZoomReset` | Actual Size | `Ctrl+0` | designer focused |

When an action is disabled for the root or for a read-only file, its description (shown in the
status bar and tooltip) states the reason.

Undo and redo are the platform's own `$Undo` and `$Redo`; they act on the document because the
designer is a `DocumentReferenceProvider` (research R7). Their menu text is "Undo <label>" with
the labels from data-model.md.

The canvas's context menu is the group `etalii.adp.freemind.DesignerPopup`: every
`etalii.adp.freemind.*` action above in table order, with separators after `AddSibling`,
`Delete` and `Outdent`, then the zoom actions.

## Structure view and selection (FR-015)

- `MindMapDesigner.getStructureViewBuilder()` returns a `TreeBasedStructureViewBuilder`. Its
  root is the map root; elements wrap `NodeKey`s and show the node text and its first icon.
- Selecting an element navigates to the node: the designer expands folded ancestors in view
  state only (not in the file), selects it and scrolls it into view.
- Selecting in the designer updates the model's current element, so "Autoscroll from Source"
  selects it in the Structure view.
- The designer publishes its selection through `DataContext`: `PlatformCoreDataKeys.SELECTED_ITEMS`
  holds the selected `NodeKey`s, and `ADP_DESIGNER` (a `DataKey<AdpDesignerEditor<?>>`) holds the
  designer, which actions read.
