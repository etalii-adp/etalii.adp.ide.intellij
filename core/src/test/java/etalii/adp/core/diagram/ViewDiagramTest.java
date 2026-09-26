package etalii.adp.core.diagram;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.diagram.sample.SampleFiles;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.diagram.view.AnchorView;
import etalii.adp.core.diagram.view.ConnectionView;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.ElementView;
import etalii.adp.testing.DiagramDriver;
import etalii.adp.testing.Layout;

/** US1-1 to US1-3: the sample files are drawn exactly as the sample definition declares. */
@RunWith(JUnit4.class)
public class ViewDiagramTest extends FileEditorManagerTestCase {

    @Override
    public void setUp() {
        super.setUp();
        SampleProvider.register(getTestRootDisposable());
    }

    private DiagramDriver open(String name) {
        return DiagramDriver.open(myFixture, SampleFiles.directory().resolve(name));
    }

    @Test
    public void aSampleFileOpensInTheSampleDesigner() {
        try (var d = open("two-tasks.adpsample")) {
            assertNotNull(d.designer());
            assertEquals(SampleProvider.EDITOR_TYPE_ID, d.driver().editorTypeIdUsed());
            assertEquals(Layout.DESIGNER, d.driver().layout());
            assertEquals(SampleFiles.read("two-tasks.adpsample"), d.driver().text());
            assertEquals(List.of("a", "b"), d.elementKeys());
            assertEquals(List.of("f1"), d.connectionKeys());
        }
    }

    @Test
    public void us1_1_roundedRectanglesWithWrappedTitlesSizedToFit() {
        try (var d = open("two-tasks.adpsample")) {
            ElementView a = d.elementView("a");
            ElementView b = d.elementView("b");
            for (ElementView view : List.of(a, b)) {
                assertEquals("task", view.type());
                assertSame(Outline.ROUNDED_RECTANGLE, view.outline());
                assertFalse(view.placeholder());
                assertEquals(new Color(Tone.BLUE.fill().getRGB()), new Color(view.fill().getRGB()));
                assertEquals(new Color(Tone.BLUE.text().getRGB()), new Color(view.text().getRGB()));
            }

            assertEquals("the stored size is kept", new Rectangle(40, 40, 120, 60), a.bounds());
            assertEquals("Place order", a.texts().get("title"));
            assertEquals("Ann", a.texts().get("owner"));

            String title = b.texts().get("title");
            assertTrue("the long title wraps: " + title, title.contains("\n"));
            assertEquals(List.of("Ship", "the", "goods", "to", "the", "customer", "quickly"), Arrays.asList(title.trim().split("\\s+")));
            Rectangle box = b.bounds();
            assertEquals(new Point(240, 40), box.getLocation());
            assertTrue("auto-sized within the maximum width: " + box, box.width > 0 && box.width <= JBUI.scale(200));
            int lineHeight = d.designer().canvas().getFontMetrics(b.font()).getHeight();
            assertTrue("two lines fit: " + box, box.height >= 2 * lineHeight);
            assertTrue("no taller than the text needs: " + box, box.height < 4 * lineHeight);
        }
    }

    @Test
    public void us1_2_aDashedCurvedLineWithAnOpenArrowAndAMiddleLabelBetweenTheDeclaredAnchors() {
        try (var d = open("flow-dashed-curved.adpsample")) {
            ConnectionView note = d.connectionView("n1");
            assertEquals("note", note.type());
            assertEquals(LineStyle.CURVED, note.line());
            assertEquals(Dash.DASHED, note.dash());
            assertEquals(ArrowHead.NONE, note.source());
            assertEquals(ArrowHead.OPEN, note.target());
            assertEquals(Map.of(LabelSlot.MIDDLE, "see also"), note.labels());
            assertFalse(note.placeholder());

            Rectangle a = d.elementView("a").bounds();
            Rectangle b = d.elementView("b").bounds();
            assertNear(new Point((int) a.getCenterX(), a.y + a.height), note.route().get(0));
            assertNear(new Point((int) b.getCenterX(), b.y), note.route().get(note.route().size() - 1));
            assertTrue(d.anchorsOf("a").stream().anyMatch(anchor -> anchor.id().equals("bottom") && anchor.visible()));
        }
    }

