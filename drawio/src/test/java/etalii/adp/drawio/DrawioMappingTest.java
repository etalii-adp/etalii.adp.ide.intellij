package etalii.adp.drawio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import etalii.adp.core.FormatProblem;
import etalii.adp.core.TextChanges;
import etalii.adp.core.diagram.AddRequest;
import etalii.adp.core.diagram.ArrowHead;
import etalii.adp.core.diagram.BoundsChange;
import etalii.adp.core.diagram.Dash;
import etalii.adp.core.diagram.EndSide;
import etalii.adp.core.diagram.LineStyle;
import etalii.adp.core.diagram.model.Connection;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;
import etalii.adp.core.diagram.model.End;

/**
 * T102: the draw.io mapping on the vendored templates (research R20, data-model.md "draw.io
 * mapping"). No published template has end labels on its edges, so those, a second page and
 * {@code editable=0} are checked on {@link #HAND_WRITTEN}, a small file written for this test.
 */
class DrawioMappingTest {

    static final List<String> EXAMPLES = List.of("flowchart_1", "cross_functional_flowchart_1", "data_flow_1", "workflow_1", "activity_diagram_1",
            "uml_1");

    private static final String LANE_1 = "77e6c97f196da883-2";
    private static final String LANE_2 = "77e6c97f196da883-3";
    private static final String IN_LANE_1 = "77e6c97f196da883-8";

    static final String HAND_WRITTEN = """
            <mxfile host="test">
              <diagram id="p1" name="Page-1">
                <mxGraphModel grid="1">
                  <root>
                    <mxCell id="0"/>
                    <mxCell id="1" parent="0"/>
                    <mxCell id="a" value="A" style="rounded=0;whiteSpace=wrap;html=1;" parent="1" vertex="1">
                      <mxGeometry x="40" y="40" width="120" height="60" as="geometry"/>
                    </mxCell>
                    <mxCell id="b" value="B" style="ellipse;editable=0;" parent="1" vertex="1">
                      <mxGeometry x="240" y="40" width="80" height="80" as="geometry"/>
                    </mxCell>
                    <mxCell id="e" value="uses" style="endArrow=block;dashed=1;curved=1;" parent="1" source="a" target="b" edge="1">
                      <mxGeometry relative="1" as="geometry"/>
                    </mxCell>
                    <mxCell id="e-s" value="1" style="edgeLabel;html=1;" parent="e" vertex="1" connectable="0">
                      <mxGeometry x="-1" relative="1" as="geometry">
                        <mxPoint as="offset"/>
                      </mxGeometry>
                    </mxCell>
                    <mxCell id="e-t" value="*" style="edgeLabel;html=1;" parent="e" vertex="1" connectable="0">
                      <mxGeometry x="1" relative="1" as="geometry"/>
                    </mxCell>
                  </root>
                </mxGraphModel>
              </diagram>
              <diagram id="p2" name="Page-2">
                <mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/><mxCell id="adp-1" value="z" parent="1" vertex="1"><mxGeometry width="10" height="10" as="geometry"/></mxCell></root></mxGraphModel>
              </diagram>
            </mxfile>
            """;

    private final DrawioMapping mapping = new DrawioMapping();

    static Path example(String name) {
        return Path.of(System.getProperty("adp.testdata", "testdata")).resolve("examples").resolve(name + ".drawio").toAbsolutePath();
    }

