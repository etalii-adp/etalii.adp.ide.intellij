# Data Model: Diagram Designer Framework

All types live in `etalii.adp.core.diagram` and its subpackages unless noted. Definition and model types are immutable records or final classes. Ids are non-empty strings matching `[A-Za-z][A-Za-z0-9_.-]*`, unique within their kind in one definition.

## Definition (declared once per designer)

### DiagramDefinition

| Field | Type | Rules |
|---|---|---|
| `id` | String | The designer's id, for example `sample`, `freemind`, `drawio`. |
| `elementTypes` | ordered map id → `ElementType` | At least one. |
| `connectionTypes` | ordered map id → `ConnectionType` | May be empty. |
| `toolbox` | list of type ids | Each names a declared element or connection type. Defaults to all types in declaration order. |
| `sectors` | map id → `SectorDecl` | May be empty. |
| `view` | `ViewOptions` | Defaults: zoom on, pan on. |
| `rules` | `DiagramRules` | Defaults to allow everything. |
| `listener` | `DiagramListener` | Defaults to none. |
| `layout` | `DiagramLayout` or null | Null: positions come from the diagram (research R7). |

`build()` validates everything below and throws `DefinitionException(List<String> problems)` with every problem, each prefixed with the declaration path, for example `element 'task' > anchor 'in'`.

### ElementType

| Field | Type | Rules |
|---|---|---|
| `id`, `label` | String | `label` is shown in the toolbox and panel. |
| `outline` | `Outline` | One of the predefined outlines, or `custom`. |
| `tone` | `Tone` | Predefined or custom `JBColor` triple. |
| `texts` | ordered list of `TextSlot` | Slot ids are unique within the type. Each slot's `property` must be a declared property of the type. |
| `sizing` | `Sizing` | `auto(maxWidth)`, `fixed(w, h)` or `fromDiagram(minW, minH)`. |
| `anchors` | ordered list of `Anchor` | Ids are unique within the type. |
| `selectable`, `movable`, `droppableOnto` | boolean | `movable` requires `selectable`. `movable` requires the definition to have no `layout`. |
| `resize` | `Resize` | `NONE`, `HORIZONTAL`, `VERTICAL` or `BOTH`. Must be `NONE` when sizing is `fixed`. With `auto`, a horizontal resize sets the width and the height still fits the text. |
| `properties` | ordered list of `PropertyDecl` | Ids are unique within the type. |

### TextSlot

| Field | Type | Rules |
|---|---|---|
| `id` | String | |
| `property` | String | The property whose value is shown. |
| `position` | `SlotPosition` | `TOP_LEFT` … `BOTTOM_RIGHT` (nine), `ABOVE`, `BELOW`. |
| `wrap` | boolean | Without wrap, overflow is clipped with an ellipsis. |
| `style` | `PLAIN`, `BOLD`, `ITALIC`, `SMALL` | |
| `editable` | boolean | Must be false when the property is read-only. |

### Anchor

| Field | Type | Rules |
|---|---|---|
| `id` | String | |
| `position` | `(fx, fy)` in [0, 1] × [0, 1], or `PERIMETER` | |
| `visible` | boolean | Invisible anchors still attach connections. |
| `accepts` | map connection type id → `Direction` (`IN`, `OUT`, `BOTH`) | Each id must be a declared connection type. |

### ConnectionType

| Field | Type | Rules |
|---|---|---|
| `id`, `label` | String | |
| `line` | `LineStyle` | `STRAIGHT`, `ORTHOGONAL`, `CURVED`. |
| `dash` | `Dash` | `SOLID`, `DASHED`, `DOTTED`. |
| `thickness` | float | 0.5 to 8, in unscaled pixels. |
| `tone` | `Tone` | |
| `sourceArrow`, `targetArrow` | `ArrowHead` | `NONE`, `OPEN`, `FILLED`, `DIAMOND`, `OPEN_DIAMOND`, `CIRCLE`, `BAR`. |
| `labels` | map `LabelSlot` (`MIDDLE`, `SOURCE`, `TARGET`) → (property, editable) | Each property must be a declared property of the type. |
| `routed` | boolean | Orthogonal lines only (research R12). |
| `userConnectable` | boolean | False: drawn from the diagram but never created or removed by a gesture (FreeMind `branch`). |
| `properties` | ordered list of `PropertyDecl` | |

A connection type must be accepted by at least one anchor in each direction, unless it is not user-connectable.

Line, dash, arrow and thickness may be overridden per connection by the mapping when the file stores them (draw.io). The declaration is the default.

### PropertyDecl

| Field | Type | Rules |
|---|---|---|
| `id`, `label`, `category` | String | `category` groups rows in the panel; it defaults to "General". |
| `editor` | `EditorKind` | `TEXT`, `MULTILINE`, `NUMBER(integer: boolean, min, max)`, `BOOLEAN`, `CHOICE(List<Choice(value, label)>)`, `COLOR`. |
| `readOnly` | boolean | |

