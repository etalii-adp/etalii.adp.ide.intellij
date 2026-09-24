# Contract: Workbench Contributions

Everything `etalii.adp.freemind/plugin.xml` contributes. The IDs are the contract that tests,
key-binding preferences and later features code against, so they are stable once released.

## Content type (FR-002)

| Attribute | Value |
|---|---|
| id | `etalii.adp.freemind.mindmap` |
| name | FreeMind Mind Map |
| base-type | `org.eclipse.core.runtime.xml` |
| file-extensions | `mm` |
| describer | `etalii.adp.freemind.MindMapContentDescriber`: VALID when the first element is `<map>`, INVALID otherwise (the platform XML root describer answers INDETERMINATE for non-XML text, so an Objective-C++ `.mm` would still match) |

## Editor (FR-001, FR-003)

| Attribute | Value |
|---|---|
| id | `etalii.adp.freemind.editor` |
| name | FreeMind Mind Map (ADP) |
| class | `etalii.adp.freemind.ui.MindMapEditor` |
| contributorClass | `etalii.adp.core.AdpActionBarContributor` |
| contentTypeBinding | `etalii.adp.freemind.mindmap` |
| default | `true` |
| extensions | *(none: binding is by content type only)* |

## New wizard (FR-025)

| Attribute | Value |
|---|---|
| category id / name | `etalii.adp.newWizards` / A Different Perspective (ADP) |
| wizard id | `etalii.adp.freemind.newWizard` |
| name | FreeMind Mind Map |
| class | `etalii.adp.freemind.ui.NewMindMapWizard` |

The initial file content is FreeMind 1.0.1's own new-map form, with a fresh `ID` and fresh
timestamps, and the root text "New Mindmap":

```xml
<map version="1.0.1">
<!-- To view this file, download free mind mapping software FreeMind from http://freemind.sourceforge.net -->
<node CREATED="…" ID="ID_…" MODIFIED="…" TEXT="New Mindmap"/>
</map>
```

## Commands, key bindings and context menu (FR-019 to FR-024)

The bindings are active only in context `etalii.adp.freemind.context` (parent
`org.eclipse.ui.contexts.window`), which the visual page activates while it has focus. The text
page keeps the text editor's bindings. `M1` is Ctrl, or Cmd on macOS.

| Command id | Label | Key | Enabled when |
|---|---|---|---|
| `etalii.adp.freemind.addChild` | Add Child Node | `Insert`, `Tab` | one node selected, editable |
| `etalii.adp.freemind.addSibling` | Add Sibling Node | `Enter` | one non-root node selected, editable |
| `etalii.adp.freemind.rename` | Rename Node | `F2` | one node selected, editable |
| `etalii.adp.freemind.delete` | Delete Node | `Delete` | non-root nodes selected, editable |
| `etalii.adp.freemind.moveUp` | Move Node Up | `M1+Up` | one non-root node with a previous sibling |
| `etalii.adp.freemind.moveDown` | Move Node Down | `M1+Down` | one non-root node with a next sibling |
| `etalii.adp.freemind.indent` | Move Under Previous Sibling | `M1+Right` | one non-root node with a previous sibling |
| `etalii.adp.freemind.outdent` | Move Up a Level | `M1+Left` | one node at depth two or deeper |
| `etalii.adp.freemind.toggleFold` | Fold / Unfold Branch | `Space` | nodes with children selected (view-only when read-only, R7) |

Undo, redo and select all use the platform's command IDs (`org.eclipse.ui.edit.undo`,
`org.eclipse.ui.edit.redo`, `org.eclipse.ui.edit.selectAll`), and zoom uses GEF's
(`org.eclipse.gef.zoom_in`, `org.eclipse.gef.zoom_out`, Ctrl+= and Ctrl+-), through global action handlers, so
their standard bindings apply.

The visual page's context menu has id `etalii.adp.freemind.editor.context`. It lists every
command above in table order, with separators after `addSibling`, `delete` and `outdent`, then
the standard `additions` group.

## Workbench adapters (FR-026, FR-027)

- `MindMapEditor.getAdapter(IContentOutlinePage.class)` returns a `MindMapOutlinePage`. Its tree
  elements are `NodeKey`s, and it links selection in both directions (US4). The page comes from
  `etalii.adp.freemind.ui.MindMapOutlineAdapterFactory`, registered in
  `org.eclipse.core.runtime.adapters` for `MindMapEditor`, which `WorkbenchPart.getAdapter`
  consults. The core framework has no outline hook.
- On the visual page, the editor site's selection provider publishes an `IStructuredSelection` of
  the selected GEF edit parts, each adaptable to `NodeKey`. On the text page it publishes the
  text editor's selection, because that is `MultiPageEditorPart`'s standard behaviour.
