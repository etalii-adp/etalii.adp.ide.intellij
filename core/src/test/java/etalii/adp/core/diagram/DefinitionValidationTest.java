package etalii.adp.core.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import etalii.adp.core.diagram.sample.SampleDefinition;

class DefinitionValidationTest {

    /** A valid one-element, one-connection definition, changed by {@code change}. */
    private static DiagramDefinition.Builder minimal(Consumer<DiagramDefinition.Builder> change) {
        DiagramDefinition.Builder builder = DiagramDefinition.builder("mini")
                .element("box", e -> e.property("title", p -> p.label("Title"))
                        .text("title", t -> t.property("title"))
                        .anchor("in", a -> a.at(0, 0.5).accepts("line", Direction.IN))
                        .anchor("out", a -> a.at(1, 0.5).accepts("line", Direction.OUT)))
                .connection("line", c -> c.label("Line"));
        change.accept(builder);
        return builder;
    }

    private static List<String> problems(Consumer<DiagramDefinition.Builder> change) {
        return assertThrows(DefinitionException.class, () -> minimal(change).build()).problems();
    }

    @Test
    void aValidDefinitionBuilds() {
        DiagramDefinition definition = minimal(b -> {
        }).build();
        assertEquals("mini", definition.id());
        assertEquals(List.of("box"), List.copyOf(definition.elementTypes().keySet()));
        assertEquals("Line", definition.connectionType("line").label());
        SampleDefinition.DEFINITION.elementType("task");
    }

    @Test
    void defaultsAreAsDocumented() {
        DiagramDefinition definition = minimal(b -> {
        }).build();
        assertEquals(List.of("box", "line"), definition.toolbox());
        assertTrue(definition.view().zoom());
        assertTrue(definition.view().pan());
        assertEquals(10, definition.view().grid());
        assertNull(definition.layout());
        assertEquals(Map.of(), definition.sectors());
        Verdict verdict = definition.rules().canRemove(null, java.util.Set.of());
        assertTrue(verdict.allowed());

        ElementType box = definition.elementType("box");
        assertEquals("box", box.label());
        assertEquals(Outline.RECTANGLE, box.outline());
        assertEquals(Tone.NEUTRAL, box.tone());
        assertEquals(Sizing.auto(200), box.sizing());
        assertEquals(Resize.NONE, box.resize());
        assertTrue(box.selectable());
        assertTrue(box.movable());
        assertEquals(false, box.droppableOnto());
        PropertyDecl title = box.property("title");
        assertEquals("General", title.category());
        assertEquals(EditorKind.TEXT, title.editor());
        assertEquals(false, title.readOnly());
        TextSlot slot = box.text("title");
        assertEquals(SlotPosition.CENTER, slot.position());
        assertEquals(false, slot.editable());

        ConnectionType line = definition.connectionType("line");
        assertEquals(LineStyle.STRAIGHT, line.line());
        assertEquals(Dash.SOLID, line.dash());
        assertEquals(1f, line.thickness());
        assertEquals(ArrowHead.NONE, line.sourceArrow());
        assertEquals(ArrowHead.NONE, line.targetArrow());
        assertTrue(line.userConnectable());
        assertEquals(false, line.routed());
    }