Value notation (strings, in the file's own terms): `BOOLEAN` is `true`/`false` unless the mapping translates it; `COLOR` is `#RRGGBB`; `NUMBER` is a decimal literal; `CHOICE` is one of the declared values. The panel refuses input that does not fit, before any edit is made.

### SectorDecl and ViewOptions

`SectorDecl(id, label, orientation: HORIZONTAL | VERTICAL, space: DIAGRAM | VIEW)`.

`ViewOptions(zoom: boolean, pan: boolean, grid: int)`. `grid` is the move and resize snap in unscaled pixels; 0 means no snap.

## Diagram (re-read from the text after every change)

### Diagram

`Diagram(Map<Object, Element> elements, Map<Object, Connection> connections, Map<Object, Sector> sectors)`. The maps are in document order, which is also the painting order.

### Element

| Field | Type | Notes |
|---|---|---|
| `key` | Object | Stable across re-reads; equals and hashCode by value. |
| `type` | String | A declared id, or an unknown id, which makes the element a placeholder. |
| `bounds` | `Rectangle2D` or null | Null for laid-out designers and auto-sized elements without a stored size. |
| `properties` | `Map<String, String>` | Values of the declared properties. A missing property shows empty. |
| `sector` | Object or null | The key of the sector it belongs to according to the file. |
| `parent` | Object or null | For drop targets and layouts (FreeMind tree). |
| `style` | `StyleOverride` or null | Fill, border and text colours read from the file. |

### Connection

| Field | Type | Notes |
|---|---|---|
| `key`, `type`, `properties`, `style` | as for Element | `style` may also override line, dash, arrows and thickness. |
| `source`, `target` | `End(elementKey, anchorId)` | `anchorId` null means the perimeter. |
| `waypoints` | `List<Point2D>` | Drawn through; preserved; not edited in this feature. |

### Sector

`Sector(key, declId, label, bounds)`. For view-space sectors, bounds are relative to the viewport.

## Changes

### DiagramChange (emitted after each re-read, research R8)

A sealed interface with these records: `Added(key)`, `Removed(key)`, `Moved(key, from, to)`, `Resized(key, from, to)`, `Connected(key, source, target)`, `Disconnected(key)`, `PropertyChanged(key, property, from, to)` and `SectorChanged(key, from, to)`. Keys identify elements or connections. A reconnect is `Disconnected` followed by `Connected` for the same key.

### Verdict

`Verdict.allow()` or `Verdict.refuse(String reason)`. The reason is shown to the user as is.

### Mapping requests (the arguments the framework passes to `DiagramMapping`)

| Request | Fields |
|---|---|
| `add` | element type id, bounds or drop target, sector, initial properties from the type's defaults |
| `remove` | keys of elements and connections (cascade already included) |
| `setBounds` | list of (key, new bounds, new sector or unchanged) |
| `drop` | keys, target key, `Placement` (`BEFORE`, `AFTER`, `INTO`) |
| `connect` | connection type id, source `End`, target `End` |
| `reconnect` | connection key, which end, new `End` |
| `setProperty` | keys, property id, new value |

Each returns `TextChanges`, or an empty `TextChanges` when there is nothing to change.

## View state (never written to the file)

`ViewState` is unchanged: zoom levels, selection (element and connection keys) and fold overrides.

### ElementView and ConnectionView (read by the test kit)

`ElementView(key, type, Rectangle bounds, Map<String, String> texts, Color fill, Color border, Color text, Font font, boolean placeholder)`, with bounds in unzoomed coordinates. `texts` holds the text as drawn, per slot.

`ConnectionView(key, type, List<Point> route, LineStyle line, Dash dash, ArrowHead source, ArrowHead target, Map<LabelSlot, String> labels, boolean placeholder)`.

`NodeView` stays for the existing test kit. `DiagramDesigner.viewOf` derives it from the `ElementView` (research R19).

## State transitions

- **Designer**: showing a diagram ↔ showing a problem, on each re-read. This is unchanged from spec 002.
- **Gesture**: idle → pressed → dragging (with feedback: allowed or refused) → released. A release either commits one command or shows a refusal and changes nothing. Escape during a drag returns to idle without changes.
- **In-place edit**: closed → open (on double-click of an editable slot) → closed by commit (one command), cancel, loss of focus, or the item disappearing on a re-read (nothing applied).
- **Panel**: empty (no designer, or no selection) ↔ showing rows for the selection. Every model or selection change rebuilds the rows. An open cell editor is cancelled when its item disappears.

## Sample designer (test fixture, `.adpsample`)

```xml
<sample>
  <lane id="l1" label="Customer" y="0" height="200"/>
  <box id="a" type="task" x="40" y="40" w="120" h="60" lane="l1" owner="Ann">Place order</box>
  <box id="b" type="decision" x="240" y="40" lane="l1">Paid?</box>
  <link id="f1" type="flow" from="a" fromAnchor="out" to="b" toAnchor="in" note="yes" readOnlyEnd="1">submit</link>
</sample>
```

It declares two element types: `task` (rounded rectangle, auto-size, horizontal resize only) and `decision` (diamond, fixed size). It declares two connection types: `flow` (orthogonal, open arrow, editable middle label, read-only end labels) and `note` (curved, dashed). It also has diagram-space lanes, a view-space legend sector, and a rule refusing to delete the last `task`.

## draw.io mapping

| draw.io | Diagram |
|---|---|
| `mxCell[@vertex="1"]` not a swimlane | `Element`, key `id`, type from the style (research R20), properties `label` (`value`), `fillColor`, `strokeColor`, `fontColor`, `dashed`, `rounded`, `id` (read-only) |
| `mxCell` with `swimlane` in its style | `Sector` in diagram space, key `id` |
| `mxCell[@edge="1"]` | `Connection`, source and target from `source`/`target`, anchors from `exitX/exitY`, `entryX/entryY` matched to declared anchors, or the perimeter |
| edge style keys | properties `edgeStyle` (choice: straight, orthogonal, curved), `startArrow`, `endArrow` (choice), `dashed`, `strokeWidth` |
| child `mxCell` with `edgeLabel` style, `x=-1` / `x=1` | `SOURCE` / `TARGET` label of its parent edge |
| `mxGeometry` `x y width height`, relative to its parent swimlane | absolute `bounds` |
| `mxGeometry/Array[@as="points"]/mxPoint` | `waypoints` |
| cells with `parent` other than the default layer or a swimlane | placeholder (groups are not supported in this feature) |
