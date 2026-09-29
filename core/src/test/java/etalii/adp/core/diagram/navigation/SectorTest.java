package etalii.adp.core.diagram.navigation;

import static etalii.adp.core.diagram.navigation.NavigationTest.paint;
import static etalii.adp.core.diagram.navigation.NavigationTest.viewport;
import static org.junit.Assert.assertNotEquals;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.diagram.DiagramChange;
import etalii.adp.core.diagram.DiagramChange.SectorChanged;
import etalii.adp.core.diagram.sample.SampleDefinition;
import etalii.adp.core.diagram.sample.SampleFiles;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramFileEditor;
import etalii.adp.testing.DiagramDriver;

/**
 * T079, acceptance US4-3 (FR-026): dragging an element into another diagram-space lane changes its
 * membership in the file as one undo step labelled {@code Move to <lane>}, and the listener is told
 * with {@code SectorChanged}; membership of a view-space sector is decided in the viewport, after
 * scrolling; and lanes are painted as bands with their labels in their orientation.
 */
@RunWith(JUnit4.class)
public class SectorTest extends FileEditorManagerTestCase {

    private final List<DiagramChange> told = new CopyOnWriteArrayList<>();

    @Override
    public void setUp() {
        super.setUp();
        SampleProvider.register(getTestRootDisposable(), SampleDefinition.builder().listener((tool, changes) -> told.addAll(changes)));
    }

    private DiagramDriver open(String name) {
        return DiagramDriver.open(myFixture, SampleFiles.directory().resolve(name));
    }

    @Test
    public void draggingIntoAnotherLaneChangesMembershipAsOneUndoStep() {
        try (var d = open("lanes.adpsample")) {
            String opened = d.driver().text();

            d.dragToSector("a", "l2");

            String edited = d.driver().text();
            assertEquals("l2", attribute(box(edited, "a"), "lane"));
            assertEquals("the other element is untouched", box(opened, "b"), box(edited, "b"));
            assertEquals("l2", d.tool().diagram().element("a").sector());
            assertEquals("Undo Move to Shop", d.driver().undoLabel());
            assertTrue("told: " + told, told.contains(new SectorChanged("a", "l1", "l2")));
            assertTrue(d.lastChanges().contains(new SectorChanged("a", "l1", "l2")));

            d.driver().undo();
            assertEquals(opened, d.driver().text());
            assertNull("one step", d.driver().undoLabel());
            assertTrue("told on undo: " + told, told.contains(new SectorChanged("a", "l2", "l1")));

            d.driver().redo();
            assertEquals(edited, d.driver().text());
        }
    }

    @Test
    public void membershipOfAViewSpaceSectorIsDecidedInTheViewportAfterScrolling() {
        try (var d = open("legend-view-space.adpsample")) {
            DiagramFileEditor tool = d.tool();
            viewport(d, 300, 200).setViewPosition(new Point(100, 0));
            assertNull(tool.diagram().element("a").sector());

            d.dragToSector("a", "g");

            assertEquals("g", attribute(box(d.driver().text(), "a"), "lane"));
            Rectangle moved = d.elementView("a").bounds();
            assertTrue("outside the legend's viewport bounds in diagram coordinates, so only the scroll put it inside: " + moved,
                    moved.getCenterX() > 150);
            assertEquals("Undo Move to Legend", d.driver().undoLabel());
            assertTrue("told: " + told, told.contains(new SectorChanged("a", null, "g")));
        }
    }

    @Test
    public void lanesArePaintedAsBandsWithTheirLabelsInTheirOrientation() {
        try (var d = open("lanes.adpsample")) {
            DiagramFileEditor tool = d.tool();
            DiagramCanvas canvas = tool.canvas();
            assertEquals(new Rectangle(0, 0, 10000, 200), SectorLayer.onCanvas(tool, "l1"));
            assertEquals(new Rectangle(0, 200, 10000, 200), SectorLayer.onCanvas(tool, "l2"));
            canvas.setSize(600, 450);
            BufferedImage image = paint(canvas);
            Color header = new Color(SectorLayer.HEADER.getRGB());

            assertEquals("horizontal bands have their header on the left", header, new Color(image.getRGB(3, 190)));
            assertNotEquals("not along the top", header, new Color(image.getRGB(300, 5)));
            assertTrue("the label is drawn in the header", anyOther(image, new Rectangle(4, 20, 16, 160), header));
            assertTrue("the second lane's label too", anyOther(image, new Rectangle(4, 220, 16, 160), header));
        }
    }

    @Test
    public void viewSpaceSectorsArePaintedWithTheirLabelsInTheirOrientation() {
        try (var d = open("legend-view-space.adpsample")) {
            DiagramCanvas canvas = d.tool().canvas();
            canvas.setSize(600, 300);
            BufferedImage image = paint(canvas);
            Color header = new Color(SectorLayer.HEADER.getRGB());

            assertEquals("vertical bands have their header along the top", header, new Color(image.getRGB(5, 5)));
            assertNotEquals("not on the left", header, new Color(image.getRGB(5, 150)));
            assertTrue("the label is drawn in the header", anyOther(image, new Rectangle(20, 4, 110, 16), header));
            assertNotEquals("nothing outside the legend", header, new Color(image.getRGB(200, 5)));
        }
    }

    private static boolean anyOther(BufferedImage image, Rectangle area, Color colour) {
        for (int x = area.x; x < area.x + area.width; x++) {
            for (int y = area.y; y < area.y + area.height; y++) {
                if (image.getRGB(x, y) != colour.getRGB()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String box(String text, String id) {
        Matcher m = Pattern.compile("<box id=\"" + id + "\"[^>]*>").matcher(text);
        assertTrue("no box " + id + " in " + text, m.find());
        return m.group();
    }

    private static String attribute(String tag, String name) {
        Matcher m = Pattern.compile(" " + name + "=\"([^\"]*)\"").matcher(tag);
        return m.find() ? m.group(1) : null;
    }
}
