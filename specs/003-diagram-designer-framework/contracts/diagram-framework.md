# Contract: Diagram Designer Framework (`etalii.adp.core.diagram`)

What a designer author codes against. Entities and validation rules are in `../data-model.md`. Spec 002's `AdpEditorProvider` and `AdpDesignerEditor` contracts still hold. The diagram classes below implement them, so a diagram designer writes none of their abstract methods itself.

## What a designer module contains

| File | Written by the author | Size target |
|---|---|---|
| `<Name>Definition` | the `DiagramDefinition`, built once | declarations only |
| `<Name>Mapping` | `implements DiagramMapping` | read and edit the file |
| `<Name>EditorProvider` | `extends DiagramEditorProvider` | extensions, sniff, names |
| `adp-<name>.xml` | `fileEditorProvider` (and `fileType` if the extension is new) | registration |
| optional | `DiagramRules`, `DiagramListener`, `DiagramLayout` | only when needed |

No drawing, hit-testing, selection, undo or property panel code (FR-001, SC-001).

## Declaring

```java
public static final DiagramDefinition DEFINITION = DiagramDefinition.builder("sample")
    .element("task", e -> e
        .label("Task")
        .outline(Outline.ROUNDED_RECTANGLE)
        .tone(Tone.BLUE)
        .text("title", t -> t.property("title").position(SlotPosition.CENTER).wrap(true).editable(true))
        .sizing(Sizing.auto(200))
        .resize(Resize.HORIZONTAL)          // with auto sizing: width set by the user, height fits the text
        .anchor("in",  a -> a.at(0, 0.5).visible(true).accepts("flow", Direction.IN))
        .anchor("out", a -> a.at(1, 0.5).visible(true).accepts("flow", Direction.OUT))
        .property("title", p -> p.label("Title").editor(EditorKind.MULTILINE))
        .property("id",    p -> p.label("Id").editor(EditorKind.TEXT).readOnly(true)))
    .connection("flow", c -> c
        .label("Flow")
        .line(LineStyle.ORTHOGONAL).dash(Dash.SOLID).thickness(1.5f).tone(Tone.NEUTRAL)
        .arrows(ArrowHead.NONE, ArrowHead.OPEN)
        .label(LabelSlot.MIDDLE, "label", true)
        .label(LabelSlot.TARGET, "note", false)
        .routed(true)
        .property("label", p -> p.label("Label").editor(EditorKind.TEXT))
        .property("note",  p -> p.label("Note").editor(EditorKind.TEXT).readOnly(true)))
    .sector("lane", s -> s.label("Lane").orientation(Orientation.HORIZONTAL).space(Space.DIAGRAM))
    .toolbox("task", "flow")
    .view(v -> v.zoom(true).pan(true).grid(10))
    .rules(new SampleRules())
    .listener(changes -> { /* designer-specific follow-up */ })
    .build();                                // throws DefinitionException listing every problem
```

Builders are the only way to create definition types. `build()` is the single validation point (FR-002).

## Mapping (the author's code, research R5)

```java
public interface DiagramMapping {
    /** Read the whole text. Unknown types are returned with their own type id. Must not modify anything. */
    Diagram read(CharSequence text) throws FormatProblem;

    // Each returns range-exact changes against `text`, which is the text `diagram` was read from.
    // An empty TextChanges means "nothing to do". Changes must not overlap: merge nested ranges.
    TextChanges add(CharSequence text, Diagram diagram, AddRequest request);
    TextChanges remove(CharSequence text, Diagram diagram, Set<Object> keys);    // cascade included by the framework
    TextChanges setBounds(CharSequence text, Diagram diagram, List<BoundsChange> changes);  // incl. sector changes
    TextChanges connect(CharSequence text, Diagram diagram, String connectionType, End source, End target);
    TextChanges reconnect(CharSequence text, Diagram diagram, Object connection, EndSide side, End end);
    TextChanges setProperty(CharSequence text, Diagram diagram, Set<Object> keys, String property, String value);

    /** Only for designers with a DiagramLayout. Default: empty (not supported). */
    default TextChanges drop(CharSequence text, Diagram diagram, Set<Object> keys, Object target, Placement placement) {
        return TextChanges.of();
    }
}
```

`AddRequest(String type, Rectangle2D bounds /* null when dropped onto a target */, Object target, Object sector, Map<String, String> properties)`. The framework chooses the default size from the type's sizing. The mapping chooses the new key and is told nothing more. The framework finds the new element by diffing and selects it.

