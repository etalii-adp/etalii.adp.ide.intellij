package etalii.adp.core.diagram.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import etalii.adp.core.FormatProblem;
import etalii.adp.core.TextChanges;
import etalii.adp.core.diagram.AddRequest;
import etalii.adp.core.diagram.BoundsChange;
import etalii.adp.core.diagram.EndSide;
import etalii.adp.core.diagram.model.Connection;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;
import etalii.adp.core.diagram.model.End;

class SampleMappingTest {

    private final SampleMapping mapping = new SampleMapping();

    private static String twoTasks() {
        return SampleFiles.read("two-tasks.adpsample");
    }

    @ParameterizedTest
    @ValueSource(strings = { "two-tasks", "flow-dashed-curved", "invisible-anchors", "lanes", "legend-view-space", "unknown-type", "crlf" })
    void readsEverySampleFile(String name) throws FormatProblem {
        Diagram diagram = mapping.read(SampleFiles.read(name + ".adpsample"));
        assertTrue(diagram.elements().size() >= 2, name);
    }

    @Test
    void aBrokenFileIsAProblem() {
        String text = SampleFiles.read("broken.adpsample");
        FormatProblem problem = assertThrows(FormatProblem.class, () -> mapping.read(text));
        assertEquals(text.indexOf("</sample>"), problem.getOffset());
        assertThrows(FormatProblem.class, () -> mapping.read("<other/>"));
    }

    @Test
    void readsElementsConnectionsAndSectors() throws FormatProblem {
        Diagram diagram = mapping.read(twoTasks());
        Element a = diagram.element("a");
        assertEquals("task", a.type());
        assertEquals(new Rectangle2D.Double(40, 40, 120, 60), a.bounds());
        assertEquals("Place order", a.properties().get("title"));
        assertEquals("Ann", a.properties().get("owner"));
        assertEquals("high", a.properties().get("priority"));
        assertEquals("a", a.properties().get("id"));
        assertEquals(new Rectangle2D.Double(240, 40, 0, 0), diagram.element("b").bounds());
        Connection f1 = diagram.connection("f1");
        assertEquals(new End("a", "out"), f1.source());
        assertEquals(new End("b", "in"), f1.target());
        assertEquals("submit", f1.properties().get("label"));

        Diagram lanes = mapping.read(SampleFiles.read("lanes.adpsample"));
        assertEquals(List.of("l1", "l2"), List.copyOf(lanes.sectors().keySet()));
        assertEquals("Shop", lanes.sectors().get("l2").label());
        assertEquals("lane", lanes.sectors().get("l2").declId());
        assertEquals(200, lanes.sectors().get("l2").bounds().getY());
        assertEquals("l1", lanes.element("a").sector());

        Diagram legend = mapping.read(SampleFiles.read("legend-view-space.adpsample"));
        assertEquals("legend", legend.sectors().get("g").declId());
    }

    @Test
    void unknownTypesAreReadWithTheirOwnIds() throws FormatProblem {
        Diagram diagram = mapping.read(SampleFiles.read("unknown-type.adpsample"));
        assertEquals("gizmo", diagram.element("z").type());
        assertEquals("wire", diagram.connection("w1").type());
        assertNull(diagram.connection("w1").source().anchorId());
    }

    @Test
    void addInsertsOneElementAtTheEnd() throws FormatProblem {
        String text = twoTasks();
        Diagram diagram = mapping.read(text);
        TextChanges changes = mapping.add(text, diagram,
                new AddRequest("task", new Rectangle2D.Double(400, 40, 0, 0), null, null, Map.of("title", "Task")));
        assertEquals(text.replace("submit</link>\n", "submit</link>\n  <box id=\"task1\" type=\"task\" x=\"400\" y=\"40\">Task</box>\n"),
                changes.applyTo(text));

        TextChanges sized = mapping.add(text, diagram,
                new AddRequest("decision", new Rectangle2D.Double(400.5, 40, 100, 60), null, "l1", Map.of()));
        assertTrue(sized.applyTo(text).contains("<box id=\"decision1\" type=\"decision\" x=\"400.5\" y=\"40\" w=\"100\" h=\"60\" lane=\"l1\"/>"));
    }

    @Test
    void removeTakesOnlyTheRemovedLines() throws FormatProblem {
        String text = twoTasks();
        String after = mapping.remove(text, mapping.read(text), Set.of("a", "f1")).applyTo(text);
        assertEquals("""
                <?xml version="1.0" encoding="UTF-8"?>
                <sample>
                  <box id="b" type="task" x="240" y="40">Ship the goods to the customer quickly</box>
                </sample>
                """, after);
    }

    @Test
    void setBoundsChangesOnlyTheCoordinatesAndTheLane() throws FormatProblem {
        String text = twoTasks();
        Diagram diagram = mapping.read(text);
        String after = mapping.setBounds(text, diagram, List.of(new BoundsChange("a", new Rectangle2D.Double(60, 80, 150, 60), "l1", true),
                new BoundsChange("b", new Rectangle2D.Double(300, 40, 0, 0), null, false))).applyTo(text);
        assertEquals(text.replace("x=\"40\" y=\"40\" w=\"120\" h=\"60\" owner=\"Ann\" priority=\"high\"",
                "x=\"60\" y=\"80\" w=\"150\" h=\"60\" owner=\"Ann\" priority=\"high\" lane=\"l1\"").replace("x=\"240\"", "x=\"300\""), after);

        String lanes = SampleFiles.read("lanes.adpsample");
        String out = mapping.setBounds(lanes, mapping.read(lanes),
                List.of(new BoundsChange("a", new Rectangle2D.Double(40, 500, 0, 0), null, true))).applyTo(lanes);
        assertTrue(out.contains("<box id=\"a\" type=\"task\" x=\"40\" y=\"500\">Place order</box>"), out);
    }