    @Test
    public void us1_3_invisibleAnchorsAreNotDrawnButStillAttach() {
        try (var d = open("invisible-anchors.adpsample")) {
            List<AnchorView> anchors = d.anchorsOf("d");
            assertEquals(List.of("in", "out", "top"), anchors.stream().map(AnchorView::id).toList());
            assertTrue(anchors.stream().noneMatch(AnchorView::visible));
            assertTrue(d.anchorsOf("a").stream().allMatch(AnchorView::visible));

            ElementView decision = d.elementView("d");
            assertSame(Outline.DIAMOND, decision.outline());
            assertEquals("fixed size", new Rectangle(240, 40, JBUI.scale(100), JBUI.scale(60)), decision.bounds());

            ConnectionView flow = d.connectionView("f1");
            assertEquals(LineStyle.ORTHOGONAL, flow.line());
            assertEquals(Map.of(LabelSlot.MIDDLE, "check", LabelSlot.SOURCE, "t1", LabelSlot.TARGET, "always"), flow.labels());
            Rectangle box = decision.bounds();
            assertNear(new Point(box.x, (int) box.getCenterY()), flow.route().get(flow.route().size() - 1));
            Rectangle a = d.elementView("a").bounds();
            assertNear(new Point(a.x + a.width, (int) a.getCenterY()), flow.route().get(0));
            for (int i = 1; i < flow.route().size(); i++) {
                Point p = flow.route().get(i - 1);
                Point q = flow.route().get(i);
                assertTrue("orthogonal: " + flow.route(), p.x == q.x || p.y == q.y);
            }
        }
    }

    @Test
    public void everySampleFileThatReadsIsPainted() {
        for (String name : List.of("two-tasks", "flow-dashed-curved", "invisible-anchors", "lanes", "legend-view-space", "unknown-type", "crlf")) {
            try (var d = open(name + ".adpsample")) {
                DiagramCanvas canvas = d.designer().canvas();
                canvas.setSize(canvas.getPreferredSize());
                assertTrue(name, canvas.getWidth() > 0 && canvas.getHeight() > 0);
                BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try {
                    canvas.paint(graphics);
                } finally {
                    graphics.dispose();
                }
                ElementView first = d.elementView(d.elementKeys().get(0));
                Rectangle box = first.bounds();
                assertEquals(name + ": the first element is filled", new Color(first.fill().getRGB()),
                        new Color(image.getRGB(box.x + box.width / 4, box.y + 2)));
            }
        }
    }

    @Test
    public void theStructureViewShowsSectorsElementsAndConnectionsAndFollowsTheSelection() {
        try (var d = open("lanes.adpsample")) {
            var model = d.driver().structure();
            var lanes = model.getRoot().getChildren();
            assertEquals(List.of("Customer", "Shop"), Arrays.stream(lanes).map(e -> e.getPresentation().getPresentableText()).toList());
            var task = (StructureViewTreeElement) lanes[0].getChildren()[0];
            assertEquals("a", task.getValue());
            assertEquals("Task Place order", task.getPresentation().getPresentableText());
            var flow = (StructureViewTreeElement) task.getChildren()[0];
            assertEquals("Flow → Ship", flow.getPresentation().getPresentableText());

            flow.navigate(true);
            assertEquals(List.of("f1"), d.driver().selectedKeys());
            d.driver().select("b");
            assertEquals("b", model.getCurrentEditorElement());
        }
    }

    private static void assertNear(Point expected, Point actual) {
        assertTrue("expected " + expected + " but was " + actual, Math.abs(expected.x - actual.x) <= 1 && Math.abs(expected.y - actual.y) <= 1);
    }
}
