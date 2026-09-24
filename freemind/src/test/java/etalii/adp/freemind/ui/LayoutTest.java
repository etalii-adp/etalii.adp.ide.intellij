package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.example;
import static etalii.adp.freemind.FreeMindAsserts.key;

import java.awt.Rectangle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.NodeView;
import etalii.adp.freemind.model.ArrowLink;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.testing.DesignerDriver;

/** Spec 001 FR-013, FR-015, US1-AS1: where nodes and arrow links are drawn. */
@RunWith(JUnit4.class)
public class LayoutTest extends FileEditorManagerTestCase {

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

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void theRootIsCentredWithBranchesOnTheirSides() {
        try (var d = DesignerDriver.openText(myFixture, "layout.mm", MAP)) {
            Rectangle root = bounds(d, "R");
            for (String left : new String[] { "A", "D" }) {
                assertTrue(left + " is left of the root", bounds(d, left).getMaxX() <= root.x);
            }
            for (String right : new String[] { "B", "C" }) {
                assertTrue(right + " is right of the root", bounds(d, right).x >= root.getMaxX());
            }
            assertTrue("a left branch grows further left", bounds(d, "A1").getMaxX() <= bounds(d, "A").x);

            int rootCentre = (int) root.getCenterY();
            assertTrue("right side centred", Math.abs((bounds(d, "B").y + (int) bounds(d, "C").getMaxY()) / 2 - rootCentre) <= 1);
            assertTrue("left side centred", Math.abs((bounds(d, "A").y + (int) bounds(d, "D").getMaxY()) / 2 - rootCentre) <= 1);
            assertTrue("siblings follow document order", bounds(d, "B").getMaxY() <= bounds(d, "C").y);
            assertTrue("every box is inside the canvas", bounds(d, "A1").x >= 0 && bounds(d, "B").y >= 0);
        }
    }

    @Test
    public void horizontalGapIsHonoured() {
        String map = MAP.replace("POSITION=\"right\" TEXT=\"B\"", "HGAP=\"100\" POSITION=\"right\" TEXT=\"B\"");
        try (var d = DesignerDriver.openText(myFixture, "hgap.mm", map)) {
            Rectangle root = bounds(d, "R");
            assertEquals(100, bounds(d, "B").x - (int) root.getMaxX());
            assertEquals(MindMapLayout.DEFAULT_HGAP, bounds(d, "C").x - (int) root.getMaxX());
        }
    }

    @Test
    public void verticalGapAndShiftAreHonoured() {
        try (var d = DesignerDriver.openText(myFixture, "vgap.mm", MAP.replace("ID=\"R\"", "ID=\"R\" VGAP=\"40\""))) {
            assertEquals(40, bounds(d, "C").y - (int) bounds(d, "B").getMaxY());
        }
        String shifted = MAP.replace("ID=\"R\"", "ID=\"R\" VGAP=\"40\"").replace("TEXT=\"C\"", "TEXT=\"C\" VSHIFT=\"25\"");
        try (var d = DesignerDriver.openText(myFixture, "vshift.mm", shifted)) {
            assertEquals(65, bounds(d, "C").y - (int) bounds(d, "B").getMaxY());
        }
    }

    @Test
    public void arrowLinksAreConnectionsAndMissingDestinationsAreNotDrawn() {
        try (var d = DesignerDriver.openText(myFixture, "links.mm", MAP)) {
            MindMapDesigner designer = designer(d);
            MindMap model = designer.model();
            ArrowLink toB = model.arrowLinks().stream().filter(l -> l.destinationId().equals("B")).findFirst().orElseThrow();
            ArrowLink toNowhere = model.arrowLinks().stream().filter(l -> l.destinationId().equals("NOWHERE")).findFirst().orElseThrow();

            MindMapLayout.Arrow arrow = designer.arrowOf(toB);
            assertNotNull(arrow);
            assertEquals(key("D"), arrow.source());
            assertEquals(key("B"), arrow.target());
            assertFalse("STARTARROW None", arrow.startArrow());
            assertTrue(arrow.endArrow());
            assertNull(designer.arrowOf(toNowhere));
            assertEquals(1, designer.arrows().size());
        }
    }

    @Test
    public void aLargeMapDrawsEveryVisibleNodeAndLink() {
        try (var d = DesignerDriver.open(myFixture, example("freemind-0.8.1-large-arrow-links.mm"))) {
            MindMapDesigner designer = designer(d);
            MindMap model = designer.model();
            int drawnLinks = 0;
            for (MapNode node : model.nodesByKey().values()) {
                assertEquals(node.toString(), visible(node), d.viewOf(node.key()) != null);
            }
            for (ArrowLink link : model.arrowLinks()) {
                MapNode destination = model.nodeById(link.destinationId());
                boolean shown = destination != null && visible(destination) && visible(model.node(link.source()));
                assertEquals(link.toString(), shown, designer.arrowOf(link) != null);
                drawnLinks += shown ? 1 : 0;
            }
            assertTrue(drawnLinks > 0);
            assertNotNull(d.viewOf(model.root().key()));
        }
    }

    @Test
    public void aFoldedBranchIsHiddenAndMarked() {
        String folded = MAP.replace("<node ID=\"A\" POSITION=\"left\" TEXT=\"A\">", "<node FOLDED=\"true\" ID=\"A\" POSITION=\"left\" TEXT=\"A\">");
        try (var d = DesignerDriver.openText(myFixture, "folded.mm", folded)) {
            NodeView a = d.viewOf(key("A"));
            assertTrue(a.folded());
            assertNull("a folded branch's children are not drawn", d.viewOf(key("A1")));
            assertFalse(d.viewOf(key("B")).folded());
            assertFalse("a leaf is never shown folded", d.viewOf(key("D")).folded());

            MindMapDesigner designer = designer(d);
            MapNode node = designer.model().node(key("A"));
            designer.setShownFolded(node, false);
            assertFalse(designer.isShownFolded(node));
            assertNotNull("unfolding for display shows the children", d.viewOf(key("A1")));
            assertEquals("display folding never edits the file", folded, d.text());
            assertFalse(d.isModified());
        }
    }

    @Test
    public void nodesDoNotOverlap() {
        try (var d = DesignerDriver.open(myFixture, example("freeplane-1.11-sample.mm"))) {
            var views = designer(d).model().nodesByKey().keySet().stream().map(d::viewOf).filter(v -> v != null).toList();
            for (int i = 0; i < views.size(); i++) {
                for (int j = i + 1; j < views.size(); j++) {
                    assertFalse(views.get(i).key() + " overlaps " + views.get(j).key(), views.get(i).bounds().intersects(views.get(j).bounds()));
                }
            }
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

    static MindMapDesigner designer(DesignerDriver d) {
        return assertInstanceOf(d.designer(), MindMapDesigner.class);
    }

    static Rectangle bounds(DesignerDriver d, String id) {
        NodeView view = d.viewOf(key(id));
        assertNotNull(id + " is drawn", view);
        return view.bounds();
    }
}
