# Building a diagram

ADP's tools come in three kinds: **diagrams**, **designers** and **editors**, as defined in the [ADP glossary](https://github.com/etalii-adp/etalii.adp/blob/develop/docs/terminology.md). The framework in `core` has two layers. The tool framework (`etalii.adp.core` and `etalii.adp.core.settings`) is what every kind shares: the file editor lifecycle, text and visual synchronisation, undo, registration and the ADP settings page. The diagram framework (`etalii.adp.core.diagram`) builds on it for diagrams. This plug-in has no designer or editor yet; when the first one comes, it gets its own framework beside the diagram framework, on the same tool framework. This guide builds a diagram.

This guide takes you from an empty module to a working diagram for your own file format. The example is a small state machine: two element types (a state and an end state) and one connection type (a transition), stored in a `.states` file. You write four small classes and one XML fragment. You write no drawing, hit-testing, selection, undo, toolbox or property panel code: the framework in `core` does all of that.

The API is described in full in [contracts/diagram-framework.md](https://github.com/etalii-adp/etalii.adp/blob/develop/specs/etalii.adp.ide.intellij/003-diagram-designer-framework/contracts/diagram-framework.md), and the test kit in [contracts/test-kit.md](https://github.com/etalii-adp/etalii.adp/blob/develop/specs/etalii.adp.ide.intellij/003-diagram-designer-framework/contracts/test-kit.md). Two complete diagrams to read next to this guide: the sample diagram in `core/src/test/java/etalii/adp/core/diagram/sample/` and the draw.io diagram in `drawio/src/main/java/etalii/adp/drawio/`.

## What you are building

A `.states` file looks like this:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<states>
  <state id="idle" x="40" y="40">Idle</state>
  <state id="busy" x="240" y="40">Busy</state>
  <end id="done" x="440" y="50"/>
  <transition id="t1" from="idle" to="busy">start</transition>
  <transition id="t2" from="busy" to="done">finish</transition>
</states>
```

The file stays the only model. When the user edits the diagram, the framework asks your mapping for the exact text changes, runs them as one undoable command on the IDE's document, reads the text again, and redraws. When the user edits the text, the diagram follows. Opening and saving without edits leaves the file byte-identical.

## 1. The module

Copy `drawio/build.gradle.kts` to `states/build.gradle.kts`. It depends on `core` for the framework and on `testing` for the tests. Then:

- add `"states"` to `include(...)` in `settings.gradle.kts`;
- add `pluginComposedModule(implementation(project(":states")))` to the `intellijPlatform { }` dependencies in the root `build.gradle.kts`;
- add `<xi:include href="/META-INF/adp-states.xml" xpointer="xpointer(/idea-plugin/*)"><xi:fallback/></xi:include>` to `src/main/resources/META-INF/plugin.xml`;
- copy `drawio/src/test/resources/META-INF/plugin.xml` to `states/src/test/resources/META-INF/plugin.xml`, changing its id and name and including `adp-states.xml` instead of `adp-drawio.xml`. It must keep the `adp-core.xml` include and the four `adp-diagram*.xml` includes: they bring the canvas tools, the toolbox and the property panel into your tests.

Put your example files in `states/testdata/` and your classes in `states/src/main/java/<your package>/`. The examples below use the package `etalii.adp.states`.

The framework's types live in five packages. The samples below show every import they need.

- `etalii.adp.core`: `FormatProblem`, `TextChange`, `TextChanges` and `AdpFileType`.
- `etalii.adp.core.diagram`: the definition and its parts (`DiagramDefinition`, `Outline`, `Tone`, `Sizing`, `Direction`, `LineStyle`, `ArrowHead`, `LabelSlot`, `EditorKind`), `DiagramMapping` and its requests (`AddRequest`, `BoundsChange`, `EndSide`), and `DiagramRules` with `Verdict`.
- `etalii.adp.core.diagram.model`: the immutable model (`Diagram`, `Element`, `Connection`, `End`).
- `etalii.adp.core.diagram.view`: `DiagramEditorProvider`.
- `etalii.adp.core.xml`: `XmlTree`, `XmlElement` and `XmlEdits`.

## 2. The definition

The definition declares what your diagrams contain and how they look. It is built once. `build()` checks it and throws a `DefinitionException` that lists every problem, each naming the declaration it concerns, so a mistake shows up the first time the diagram loads.

```java
package etalii.adp.states;

import etalii.adp.core.diagram.ArrowHead;
import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.Direction;
import etalii.adp.core.diagram.LabelSlot;
import etalii.adp.core.diagram.LineStyle;
import etalii.adp.core.diagram.Outline;
import etalii.adp.core.diagram.Sizing;
import etalii.adp.core.diagram.Tone;

public final class StatesDefinition {

    public static final DiagramDefinition DEFINITION = DiagramDefinition.builder("states")
            .element("state", e -> e.label("State").outline(Outline.ROUNDED_RECTANGLE).tone(Tone.BLUE)
                    .text("name", t -> t.property("name").wrap(true).editable(true))
                    .sizing(Sizing.auto(160))
                    .anchor("in", a -> a.at(0, 0.5).accepts("transition", Direction.IN))
                    .anchor("out", a -> a.at(1, 0.5).accepts("transition", Direction.OUT))
                    .property("name", p -> p.label("Name").defaultValue("State"))
                    .property("id", p -> p.label("Id").readOnly(true)))
            .element("end", e -> e.label("End").outline(Outline.ELLIPSE).tone(Tone.GREY)
                    .sizing(Sizing.fixed(40, 40))
                    .anchor("in", a -> a.at(0, 0.5).accepts("transition", Direction.IN))
                    .property("id", p -> p.label("Id").readOnly(true)))
            .connection("transition", c -> c.label("Transition").line(LineStyle.CURVED)
                    .arrows(ArrowHead.NONE, ArrowHead.FILLED)
                    .label(LabelSlot.MIDDLE, "event", true)
                    .property("event", p -> p.label("Event"))
                    .property("id", p -> p.label("Id").readOnly(true)))
            .view(v -> v.grid(10))
            .rules(new StatesRules())
            .build();

    private StatesDefinition() {
    }
}
```

The `rules(...)` line uses `StatesRules`, which section 7 shows. Rules are optional. Either write that class now or leave the line out until you do.

What each part does:

- **Element types** have an outline (one of `Outline`'s shapes, or `Outline.custom(...)`), a `Tone` that reads well on light and dark themes, text slots that show property values at a position, a sizing (`auto`, `fixed` or `fromDiagram`), anchors, and the properties the panel shows. By default an element can be selected and moved but not resized; see `ElementType.Builder`.
- **Anchors** are where connections attach. `at(fx, fy)` is a point on the bounds (0,0 is the top left, 1,1 the bottom right). `perimeter()` attaches where the line meets the outline. `accepts(type, direction)` says which connection types may start (`OUT`), end (`IN`) or both (`BOTH`) at it. In the example a state takes transitions in on its left and sends them out on its right, and the end state only takes the end of a transition. A perimeter anchor is taken hold of on the outline: a press in the middle of an element moves it instead. `DiagramDriver.connect` presses on the outline for you.
- **Connection types** have a line (straight, orthogonal or curved), a dash, a thickness, a tone, an arrowhead at each end, and up to three labels (middle, source, target), each editable in place or not. `routed(true)` makes an orthogonal line go around other elements.
- **Properties** have an editor kind: `EditorKind.TEXT`, `MULTILINE`, `BOOLEAN`, `COLOR`, `number(integer, min, max)` or `choice(option(value, label), ...)`. Input that does not fit is refused before any edit is made. Values are strings in your file's own notation.
- **The toolbox** lists every type in declaration order unless you call `toolbox(...)`. **View options** switch pan and zoom and set the grid spacing (10 by default); whether the grid is shown and snapped to is the user's choice on the ADP settings page (see section 8). **Sectors** (`sector(...)`) declare swimlanes in diagram or view space; see the sample diagram.

Write a one-line test that the definition builds (`StatesDefinition.DEFINITION` is enough): a `DefinitionException` then fails the test with every problem listed.

## 3. The mapping

The mapping is the only code that knows your format. It reads the whole text into an immutable `Diagram`, and for each kind of edit it returns the exact `TextChanges` to make. Build it on the XML helpers in `etalii.adp.core.xml`:

- `XmlTree.of(text)` parses and keeps every element's exact ranges; `XmlTree.reread(text)` is the same for text you have read before.
- `XmlElement` gives `name()`, `range()`, `attribute(name)`, `attribute(name, fallback)`, `number(name, fallback)`, `text()`, `children()`, `child(name)` and `descendants()`.
- `XmlEdits` builds the changes: `update(element, name, value)` (set, remove when empty, or nothing when unchanged), `updateNumber` for coordinates, `number(value)` to write a coordinate the way `updateNumber` does, `insertChild(text, parent, index, xml)` (on its own line with the siblings' indentation and the file's line separator), `remove(text, element)` (with its line), `setContent`, `element(name, attributes, content)`, `attributes(...)`, `uniqueId(root, prefix)` and `changes(...)`, which drops the `null`s that mean "nothing to do".

```java
package etalii.adp.states;

import etalii.adp.core.FormatProblem;
import etalii.adp.core.TextChange;
import etalii.adp.core.TextChanges;
import etalii.adp.core.diagram.AddRequest;
import etalii.adp.core.diagram.BoundsChange;
import etalii.adp.core.diagram.DiagramMapping;
import etalii.adp.core.diagram.EndSide;
import etalii.adp.core.diagram.model.Connection;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;
import etalii.adp.core.diagram.model.End;
import etalii.adp.core.xml.XmlEdits;
import etalii.adp.core.xml.XmlElement;
import etalii.adp.core.xml.XmlTree;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StatesMapping implements DiagramMapping {

    @Override
    public Diagram read(CharSequence text) throws FormatProblem {
        XmlElement root = XmlTree.of(text).root();
        if (!root.name().equals("states")) {
            throw new FormatProblem("This is not a state machine: the root element is <" + root.name() + ">", root.range().offset());
        }
        List<Element> elements = new ArrayList<>();
        List<Connection> connections = new ArrayList<>();
        for (XmlElement child : root.children()) {
            String id = child.attribute("id");
            if (child.name().equals("transition")) {
                connections.add(new Connection(id, "transition", new End(child.attribute("from"), null), new End(child.attribute("to"), null),
                        Map.of("event", child.text(), "id", id), null, List.of()));
            } else {
                elements.add(new Element(id, child.name(), new Rectangle2D.Double(child.number("x", 0), child.number("y", 0), 0, 0),
                        Map.of("name", child.text(), "id", id), null, null, null));
            }
        }
        return Diagram.of(elements, connections, List.of());
    }

    @Override
    public TextChanges add(CharSequence text, Diagram diagram, AddRequest request) {
        XmlElement root = XmlTree.reread(text).root();
        Map<String, String> attributes = XmlEdits.attributes("id", XmlEdits.uniqueId(root, request.type()),
                "x", XmlEdits.number(request.bounds().getX()), "y", XmlEdits.number(request.bounds().getY()));
        String xml = XmlEdits.element(request.type(), attributes, request.properties().get("name"));
        return XmlEdits.changes(XmlEdits.insertChild(text, root, root.children().size(), xml));
    }

    @Override
    public TextChanges remove(CharSequence text, Diagram diagram, Set<Object> keys) {
        return XmlEdits.changes(byId(text).entrySet().stream().filter(e -> keys.contains(e.getKey()))
                .map(e -> XmlEdits.remove(text, e.getValue())).toList());
    }

    @Override
    public TextChanges setBounds(CharSequence text, Diagram diagram, List<BoundsChange> changes) {
        Map<String, XmlElement> byId = byId(text);
        List<TextChange> edits = new ArrayList<>();
        for (BoundsChange change : changes) {
            XmlElement element = byId.get(change.key());
            edits.add(XmlEdits.updateNumber(element, "x", change.bounds().getX()));
            edits.add(XmlEdits.updateNumber(element, "y", change.bounds().getY()));
        }
        return XmlEdits.changes(edits);
    }

    @Override
    public TextChanges connect(CharSequence text, Diagram diagram, String connectionType, End source, End target) {
        XmlElement root = XmlTree.reread(text).root();
        String xml = XmlEdits.element("transition", XmlEdits.attributes("id", XmlEdits.uniqueId(root, "t"),
                "from", source.elementKey().toString(), "to", target.elementKey().toString()), null);
        return XmlEdits.changes(XmlEdits.insertChild(text, root, root.children().size(), xml));
    }

    @Override
    public TextChanges reconnect(CharSequence text, Diagram diagram, Object connection, EndSide side, End end) {
        XmlElement element = byId(text).get(connection);
        return XmlEdits.changes(XmlEdits.update(element, side == EndSide.SOURCE ? "from" : "to", end.elementKey().toString()));
    }

    @Override
    public TextChanges setProperty(CharSequence text, Diagram diagram, Set<Object> keys, String property, String value) {
        Map<String, XmlElement> byId = byId(text);
        return XmlEdits.changes(keys.stream().map(byId::get)
                .map(element -> element.text().equals(value) ? null : XmlEdits.setContent(element, value)).toList());
    }

    private static Map<String, XmlElement> byId(CharSequence text) {
        Map<String, XmlElement> byId = new LinkedHashMap<>();
        XmlTree.reread(text).root().children().forEach(child -> byId.put(child.attribute("id"), child));
        return byId;
    }
}
```

The rules the mapping lives by:

- **Keys** are your own stable values, compared by value (here the `id` attribute), so the selection survives every re-read.
- **Ends.** An `End` is an element key and an anchor id. A `null` anchor means "no anchor", and the line then meets the element's outline. This format does not store anchors, so it reads every end with `null`.
- **Changes are range-exact and do not overlap.** Change only what the edit is about; the rest of the file keeps its bytes, including everything your mapping does not understand.
- **Unknown types** are read with the type id you found. The framework draws them as placeholders, keeps them, and refuses to edit them.
- **Sizes.** A stored width or height of 0 means "not stored": the framework sizes the element from its type's sizing. Here states are auto-sized and the end state has a fixed size, so the file stores only positions.
- **Cascades are done for you.** `remove` already receives the connections of removed elements, and the framework has checked the definition and your rules before it calls you.
- **A text you cannot show** raises `FormatProblem` with its offset. The file then opens in the text view with your explanation.

## 4. The provider

The provider tells the IDE which files are yours. The constructor takes the definition, the mapping, the editor type id, the name shown on the editor, the file extension, and the root element names that identify your format. A file is claimed only when both its extension and its first element match, so other files with the same extension are left alone.

```java
package etalii.adp.states;

import etalii.adp.core.diagram.view.DiagramEditorProvider;

public final class StatesEditorProvider extends DiagramEditorProvider {

    public StatesEditorProvider() {
        super(StatesDefinition.DEFINITION, StatesMapping::new, "etalii.adp.states", "State Machine Diagram", "states", "states");
    }
}
```

If the extension is new to the IDE, give it a file type so the files open as text too:

```java
package etalii.adp.states;

import com.intellij.icons.AllIcons;
import etalii.adp.core.AdpFileType;

public final class StatesFileType extends AdpFileType {

    public static final StatesFileType INSTANCE = new StatesFileType();

    private StatesFileType() {
        super("State Machine", "State machine diagram", "states", AllIcons.FileTypes.Diagram);
    }
}
```

## 5. Registration

`states/src/main/resources/META-INF/adp-states.xml`:

```xml
<idea-plugin>
    <extensions defaultExtensionNs="com.intellij">
        <fileType name="State Machine" implementationClass="etalii.adp.states.StatesFileType" fieldName="INSTANCE" extensions="states"/>
        <fileEditorProvider implementation="etalii.adp.states.StatesEditorProvider"/>
    </extensions>
</idea-plugin>
```

Run `./gradlew runIde` and open a `.states` file: it is drawn from your definition. The ADP Toolbox and ADP Properties tool windows (on the right) follow the selected diagram. Drag a state from the toolbox, connect two states by dragging from one state's right anchor to another state's left anchor, move, delete, rename in place with a double-click or F2, and undo each step with Ctrl+Z.

## 6. Tests

The copied build file runs JUnit 5 tests and, through the vintage engine, the JUnit 4 platform tests. The definition test is plain JUnit 5:

```java
package etalii.adp.states;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class StatesDefinitionTest {

    @Test
    void definitionBuilds() {
        assertNotNull(StatesDefinition.DEFINITION);
    }
}
```

Mapping tests are plain JUnit 5 too: read each example file, apply each kind of edit with `TextChanges.applyTo(text)`, which returns the new text, and compare the result with the exact text you expect. Include a round trip (an edit and its inverse give the original bytes) and a file with an unknown element. A `BoundsChange` takes the element's key, its new bounds, its new sector and whether the sector changed. Pass `null` and `false` when your definition has no sectors. Read the example files from the directory in the `adp.testdata` system property:

```java
package etalii.adp.states;

import etalii.adp.core.diagram.BoundsChange;
import etalii.adp.core.diagram.model.Diagram;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatesMappingTest {

    private final StatesMapping mapping = new StatesMapping();

    @Test
    void moveAndMoveBackGiveTheOriginalBytes() throws Exception {
        String text = Files.readString(Path.of(System.getProperty("adp.testdata"), "traffic.states"));
        Diagram diagram = mapping.read(text);
        String moved = mapping.setBounds(text, diagram,
                List.of(new BoundsChange("idle", new Rectangle2D.Double(60, 80, 0, 0), null, false))).applyTo(text);
        assertEquals(text.replace("<state id=\"idle\" x=\"40\" y=\"40\">", "<state id=\"idle\" x=\"60\" y=\"80\">"), moved);
        String back = mapping.setBounds(moved, mapping.read(moved),
                List.of(new BoundsChange("idle", new Rectangle2D.Double(40, 40, 0, 0), null, false))).applyTo(moved);
        assertEquals(text, back);
    }
}
```

Diagram tests run in a headless IDE with `DiagramDriver` from the `testing` module. They are JUnit 4 tests on the platform's `FileEditorManagerTestCase`. The driver opens a file, dispatches real mouse and key events to the canvas, and lets you observe what is drawn. A path relative to the module directory finds your test data:

```java
package etalii.adp.states;

import com.intellij.testFramework.FileEditorManagerTestCase;
import etalii.adp.testing.DiagramDriver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.file.Path;

@RunWith(JUnit4.class)
public class StatesDiagramTest extends FileEditorManagerTestCase {

    @Test
    public void connectAndUndo() {
        try (var d = DiagramDriver.open(myFixture, Path.of("testdata/traffic.states"))) {
            String before = d.driver().text();
            d.connect("transition", "done", "in", "idle", "in");
            assertEquals("'transition' may not start at anchor 'in' of 'end'", d.refusal());
            d.connect("transition", "busy", "out", "idle", "in");
            assertEquals(3, d.connectionKeys().size());
            d.driver().undo();
            assertEquals(before, d.driver().text());
        }
    }
}
```

A connection that starts at an anchor that does not accept it as a start is refused with `'<connection type>' may not start at anchor '<anchor id>' of '<element type>'`, and the text is left unchanged.

Useful driver methods: `elementView(key)` and `connectionView(key)` (what is drawn: bounds, texts, colours, route, arrows, labels), `moveBy`, `resize`, `connect`, `marquee`, `doubleClickText`, `zoom`, `refusal()` and `lastChanges()`. Headless tests do not create tool windows from the plug-in descriptor. To test the toolbox or the property panel, register them as `freemind/src/test/java/etalii/adp/freemind/ui/FreeMindToolboxTest.java` and `FreeMindPropertiesTest.java` do. Test data is found through the `adp.testdata` system property that the copied build file sets.

## 7. Optional: rules, a listener and a layout

**Rules** decide what the definition alone cannot. Each method returns `Verdict.allow()` or `Verdict.refuse(reason)`; the reason is shown to the user in a balloon, and nothing is changed. The framework asks the definition's permissions first, then your rules, then your mapping.

```java
package etalii.adp.states;

import etalii.adp.core.diagram.DiagramRules;
import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.End;

public final class StatesRules implements DiagramRules {

    @Override
    public Verdict canConnect(Diagram d, String type, End source, End target) {
        return source.elementKey().equals(target.elementKey()) ? Verdict.refuse("a state cannot move to itself") : Verdict.allow();
    }
}
```

Every rule has a default that allows, so you override only the ones you need. The other rules are `canAdd`, `canRemove`, `canDisconnect`, `canDrop` and `canSetProperty` (a value that is read-only for one item only).

**A listener** hears about every change after the text is read again, whether it came from the diagram, the text editor, undo or a change on disk: `definition.listener((diagram, changes) -> ...)`. The changes are `Added`, `Removed`, `Moved`, `Resized`, `Connected`, `Disconnected`, `PropertyChanged` and `SectorChanged`.

**A layout** is for formats that do not store positions, such as FreeMind. Give the definition a `DiagramLayout` that returns bounds for every element to show; its elements are then not moved freely but dropped onto other elements, which the mapping's `drop` turns into text. A definition with a layout may not declare movable element types. See `freemind/src/main/java/etalii/adp/freemind/ui/MindMapLayout.java`.

## 8. Optional: settings

Every diagram is listed on the IDE's own settings page, Settings > Tools > ADP, with its file types, version, origin and whether it loaded. The user can turn it off there, and the IDE then opens its files as it would without ADP. You write nothing for this: the list is read from your provider.

**Canvas options** apply to every diagram: Show grid, Snap to grid and the zoom a diagram opens at. When your format decides one of them itself, fix it in the definition and the page names your diagram under that option as not following it. FreeMind lays nodes out rather than placing them, so it fixes both grid options off:

```java
.view(v -> v.fix(CanvasOption.SHOW_GRID, false).fix(CanvasOption.SNAP_TO_GRID, false))
```

`grid(spacing)` sets only the spacing of the grid. A diagram that must not zoom turns zoom off with `zoom(false)`, and the opening zoom then does not apply to it.

**Settings of your own** are declared, not coded. Override `settings()` on the provider, and the framework builds a page named after your diagram under ADP, with a check box, a spinner or a list per setting. Values are stored for the user under your editor type id, so they survive your diagram being turned off or uninstalled. Read them where you need them:

```java
public static final ToolSetting DIRECTION = ToolSetting.choice("direction", "Layout direction", "right", "left", "right", "both");

@Override
public List<ToolSetting> settings() {
    return List.of(DIRECTION);
}

// anywhere in the diagram
String direction = AdpSettings.getInstance().choice(getEditorTypeId(), DIRECTION);
```

A key starts with a letter and uses letters, digits, `_`, `.` and `-`; a label is not empty; a number's default lies in its range; a choice's default is one of its choices. A declaration that breaks a rule is listed as a problem of your diagram, which then opens no files and gets no page. To repaint when the user applies the page, subscribe to `AdpSettingsListener.TOPIC` on the application message bus for the diagram's lifetime; never change the document in response.

**Origin** is where the page says your diagram comes from: "Built into ADP", or "From plug-in <name>" when another plug-in registers it. `origin()` works this out from the plug-in that registered the provider; a diagram interpreted from a bundled DISL specification returns `ToolOrigin.BundledSpecification` with the specification's name, DISL version and the etalii.adp revision it was copied from.

## Checklist

- The definition test passes: no `DefinitionException`.
- Every example file reads, and opening and saving it without edits is byte-identical.
- Each edit kind changes only its own ranges.
- The diagram test opens an example, edits it, and undoes back to the original bytes.
- The provider claims your files and no others.
- On Settings > Tools > ADP your diagram is listed as loaded, and any option your definition fixes names it under "Not followed by".