    @Test
    void eachRuleNamesItsDeclaration() {
        assertEquals(List.of("definition 'mini': declares no element types"),
                assertThrows(DefinitionException.class, () -> DiagramDefinition.builder("mini").build()).problems());
        assertEquals(List.of("element 'box': is declared twice"), problems(b -> b.element("box", e -> {
        })));
        assertEquals(List.of("element '1box': the id must start with a letter and use letters, digits, '_', '.' or '-'"),
                problems(b -> b.element("1box", e -> {
                })));
        assertEquals(List.of("connection 'box': has the id of an element type"), problems(b -> b.connection("box", c -> c.userConnectable(false))));
        assertEquals(List.of("toolbox: names undeclared type 'nope'"), problems(b -> b.toolbox("box", "nope")));
        assertEquals(List.of("element 'x' > text 'a': shows undeclared property 'missing'"),
                problems(b -> b.element("x", e -> e.text("a", t -> t.property("missing")))));
        assertEquals(List.of("element 'x' > text 'a': is editable but property 'p' is read-only"),
                problems(b -> b.element("x", e -> e.property("p", p -> p.readOnly(true)).text("a", t -> t.property("p").editable(true)))));
        assertEquals(List.of("element 'x' > text 'a': is declared twice"),
                problems(b -> b.element("x", e -> e.property("p", p -> {
                }).text("a", t -> t.property("p")).text("a", t -> t.property("p")))));
        assertEquals(List.of("element 'x' > property 'p': is declared twice"),
                problems(b -> b.element("x", e -> e.property("p", p -> {
                }).property("p", p -> {
                }))));
        assertEquals(List.of("element 'x' > anchor 'a': names undeclared connection type 'flw'"),
                problems(b -> b.element("x", e -> e.anchor("a", a -> a.accepts("flw", Direction.BOTH)))));
        assertEquals(List.of("element 'x' > anchor 'a': the position must lie within 0 and 1"),
                problems(b -> b.element("x", e -> e.anchor("a", a -> a.at(1.5, 0)))));
        assertEquals(List.of("element 'x' > anchor 'a': is declared twice"),
                problems(b -> b.element("x", e -> e.anchor("a", a -> {
                }).anchor("a", a -> {
                }))));
        assertEquals(List.of("element 'x': is movable but not selectable"), problems(b -> b.element("x", e -> e.selectable(false))));
        assertEquals(List.of("element 'x': is resizable but has a fixed size"),
                problems(b -> b.element("x", e -> e.sizing(Sizing.fixed(10, 10)).resize(Resize.BOTH))));
        assertEquals(List.of("element 'box': is movable but the diagram has a layout"),
                problems(b -> b.layout((diagram, view, measure) -> Map.of())));
        assertEquals(List.of("connection 'c': the thickness must lie within 0.5 and 8"),
                problems(b -> b.connection("c", c -> c.userConnectable(false).thickness(9))));
        assertEquals(List.of("connection 'c': is routed but its line is not orthogonal"),
                problems(b -> b.connection("c", c -> c.userConnectable(false).routed(true))));
        assertEquals(List.of("connection 'c' > label MIDDLE: shows undeclared property 'm'"),
                problems(b -> b.connection("c", c -> c.userConnectable(false).label(LabelSlot.MIDDLE, "m", false))));
        assertEquals(List.of("connection 'c' > label MIDDLE: is editable but property 'm' is read-only"),
                problems(b -> b.connection("c", c -> c.userConnectable(false).property("m", p -> p.readOnly(true)).label(LabelSlot.MIDDLE, "m", true))));
        assertEquals(List.of("connection 'c': no anchor accepts it as a source", "connection 'c': no anchor accepts it as a target"),
                problems(b -> b.connection("c", c -> {
                })));
        assertEquals(List.of("sector 'lane': is declared twice"), problems(b -> b.sector("lane", s -> {
        }).sector("lane", s -> {
        })));
        assertEquals(List.of("element 'x' > property 'n': the minimum is larger than the maximum"),
                problems(b -> b.element("x", e -> e.property("n", p -> p.editor(EditorKind.number(true, 5, 1))))));
        assertEquals(List.of("element 'x' > property 'c': a choice needs at least one value"),
                problems(b -> b.element("x", e -> e.property("c", p -> p.editor(EditorKind.choice())))));
    }

    @Test
    void severalProblemsAreReportedTogether() {
        DefinitionException problem = assertThrows(DefinitionException.class, () -> minimal(b -> b
                .element("x", e -> e.selectable(false).anchor("a", a -> a.accepts("flw", Direction.IN)))
                .toolbox("nope")).build());
        assertEquals(List.of("toolbox: names undeclared type 'nope'", "element 'x': is movable but not selectable",
                "element 'x' > anchor 'a': names undeclared connection type 'flw'"), problem.problems());
        assertTrue(problem.getMessage().contains("element 'x': is movable but not selectable"));
    }
}