    static String read(String name) {
        try {
            return Files.readString(example(name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The text without one cell's lines. */
    static String withoutCell(String text, String id) {
        Matcher cell = Pattern.compile("(?ms)^[ \\t]*<mxCell id=\"" + Pattern.quote(id) + "\"[^>]*?(/>|>.*?</mxCell>)\\r?\\n").matcher(text);
        assertTrue(cell.find(), id);
        return text.substring(0, cell.start()) + text.substring(cell.end());
    }

    @ParameterizedTest
    @ValueSource(strings = { "flowchart_1", "cross_functional_flowchart_1", "data_flow_1", "workflow_1", "activity_diagram_1", "uml_1" })
    void everyExampleReadsAndRoundTripsByteForByte(String name) throws FormatProblem {
        String text = read(name);
        Diagram diagram = mapping.read(text);
        assertFalse(diagram.elements().isEmpty(), name);
        assertFalse(diagram.connections().isEmpty(), name + " has connections");

        for (Element element : diagram.elements().values()) {
            assertNotNull(element.bounds(), name + " " + element.key());
            if (DrawioDefinition.DEFINITION.elementType(element.type()) != null) {
                assertTrue(mapping.setBounds(text, diagram, List.of(new BoundsChange(element.key(), element.bounds(), element.sector(), false)))
                        .isEmpty(), name + ": " + element.key() + " did not move");
                assertTrue(mapping.setProperty(text, diagram, Set.of(element.key()), "label", element.property("label")).isEmpty());
            }
        }
        for (Connection connection : diagram.connections().values()) {
            assertTrue(mapping.setProperty(text, diagram, Set.of(connection.key()), "edgeStyle", connection.property("edgeStyle")).isEmpty(),
                    name + ": " + connection.key());
        }

        String added = mapping.add(text, diagram, new AddRequest("rectangle", new Rectangle2D.Double(5, 5, 10, 10), null, null, Map.of()))
                .applyTo(text);
        Diagram withAdded = mapping.read(added);
        assertEquals(diagram.elements().size() + 1, withAdded.elements().size(), name);
        Set<Object> fresh = new HashSet<>(withAdded.elements().keySet());
        fresh.removeAll(diagram.elements().keySet());
        assertEquals(text, mapping.remove(added, withAdded, fresh).applyTo(added), name);
    }

    @Test
    void elementTypesComeFromTheStyle() throws FormatProblem {
        Diagram flowchart = mapping.read(read("flowchart_1"));
        assertEquals("rounded", flowchart.element("90").type());
        assertEquals("rectangle", flowchart.element("56").type(), "a style without a shape is draw.io's default rectangle");
        Diagram activity = mapping.read(read("activity_diagram_1"));
        assertEquals("rhombus", activity.element("21").type());
        assertEquals("note", activity.element("31").type());
        assertEquals("startState", activity.element("5").type(), "shape= wins over a leading name, and an unknown shape is a placeholder");
        assertEquals("line", activity.element("25").type());
        assertEquals("rectangle", activity.element("15").type(), "an empty style is a rectangle");
        assertEquals("trapezoid", mapping.read(read("workflow_1")).element("60e70716793133e9-2").type());
        assertEquals("umlFrame", mapping.read(read("uml_1")).element("17acba5748e5396b-1").type());
        assertEquals("text", mapping.read(read("uml_1")).element("5d2195bd80daf111-16").type(), "a text cell outside a list");

        Element rhombus = activity.element("21");
        assertEquals("queue empty", rhombus.property("label"));
        assertEquals("#ffffc0", rhombus.property("fillColor"));
        assertEquals("#ff0000", rhombus.property("strokeColor"));
        assertEquals("21", rhombus.property("id"));
        assertEquals(new Color(0xffffc0), rhombus.style().fill());
        assertEquals(new Color(0xff0000), rhombus.style().border());
    }

    @Test
    void swimlanesAreSectorsAndTheirChildrenAreAbsolute() throws FormatProblem {
        Diagram activity = mapping.read(read("activity_diagram_1"));
        assertEquals(List.of("2", "3", "4"), List.copyOf(activity.sectors().keySet()));
        assertEquals("Thread 2", activity.sector("3").label());
        assertEquals(DrawioDefinition.SWIMLANE, activity.sector("3").declId());
        assertEquals(new Rectangle2D.Double(444.5, 128, 280, 570), activity.sector("3").bounds());
        assertEquals(new Rectangle2D.Double(444.5 + 150, 128 + 225, 80, 40), activity.element("21").bounds());
        assertEquals("3", activity.element("21").sector());

        Diagram pool = mapping.read(read("cross_functional_flowchart_1"));
        assertEquals(new Rectangle2D.Double(70, 60, 160, 730), pool.sector(LANE_1).bounds(), "a lane inside a pool is offset by the pool");
        assertEquals(new Rectangle2D.Double(70, 40, 960, 750), pool.sector("77e6c97f196da883-1").bounds(), "a pool is a sector too");
        assertEquals(List.of("77e6c97f196da883-1", LANE_1), List.copyOf(pool.sectors().keySet()).subList(0, 2), "the pool comes first, so a lane in it wins");
        assertEquals(new Rectangle2D.Double(90, 125, 100, 60), pool.element(IN_LANE_1).bounds());
        assertEquals(LANE_1, pool.element(IN_LANE_1).sector());
    }

    @Test
    void edgesAreConnectionsWithAnchorsWaypointsAndStyle() throws FormatProblem {
        Connection edge = mapping.read(read("flowchart_1")).connection("89");
        assertEquals(DrawioDefinition.EDGE, edge.type());
        assertEquals(new End("90", "x1y0.5"), edge.source());
        assertEquals(new End("92", "x0y0.5"), edge.target());
        assertEquals(List.of(new Point2D.Double(422, 980.5), new Point2D.Double(422, 1180.5)), edge.waypoints());
        assertEquals("orthogonal", edge.property("edgeStyle"));
        assertEquals("classic", edge.property("endArrow"));
        assertEquals("none", edge.property("startArrow"));
        assertEquals("3", edge.property("strokeWidth"));
        assertEquals(LineStyle.ORTHOGONAL, edge.style().line());
        assertEquals(ArrowHead.FILLED, edge.style().targetArrow());
        assertEquals(ArrowHead.NONE, edge.style().sourceArrow());
        assertEquals(3f, edge.style().thickness());

        Diagram activity = mapping.read(read("activity_diagram_1"));
        Connection yes = activity.connection("22");
        assertEquals("yes", yes.property("label"));
        assertEquals(new End("21", "x0.5y0"), yes.source());
        assertEquals(new End("25", null), yes.target(), "a placeholder has no anchors");
        assertEquals(List.of(new Point2D.Double(444.5 + 190, 128 + 180)), yes.waypoints(), "waypoints are relative to the edge's parent");
        assertEquals("straight", activity.connection("11").property("edgeStyle"));
        assertEquals(ArrowHead.OPEN, activity.connection("11").style().targetArrow());
        assertNull(activity.connection("35"), "an edge with a loose end is not shown, and kept");

        Connection curved = mapping.read(HAND_WRITTEN).connection("e");
        assertEquals("curved", curved.property("edgeStyle"));
        assertEquals("true", curved.property("dashed"));
        assertEquals(LineStyle.CURVED, curved.style().line());
        assertEquals(Dash.DASHED, curved.style().dash());
        assertEquals(new End("b", null), curved.target());
    }

    @Test
    void edgeLabelChildrenAreTheEndLabels() throws FormatProblem {
        Diagram diagram = mapping.read(HAND_WRITTEN);
        Connection edge = diagram.connection("e");
        assertEquals("uses", edge.property("label"));
        assertEquals("1", edge.property("sourceLabel"));
        assertEquals("*", edge.property("targetLabel"));
        assertEquals(Set.of("a", "b"), diagram.elements().keySet(), "labels and the second page are not elements");
    }

    @Test
    void aGroupIsNotShownAndItsMembersAreOrdinaryElementsThatStayInIt() throws FormatProblem {
        String text = read("flowchart_1");
        Diagram diagram = mapping.read(text);
        assertNull(diagram.element("140"), "a group is an invisible container");
        Element milestone = diagram.element("141");
        assertEquals("rectangle", milestone.type(), "a member is typed by its own style");
        assertEquals("Milestone 1", milestone.property("label"));
        assertEquals(new Rectangle2D.Double(244, 890.5, 646.6656362699642, 40), milestone.bounds(), "relative to the group");
        String moved = mapping.setBounds(text, diagram, List.of(new BoundsChange("141", new Rectangle2D.Double(254, 890.5, 646.6656362699642, 40),
                milestone.sector(), false))).applyTo(text);
        assertEquals(text.replace("<mxGeometry width=\"646.6656362699642\" height=\"40\" as=\"geometry\"/>",
                "<mxGeometry width=\"646.6656362699642\" height=\"40\" as=\"geometry\" x=\"10\"/>"),
                moved, "only its relative x is written; its parent stays the group");
    }

    @Test
    void placeholdersSurviveEditsAroundThem() throws FormatProblem {
        String text = read("activity_diagram_1");
        Diagram diagram = mapping.read(text);
        assertNull(DrawioDefinition.DEFINITION.elementType(diagram.element("25").type()), "shape=line is a placeholder");
        String line = text.substring(text.indexOf("<mxCell id=\"25\""), text.indexOf("</mxCell>", text.indexOf("<mxCell id=\"25\"")));
        String edited = mapping.setProperty(text, diagram, Set.of("21"), "fillColor", "#FF0000").applyTo(text);
        edited = mapping.add(edited, mapping.read(edited), new AddRequest("ellipse", new Rectangle2D.Double(0, 0, 0, 0), null, null, Map.of())).applyTo(edited);
        assertTrue(edited.contains(line), "the placeholder's lines are untouched");
    }

    @Test
    void listsAreElementsWithRowsAsParts() throws FormatProblem {
        String text = read("data_flow_1");
        Diagram diagram = mapping.read(text);
        String list = "21ea969265ad0168-14";
        String row = "21ea969265ad0168-17";
        assertEquals(DrawioDefinition.LIST, diagram.element(list).type());
        assertEquals("Function", diagram.element(list).property("label"));
        assertEquals(new Rectangle2D.Double(160, 266, 160, 110), diagram.element(list).bounds());
        assertEquals(DrawioDefinition.ROW, diagram.element(row).type());
        assertEquals("Row 3", diagram.element(row).property("label"));
        assertEquals(list, diagram.element(row).parent());
        assertEquals(new Rectangle2D.Double(160, 266 + 78, 160, 26), diagram.element(row).bounds());
        assertTrue(diagram.sectors().isEmpty(), "lists are not sectors");
        var rowType = DrawioDefinition.DEFINITION.elementType(DrawioDefinition.ROW);
        assertFalse(rowType.movable());
        assertTrue(rowType.selectable());
        assertEquals(List.of("x0y0.5", "x1y0.5"), rowType.anchors().stream().map(a -> a.id()).toList(), "a row connects on its left and right only");

        Connection toRow = diagram.connection("21ea969265ad0168-33");
        assertEquals(new End("21ea969265ad0168-18", "x1y0.25"), toRow.source());
        assertEquals(new End(row, "x1y0.5"), toRow.target());

        String moved = mapping.setBounds(text, diagram, List.of(new BoundsChange(list, new Rectangle2D.Double(200, 300, 160, 110), null, false))).applyTo(text);
        assertEquals(text.replace("<mxGeometry x=\"160\" y=\"266\" width=\"160\"", "<mxGeometry x=\"200\" y=\"300\" width=\"160\""), moved,
                "only the list's geometry is written");
        assertEquals(new Rectangle2D.Double(200, 300 + 78, 160, 26), mapping.read(moved).element(row).bounds(), "its rows move with it");

        Diagram uml = mapping.read(read("uml_1"));
        assertEquals(DrawioDefinition.LIST, uml.element("17acba5748e5396b-2").type());
        assertFalse(uml.connections().isEmpty(), "edges between UML classes are shown");
    }

    @Test
    void deletingARowOrAListTakesWhatHangsOnIt() throws FormatProblem {
        String text = read("data_flow_1");
        Diagram diagram = mapping.read(text);
        String row = "21ea969265ad0168-17";
        Set<Object> rowAndEdges = new java.util.HashSet<>(List.of(row));
        diagram.connectionsOf(row).forEach(c -> rowAndEdges.add(c.key()));
        Diagram withoutRow = mapping.read(mapping.remove(text, diagram, rowAndEdges).applyTo(text));
        assertNull(withoutRow.element(row));
        assertEquals(List.of("21ea969265ad0168-15", "21ea969265ad0168-16"), withoutRow.elements().values().stream()
                .filter(e -> "21ea969265ad0168-14".equals(e.parent())).map(e -> e.key()).toList(), "the list keeps its other rows");

        Diagram withoutList = mapping.read(mapping.remove(text, diagram, Set.of("21ea969265ad0168-14")).applyTo(text));
        assertTrue(withoutList.elements().values().stream().noneMatch(e -> "21ea969265ad0168-14".equals(e.parent())), "its rows went with it");
        assertNull(withoutList.connection("21ea969265ad0168-33"), "and the edges at its rows");
        assertTrue(withoutList.connections().values().stream().allMatch(c -> withoutList.element(c.source().elementKey()) != null
                && withoutList.element(c.target().elementKey()) != null));
    }

    @Test
    void thePaintingOrderIsTheFileOrderAndHtmlLabelsAreMarked() throws FormatProblem {
        Diagram flowchart = mapping.read(read("flowchart_1"));
        assertTrue(flowchart.order().indexOf("89") > flowchart.order().indexOf("68"), "an edge written after a grid rectangle is painted above it");
        assertEquals(flowchart.elements().size() + flowchart.connections().size(), flowchart.order().size());
        assertTrue(flowchart.element("90").style().html(), "html=1");
        assertTrue(flowchart.connection("89").style().html());
        Diagram activity = mapping.read(read("activity_diagram_1"));
        assertFalse(activity.element("21").style().html(), "no html=1");
        assertEquals("Frequently<div>performed</div><div>process?</div>", mapping.read(read("workflow_1")).element("60e70716793133e9-5").property("label"),
                "the value stays the raw markup");
    }

    @Test
    void aCompressedPageIsAProblemThatNamesTheSetting() {
        String text = read("compressed");
        FormatProblem problem = assertThrows(FormatProblem.class, () -> mapping.read(text));
        assertTrue(problem.getMessage().contains("\"Compressed\""), problem.getMessage());
        assertEquals(text.indexOf('>', text.indexOf("<diagram")) + 1, problem.getOffset());
        assertThrows(FormatProblem.class, () -> mapping.read("<svg/>"));
        assertThrows(FormatProblem.class, () -> mapping.read("<mxfile><diagram>"));
    }

    @Test
    void aBareGraphModelReads() throws FormatProblem {
        Diagram diagram = mapping.read("<mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>"
                + "<mxCell id=\"v\" vertex=\"1\" parent=\"1\"><mxGeometry x=\"1\" y=\"2\" width=\"3\" height=\"4\" as=\"geometry\"/></mxCell></root></mxGraphModel>");
        assertEquals(new Rectangle2D.Double(1, 2, 3, 4), diagram.element("v").bounds());
    }

    @Test
    void addWritesOneCellAtTheEndWithAFreshId() throws FormatProblem {
        String text = read("activity_diagram_1");
        Diagram diagram = mapping.read(text);
        String added = mapping.add(text, diagram, new AddRequest("rounded", new Rectangle2D.Double(300, 40, 10, 10), null, null, Map.of()))
                .applyTo(text);
        assertEquals(text.replace("</mxCell>\n      </root>", """
                </mxCell>
                        <mxCell id="adp-1" value="" style="rounded=1;whiteSpace=wrap;html=1;" parent="1" vertex="1">
                          <mxGeometry x="300" y="40" width="120" height="60" as="geometry"/>
                        </mxCell>
                      </root>"""), added);

        String inLane = mapping.add(added, mapping.read(added),
                new AddRequest("text", new Rectangle2D.Double(500, 200, 10, 10), null, "3", Map.of("label", "Text"))).applyTo(added);
        assertTrue(inLane.contains("<mxCell id=\"adp-2\" value=\"Text\" style=\"text;whiteSpace=wrap;html=1;\" parent=\"3\" vertex=\"1\">\n"
                + "          <mxGeometry x=\"55.5\" y=\"72\" width=\"60\" height=\"30\" as=\"geometry\"/>"), inLane);
        assertEquals(new Rectangle2D.Double(500, 200, 60, 30), mapping.read(inLane).element("adp-2").bounds());

        String second = mapping.add(HAND_WRITTEN, mapping.read(HAND_WRITTEN),
                new AddRequest("ellipse", new Rectangle2D.Double(0, 0, 0, 0), null, null, Map.of())).applyTo(HAND_WRITTEN);
        assertTrue(second.contains("<mxCell id=\"adp-2\""), "ids are unique across every page of the file");
    }

    @Test
    void removeTakesTheCellsTheirChildrenAndTheirEdges() throws FormatProblem {
        String text = read("activity_diagram_1");
        String after = mapping.remove(text, mapping.read(text), Set.of("21", "22", "23", "24")).applyTo(text);
        assertEquals(withoutCell(withoutCell(withoutCell(withoutCell(text, "21"), "22"), "23"), "24"), after);

        String hand = mapping.remove(HAND_WRITTEN, mapping.read(HAND_WRITTEN), Set.of("a")).applyTo(HAND_WRITTEN);
        assertEquals(withoutCell(withoutCell(withoutCell(withoutCell(HAND_WRITTEN, "a"), "e"), "e-s"), "e-t"), hand);
    }

    @Test
    void setBoundsWritesRelativeCoordinatesAndRewritesTheParentBetweenLanes() throws FormatProblem {
        String text = read("cross_functional_flowchart_1");
        Diagram diagram = mapping.read(text);
        String line = "parent=\"" + LANE_1 + "\" vertex=\"1\">\n          <mxGeometry x=\"20\" y=\"65\" width=\"100\" height=\"60\" as=\"geometry\"/>";
        assertTrue(text.contains(line));

        String within = mapping.setBounds(text, diagram, List.of(new BoundsChange(IN_LANE_1, new Rectangle2D.Double(100, 135, 150, 60), LANE_1, false)))
                .applyTo(text);
        assertEquals(text.replace(line, line.replace("x=\"20\" y=\"65\" width=\"100\"", "x=\"30\" y=\"75\" width=\"150\"")), within);

        String across = mapping.setBounds(text, diagram, List.of(new BoundsChange(IN_LANE_1, new Rectangle2D.Double(260, 135, 100, 60), LANE_2, true)))
                .applyTo(text);
        assertEquals(text.replace(line, line.replace(LANE_1, LANE_2).replace("x=\"20\" y=\"65\"", "x=\"30\" y=\"75\"")), across);
        assertEquals(LANE_2, mapping.read(across).element(IN_LANE_1).sector());
        assertEquals(new Rectangle2D.Double(260, 135, 100, 60), mapping.read(across).element(IN_LANE_1).bounds());

        String out = mapping.setBounds(text, diagram, List.of(new BoundsChange(IN_LANE_1, new Rectangle2D.Double(1100, 50, 100, 60), null, true)))
                .applyTo(text);
        assertEquals(text.replace(line, line.replace(LANE_1, "1").replace("x=\"20\" y=\"65\"", "x=\"1100\" y=\"50\"")), out);
        assertNull(mapping.read(out).element(IN_LANE_1).sector());
    }

    @Test
    void connectAndReconnectWriteTheEndsAndTheirAnchorsInTheStyle() throws FormatProblem {
        Diagram diagram = mapping.read(HAND_WRITTEN);
        String connected = mapping.connect(HAND_WRITTEN, diagram, DrawioDefinition.EDGE, new End("a", "x1y0.5"), new End("b", null)).applyTo(HAND_WRITTEN);
        assertEquals(HAND_WRITTEN.replace("""
                          <mxGeometry x="1" relative="1" as="geometry"/>
                        </mxCell>
                """, """
                          <mxGeometry x="1" relative="1" as="geometry"/>
                        </mxCell>
                        <mxCell id="adp-2" value="" style="edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;exitX=1;exitY=0.5;" parent="1" source="a" target="b" edge="1">
                          <mxGeometry relative="1" as="geometry"/>
                        </mxCell>
                """), connected);
        assertEquals(new End("a", "x1y0.5"), mapping.read(connected).connection("adp-2").source());

        String target = mapping.reconnect(HAND_WRITTEN, diagram, "e", EndSide.TARGET, new End("a", "x0y0.5")).applyTo(HAND_WRITTEN);
        assertEquals(HAND_WRITTEN.replace("style=\"endArrow=block;dashed=1;curved=1;\" parent=\"1\" source=\"a\" target=\"b\"",
                "style=\"endArrow=block;dashed=1;curved=1;entryX=0;entryY=0.5;\" parent=\"1\" source=\"a\" target=\"a\""), target);

        String text = read("activity_diagram_1");
        String source = mapping.reconnect(text, mapping.read(text), "22", EndSide.SOURCE, new End("21", null)).applyTo(text);
        assertEquals(text.replace("strokeColor=#FF0000;exitX=0.5;exitY=0;endFill=1", "strokeColor=#FF0000;endFill=1"), source);
    }

    @Test
    void setPropertyEditsOneKeyOfTheStyleOrTheValue() throws FormatProblem {
        String flowchart = read("flowchart_1");
        Diagram chart = mapping.read(flowchart);
        assertEquals(flowchart.replace("style=\"rounded=1;fillColor=#23445D;strokeColor=none;strokeWidth=2;fontFamily=Helvetica;html=1;gradientColor=none;\" parent=\"1\" vertex=\"1\">\n          <mxGeometry x=\"270.3945578231293\"",
                "style=\"rounded=1;fillColor=#FF0000;strokeColor=none;strokeWidth=2;fontFamily=Helvetica;html=1;gradientColor=none;\" parent=\"1\" vertex=\"1\">\n          <mxGeometry x=\"270.3945578231293\""),
                mapping.setProperty(flowchart, chart, Set.of("90"), "fillColor", "#FF0000").applyTo(flowchart));
        assertTrue(mapping.setProperty(flowchart, chart, Set.of("89"), "edgeStyle", "orthogonal").isEmpty(), "already orthogonal");

        Diagram hand = mapping.read(HAND_WRITTEN);
        assertEquals(HAND_WRITTEN.replace("rounded=0;whiteSpace=wrap;html=1;\"", "rounded=0;whiteSpace=wrap;html=1;dashed=1;\""),
                mapping.setProperty(HAND_WRITTEN, hand, Set.of("a"), "dashed", "true").applyTo(HAND_WRITTEN));
        assertEquals(HAND_WRITTEN.replace("endArrow=block;dashed=1;curved=1;", "endArrow=block;dashed=1;edgeStyle=orthogonalEdgeStyle;"),
                mapping.setProperty(HAND_WRITTEN, hand, Set.of("e"), "edgeStyle", "orthogonal").applyTo(HAND_WRITTEN));
        assertEquals(HAND_WRITTEN.replace("endArrow=block;dashed=1;curved=1;", "endArrow=block;curved=1;"),
                mapping.setProperty(HAND_WRITTEN, hand, Set.of("e"), "dashed", "false").applyTo(HAND_WRITTEN));
        assertEquals(HAND_WRITTEN.replace("value=\"uses\"", "value=\"calls &lt;it>\""),
                mapping.setProperty(HAND_WRITTEN, hand, Set.of("e"), "label", "calls <it>").applyTo(HAND_WRITTEN));
        assertEquals(HAND_WRITTEN.replace("value=\"A\"", "value=\"X\"").replace("value=\"uses\"", "value=\"X\""),
                mapping.setProperty(HAND_WRITTEN, hand, Set.of("a", "e"), "label", "X").applyTo(HAND_WRITTEN));
        assertTrue(mapping.setProperty(HAND_WRITTEN, hand, Set.of("a"), "id", "zz").isEmpty(), "the id is read-only");

        String activity = read("activity_diagram_1");
        assertEquals(activity.replace("entryPerimeter=0\" parent=\"3\" source=\"21\"", "entryPerimeter=0;strokeWidth=2\" parent=\"3\" source=\"21\""),
                mapping.setProperty(activity, mapping.read(activity), Set.of("22"), "strokeWidth", "2").applyTo(activity));
    }

    @Test
    void editableZeroMakesAnItemsValuesReadOnly() throws FormatProblem {
        Diagram hand = mapping.read(HAND_WRITTEN);
        assertFalse(DrawioDefinition.DEFINITION.rules().canSetProperty(hand, "b", "label").allowed());
        assertTrue(DrawioDefinition.DEFINITION.rules().canSetProperty(hand, "a", "label").allowed());
        assertTrue(DrawioDefinition.DEFINITION.rules().canSetProperty(hand, "e", "label").allowed());
    }

    @Test
    void everyChangeIsRangeExact() throws FormatProblem {
        String text = read("workflow_1");
        Diagram diagram = mapping.read(text);
        Object first = diagram.elements().keySet().iterator().next();
        TextChanges changes = mapping.setProperty(text, diagram, Set.of(first), "strokeColor", "#123456");
        assertEquals(1, changes.changes().size());
        String before = text.substring(0, changes.changes().get(0).offset());
        assertTrue(before.endsWith("style=\""), "only the style's value is replaced");
    }
}