## Rules and notifications

```java
public interface DiagramRules {
    default Verdict canAdd(Diagram d, String type, Object targetOrSector) { return Verdict.allow(); }
    default Verdict canRemove(Diagram d, Set<Object> keys)                { return Verdict.allow(); }
    default Verdict canConnect(Diagram d, String type, End source, End target) { return Verdict.allow(); }
    default Verdict canDisconnect(Diagram d, Object connection)           { return Verdict.allow(); }
    default Verdict canDrop(Diagram d, Set<Object> keys, Object target, Placement placement) { return Verdict.allow(); }
}

@FunctionalInterface
public interface DiagramListener { void changed(DiagramDesigner designer, List<DiagramChange> changes); }
```

The order of checks for every gesture is: definition permissions, then rules, then the mapping. The first refusal wins, and nothing is written.

## Layout (optional, research R7)

```java
public interface DiagramLayout {
    /** Bounds for every element to show; elements left out are not drawn. `measure` gives each element's auto size. */
    Map<Object, Rectangle2D> layout(Diagram diagram, ViewState view, Function<Element, Dimension2D> measure);
}
```

## Provider and designer

```java
public abstract class DiagramEditorProvider extends AdpEditorProvider {
    protected DiagramEditorProvider(DiagramDefinition definition, Supplier<DiagramMapping> mapping);
    // author still implements: extensions(), sniff(byte[]), editorName(), getEditorTypeId()
    // createDesigner(...) is provided: returns a DiagramDesigner; override to return a subclass (FreeMind).
}

public class DiagramDesigner extends AdpDesignerEditor<Diagram> {
    public DiagramDefinition definition();
    public Diagram diagram();                      // same as model()
    public List<Object> selection();               // element and connection keys
    public ElementView elementView(Object key);     // null when not shown
    public ConnectionView connectionView(Object key);
    @Override public NodeView viewOf(Object key);   // derived from elementView, for the existing test kit
    public Verdict lastRefusal();                  // for tests; null when the last gesture was allowed

    // for designer-specific actions (FreeMind fold, tree navigation):
    protected void navigate(Object from, Direction direction);   // default: nearest element in that direction
    public void runCommand(String label, Function<CharSequence, TextChanges> edit, Runnable reselect);
}
```

`AdpDesignerEditor.execute` is unchanged, and every framework gesture goes through it.

## Canvas features (framework-internal extension point)

```java
/** Registered as <etalii.adp.diagramFeature implementation="..."/>; installed on every DiagramCanvas. */
public interface DiagramFeature {
    void install(DiagramDesigner designer, DiagramCanvas canvas, Disposable lifetime);
}
```

`DiagramCanvas.addLayer(CanvasLayer)` paints in diagram or view space in a declared order. `addTool(CanvasTool)` receives mouse and key events in registration order until one consumes them. Editing, properties and navigation each register one feature from their own descriptor fragment. Designer authors do not implement it.

## Command labels (shown as "Undo <label>")

| Gesture | Label |
|---|---|
| toolbox drop | `Add <type label>` |
| connect | `Connect <type label>` |
| reconnect | `Reconnect <type label>` |
| move, one or many | `Move` |
| resize | `Resize` |
| drop onto (laid-out designers) | `Move` |
| delete | `Delete` |
| panel or in-place edit | `Change <property label>` |
| sector change only | `Move to <sector label>` |

## XML helpers (`etalii.adp.core.xml`, no platform imports)

```java
public final class XmlScanner { public static List<Token> scan(CharSequence text); }   // moved from freemind
public final class XmlTree {                   // elements with exact ranges, built on XmlScanner
    public static XmlTree of(CharSequence text) throws FormatProblem;
    public XmlElement root();
}
public record XmlElement(String name, Range range, Range startTag, Map<String, XmlAttribute> attributes,
                         List<XmlElement> children, int attributeInsertPoint, int childInsertPoint,
                         String indent, Range content) {}
public final class XmlEdits {                  // each returns a TextChange, preserving quotes and indentation
    public static TextChange setAttribute(XmlElement e, String name, String value);   // insert when absent
    public static TextChange removeAttribute(XmlElement e, String name);
    public static TextChange insertChild(CharSequence text, XmlElement parent, int index, String elementXml);
    public static TextChange remove(CharSequence text, XmlElement e);                 // with its own line when alone on it
    public static TextChange setContent(XmlElement e, String text);                    // escaped
    public static String escape(String text);
}
```
