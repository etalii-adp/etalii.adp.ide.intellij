package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.example;
import static etalii.adp.freemind.MindMapAsserts.key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.draw2d.Connection;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Rectangle;
import org.junit.jupiter.api.Test;

import etalii.adp.freemind.model.ArrowLink;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.testing.DesignerDriver;

/** FR-013, FR-015, US1-AS2: where nodes and arrow links are drawn. */
class LayoutTest {

    /** A and B record their side; C and D get one automatically: C right (tie), D left (fewer). */
    static final String MAP = """
            <map version="1.0.1">
            <node ID="R" TEXT="Root">
            <node ID="A" POSITION="left" TEXT="A">
            <node ID="A1" TEXT="A1"/>
            </node>
            <node ID="B" POSITION="right" TEXT="B"/>
            <node ID="C" TEXT="C"/>
            <node ID="D" TEXT="D">
            <arrowlink DESTINATION="B" ENDARROW="Default" ID="Arrow_1" STARTARROW="None"/>
            <arrowlink DESTINATION="NOWHERE" ENDARROW="Default" ID="Arrow_2" STARTARROW="None"/>
            </node>
            </node>
            </map>
            """;

    @Test
    void theRootIsCentredWithBranchesOnTheirSides() {
        try (var d = DesignerDriver.openText("layout.mm", MAP, MindMapEditor.ID)) {
            Rectangle root = bounds(d, "R");
            for (String left : new String[] { "A", "D" }) {
                assertTrue(bounds(d, left).right() <= root.x, left + " is left of the root");
            }
            for (String right : new String[] { "B", "C" }) {
                assertTrue(bounds(d, right).x >= root.right(), right + " is right of the root");
            }
            assertTrue(bounds(d, "A1").right() <= bounds(d, "A").x, "a left branch grows further left");

            int rootCentre = root.getCenter().y;
            assertTrue(Math.abs((bounds(d, "B").y + bounds(d, "C").bottom()) / 2 - rootCentre) <= 1, "right side centred");
            assertTrue(Math.abs((bounds(d, "A").y + bounds(d, "D").bottom()) / 2 - rootCentre) <= 1, "left side centred");
            assertTrue(bounds(d, "B").bottom() <= bounds(d, "C").y, "siblings follow document order");
        }
    }

    @Test
    void horizontalGapIsHonoured() {
        String map = MAP.replace("POSITION=\"right\" TEXT=\"B\"", "HGAP=\"100\" POSITION=\"right\" TEXT=\"B\"");
        try (var d = DesignerDriver.openText("hgap.mm", map, MindMapEditor.ID)) {
            Rectangle root = bounds(d, "R");
            assertEquals(100, bounds(d, "B").x - root.right());
            assertEquals(MindMapLayout.DEFAULT_HGAP, bounds(d, "C").x - root.right());
        }
    }

    @Test
    void verticalGapAndShiftAreHonoured() {
        try (var d = DesignerDriver.openText("vgap.mm", MAP.replace("ID=\"R\"", "ID=\"R\" VGAP=\"40\""), MindMapEditor.ID)) {
            assertEquals(40, bounds(d, "C").y - bounds(d, "B").bottom());
        }
        String shifted = MAP.replace("ID=\"R\"", "ID=\"R\" VGAP=\"40\"").replace("TEXT=\"C\"", "TEXT=\"C\" VSHIFT=\"25\"");
        try (var d = DesignerDriver.openText("vshift.mm", shifted, MindMapEditor.ID)) {
            assertEquals(65, bounds(d, "C").y - bounds(d, "B").bottom());
        }
    }

    @Test
    void arrowLinksAreConnectionsAndMissingDestinationsAreNotDrawn() {
        try (var d = DesignerDriver.openText("links.mm", MAP, MindMapEditor.ID)) {
            MindMap model = (MindMap) d.editor().model();
            ArrowLink toB = model.arrowLinks().stream().filter(l -> l.destinationId().equals("B")).findFirst().orElseThrow();
            ArrowLink toNowhere = model.arrowLinks().stream().filter(l -> l.destinationId().equals("NOWHERE")).findFirst().orElseThrow();

            Connection connection = assertInstanceOf(Connection.class, d.figureOf(toB));
            assertSame(d.figureOf(key("D")), connection.getSourceAnchor().getOwner());
            assertSame(d.figureOf(key("B")), connection.getTargetAnchor().getOwner());
            assertNull(d.figureOf(toNowhere));
        }
    }

    @Test
    void aLargeMapDrawsEveryVisibleNodeAndLink() {
        try (var d = DesignerDriver.open(example("freemind-0.8.1-large-arrow-links.mm"), MindMapEditor.ID)) {
            MindMap model = (MindMap) d.editor().model();
            int drawnLinks = 0;
            for (MapNode node : model.nodesByKey().values()) {
                IFigure figure = d.figureOf(node.key());
                assertEquals(visible(node), figure != null, node.toString());
            }
            for (ArrowLink link : model.arrowLinks()) {
                MapNode destination = model.nodeById(link.destinationId());
                boolean shown = destination != null && visible(destination) && visible(model.node(link.source()));
                assertEquals(shown, d.figureOf(link) != null, link.toString());
                drawnLinks += shown ? 1 : 0;
            }
            assertTrue(drawnLinks > 0);
            assertNotNull(d.figureOf(model.root().key()));
        }
    }

    private static boolean visible(MapNode node) {
        for (MapNode n = node.parent(); n != null; n = n.parent()) {
            if (n.folded()) {
                return false;
            }
        }
        return true;
    }

    static Rectangle bounds(DesignerDriver d, String id) {
        IFigure figure = d.figureOf(key(id));
        assertNotNull(figure, id + " is drawn");
        return figure.getBounds().getCopy();
    }
}
