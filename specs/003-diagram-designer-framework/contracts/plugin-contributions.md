# Contract: Plug-in Contributions

Additions to spec 002's `plugin.xml` contract. Ids are what tests and keymaps code against.

## `plugin.xml` (root)

Five new includes, each with `<xi:fallback/>`, after the existing ones:

```xml
<xi:include href="/META-INF/adp-diagram.xml" xpointer="xpointer(/idea-plugin/*)"><xi:fallback/></xi:include>
<xi:include href="/META-INF/adp-diagram-editing.xml" xpointer="xpointer(/idea-plugin/*)"><xi:fallback/></xi:include>
<xi:include href="/META-INF/adp-diagram-properties.xml" xpointer="xpointer(/idea-plugin/*)"><xi:fallback/></xi:include>
<xi:include href="/META-INF/adp-diagram-navigation.xml" xpointer="xpointer(/idea-plugin/*)"><xi:fallback/></xi:include>
<xi:include href="/META-INF/adp-drawio.xml" xpointer="xpointer(/idea-plugin/*)"><xi:fallback/></xi:include>
```

The description gains draw.io as the second format.

## `adp-diagram*.xml` (core)

`adp-diagram.xml` declares the extension point `etalii.adp.diagramFeature` (interface `etalii.adp.core.diagram.DiagramFeature`, dynamic). Each story's fragment registers its feature and the rows below: `adp-diagram-editing.xml` the toolbox, Delete and the popup group; `adp-diagram-properties.xml` ADP Properties, EditInPlace and ShowProperties (added to the popup group with `add-to-group`); `adp-diagram-navigation.xml` only its feature.

| Kind | Id | Details |
|---|---|---|
| `toolWindow` | `ADP Toolbox` | `factoryClass="etalii.adp.core.diagram.toolbox.ToolboxToolWindowFactory"`, `anchor="right"`, `secondary="false"`, icon `AllIcons.Toolwindows.ToolWindowPalette`. Shows "Open an ADP diagram to see its toolbox" when no diagram designer is selected. |
| `toolWindow` | `ADP Properties` | `factoryClass="etalii.adp.core.diagram.properties.PropertiesToolWindowFactory"`, `anchor="right"`, `secondary="true"`. Empty states: "No ADP designer is active" and "Nothing selected". |
| `action` | `etalii.adp.core.Delete` | `DeleteAction`. It uses the platform's `$Delete` shortcut through `use-shortcut-of="$Delete"`, and is enabled only while a diagram designer's canvas has focus and something deletable is selected. |
| `action` | `etalii.adp.core.EditInPlace` | Opens in-place editing of the selected item's first editable text. Shortcut F2. |
| `action` | `etalii.adp.core.ShowProperties` | Activates `ADP Properties`. Shortcut `alt shift P`, only while the canvas has focus. |
| `group` | `etalii.adp.core.DiagramPopup` | Delete, a separator, then Zoom In, Zoom Out and Actual Size; EditInPlace and ShowProperties join it from the properties fragment. Diagram designers install it as the canvas popup. FreeMind adds its own group in front. |

The existing `etalii.adp.core.SelectAll` selects every selectable element and connection of a diagram designer, through `allKeys()`.

## `adp-freemind-editing.xml` (changed)

- `EditingInstaller` (a project listener) is removed. Gestures now come from the canvas.
- FreeMind's Delete and Rename actions stay, with their ids and shortcuts, so keymaps and tests keep working. They delegate to the framework's delete and in-place editing.

## `adp-drawio.xml` (new)

```xml
<idea-plugin>
    <extensions defaultExtensionNs="com.intellij">
        <fileType name="draw.io Diagram" implementationClass="etalii.adp.drawio.DrawioFileType"
                  fieldName="INSTANCE" extensions="drawio"/>
        <fileEditorProvider implementation="etalii.adp.drawio.DrawioEditorProvider"/>
    </extensions>
</idea-plugin>
```

- Editor type id: `etalii.adp.drawio`. Editor name: `draw.io Designer`.
- Sniff: after an optional BOM, XML declaration, whitespace and comments, the first element is `mxfile` or `mxGraphModel`. At most 4 KB are read.
- `.drawio.svg` and `.drawio.png` are not claimed (their extension is `svg` and `png`).
- If another plug-in already registers a `.drawio` file type, the platform keeps the first registration and logs a conflict. Our editor provider still accepts by extension and sniff, so both editors are offered. An integration test covers the case without the other plug-in only.

## Sample designer (tests only)

Registered in `core/src/test/resources/META-INF/plugin.xml` through `EP_FILE_EDITOR_PROVIDER` in each test's setup, like spec 002's `FakeFormat`. Extension `.adpsample`, editor type id `etalii.adp.sample`. It never ships.