    @Test
    void connectAndReconnect() throws FormatProblem {
        String text = twoTasks();
        String connected = mapping.connect(text, mapping.read(text), "note", new End("a", "top"), new End("b", null)).applyTo(text);
        assertEquals(text.replace("submit</link>\n", "submit</link>\n  <link id=\"note1\" type=\"note\" from=\"a\" fromAnchor=\"top\" to=\"b\"/>\n"),
                connected);

        String reconnected = mapping.reconnect(text, mapping.read(text), "f1", EndSide.TARGET, new End("a", null)).applyTo(text);
        assertEquals(text.replace("to=\"b\" toAnchor=\"in\"", "to=\"a\""), reconnected);
        String source = mapping.reconnect(text, mapping.read(text), "f1", EndSide.SOURCE, new End("b", "out")).applyTo(text);
        assertEquals(text.replace("from=\"a\"", "from=\"b\""), source);
    }

    @Test
    void setPropertyChangesContentOrAnAttribute() throws FormatProblem {
        String text = twoTasks();
        Diagram diagram = mapping.read(text);
        assertEquals(text.replace(">Place order<", ">Place &lt;big&gt; order<"),
                mapping.setProperty(text, diagram, Set.of("a"), "title", "Place <big> order").applyTo(text));
        assertEquals(text.replace("owner=\"Ann\"", "owner=\"Bob\"").replace("x=\"240\" y=\"40\"", "x=\"240\" y=\"40\" owner=\"Bob\""),
                mapping.setProperty(text, diagram, Set.of("a", "b"), "owner", "Bob").applyTo(text));
        assertEquals(text.replace(" owner=\"Ann\"", ""), mapping.setProperty(text, diagram, Set.of("a"), "owner", "").applyTo(text));
        assertEquals(text.replace(">submit<", ">send<"), mapping.setProperty(text, diagram, Set.of("f1"), "label", "send").applyTo(text));
    }

    @Test
    void editsRoundTripToTheSameBytes() throws FormatProblem {
        for (String name : List.of("two-tasks.adpsample", "crlf.adpsample")) {
            String text = SampleFiles.read(name);
            Diagram diagram = mapping.read(text);
            String added = mapping.add(text, diagram, new AddRequest("task", new Rectangle2D.Double(1, 2, 0, 0), null, null, Map.of())).applyTo(text);
            Diagram withAdded = mapping.read(added);
            Set<Object> fresh = new HashSet<>(withAdded.elements().keySet());
            fresh.removeAll(diagram.elements().keySet());
            assertEquals(text, mapping.remove(added, withAdded, fresh).applyTo(added), name);

            String connected = mapping.connect(text, diagram, "flow", new End("b", "out"), new End("a", "in")).applyTo(text);
            Diagram withLink = mapping.read(connected);
            Set<Object> link = new HashSet<>(withLink.connections().keySet());
            link.removeAll(diagram.connections().keySet());
            assertEquals(text, mapping.remove(connected, withLink, link).applyTo(connected), name);
        }
        String crlf = SampleFiles.read("crlf.adpsample");
        String added = mapping.add(crlf, mapping.read(crlf), new AddRequest("task", new Rectangle2D.Double(1, 2, 0, 0), null, null, Map.of()))
                .applyTo(crlf);
        assertTrue(added.contains("</link>\r\n  <box id=\"task1\""), added);
        assertEquals(added.split("\n", -1).length - 1, added.split("\r\n", -1).length - 1);
    }

    @Test
    void unknownContentSurvivesEveryEditAroundIt() throws FormatProblem {
        String text = SampleFiles.read("unknown-type.adpsample");
        List<String> kept = List.of("<meta generator=\"by hand\"/>",
                "<box id=\"z\" type=\"gizmo\" x=\"240\" y=\"40\" w=\"80\" h=\"40\" colour=\"teal\">Unknown</box>",
                "<link id=\"w1\" type=\"wire\" from=\"z\" to=\"a\"/>");
        Diagram diagram = mapping.read(text);
        List<TextChanges> edits = List.of(
                mapping.add(text, diagram, new AddRequest("task", new Rectangle2D.Double(0, 0, 0, 0), null, null, Map.of())),
                mapping.remove(text, diagram, Set.of("f1")),
                mapping.setBounds(text, diagram, List.of(new BoundsChange("a", new Rectangle2D.Double(9, 9, 0, 0), null, false))),
                mapping.connect(text, diagram, "flow", new End("a", "out"), new End("a", "in")),
                mapping.reconnect(text, diagram, "f1", EndSide.TARGET, new End("a", "in")),
                mapping.setProperty(text, diagram, Set.of("a"), "title", "Renamed"));
        for (TextChanges edit : edits) {
            String after = edit.applyTo(text);
            assertNotEquals(text, after);
            for (String piece : kept) {
                assertTrue(after.contains(piece), piece + " lost by " + edit);
            }
        }
    }

    @Test
    void newIdsAreUnique() throws FormatProblem {
        String text = twoTasks().replace("<box id=\"b\"", "<box id=\"task1\"").replace("to=\"b\"", "to=\"task1\"");
        String added = mapping.add(text, mapping.read(text), new AddRequest("task", new Rectangle2D.Double(0, 0, 0, 0), null, null, Map.of()))
                .applyTo(text);
        assertTrue(added.contains("id=\"task2\""), added);
    }
}
