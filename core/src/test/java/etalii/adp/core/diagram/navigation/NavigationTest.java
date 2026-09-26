package etalii.adp.core.diagram.navigation;

import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.diagram.sample.SampleDefinition;
import etalii.adp.core.diagram.sample.SampleFiles;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramDesigner;
import etalii.adp.core.diagram.view.ElementView;
import etalii.adp.testing.DiagramDriver;

/**
 * T078, acceptance US4-1 and US4-2 (FR-025): zooming scales elements, connections, texts and
 * diagram-space lanes together while view-space sectors stay fixed; Ctrl+wheel zooms around the
 * pointer; with zoom off the zoom actions are disabled and Ctrl+wheel does nothing; Space-drag and
 * middle-drag pan, and do nothing with pan off.
 */
@RunWith(JUnit4.class)
public class NavigationTest extends FileEditorManagerTestCase {

    static final String ZOOM_IN = "etalii.adp.core.ZoomIn";
    static final String ZOOM_OUT = "etalii.adp.core.ZoomOut";
    static final String ZOOM_RESET = "etalii.adp.core.ZoomReset";

    private DiagramDriver open(String name) {
        return DiagramDriver.open(myFixture, SampleFiles.directory().resolve(name));
    }

    @Test
    public void zoomingScalesElementsConnectionsTextsAndDiagramLanesTogether() {
        SampleProvider.register(getTestRootDisposable());
        try (var d = open("lanes.adpsample")) {
            DiagramDesigner designer = d.designer();
            DiagramCanvas canvas = designer.canvas();
            Rectangle element = canvas.toCanvas(d.elementView("a").bounds());
            Rectangle text = canvas.toCanvas(designer.textBounds("a", "title"));
            Point bend = canvas.toCanvas(d.connectionView("f1").route().get(1));
            Rectangle lane = SectorLayer.onCanvas(designer, "l2");
            assertEquals(new Rectangle(0, 200, 10000, 200), lane);

            d.zoom(3);

            assertEquals(2.0, d.zoomLevel(), 1e-9);
            assertScaled(element, canvas.toCanvas(d.elementView("a").bounds()));
            assertScaled(text, canvas.toCanvas(designer.textBounds("a", "title")));
            Point zoomed = canvas.toCanvas(d.connectionView("f1").route().get(1));
            assertEquals(new Point(bend.x * 2, bend.y * 2), zoomed);
            assertScaled(lane, SectorLayer.onCanvas(designer, "l2"));

            canvas.setSize(1200, 1000);
            BufferedImage image = paint(canvas);
            ElementView a = d.elementView("a");
            Rectangle box = canvas.toCanvas(a.bounds());
            assertEquals("the element is painted at twice its size", new Color(a.fill().getRGB()),
                    new Color(image.getRGB(box.x + box.width / 4, box.y + 4)));
            Rectangle l2 = SectorLayer.onCanvas(designer, "l2");
            assertEquals("the lane's header is painted at twice its width", new Color(SectorLayer.HEADER.getRGB()),
                    new Color(image.getRGB(l2.x + 2 * 20, l2.y + l2.height - 4)));
        }
    }

    @Test
    public void viewSpaceSectorsStayFixedWhileZoomingAndScrolling() {
        SampleProvider.register(getTestRootDisposable());
        try (var d = open("legend-view-space.adpsample")) {
            DiagramDesigner designer = d.designer();
            DiagramCanvas canvas = designer.canvas();
            JViewport viewport = viewport(d, 300, 200);
            viewport.setViewPosition(new Point(60, 0));
            assertEquals(new Point(60, 0), canvas.viewportPosition());
            assertEquals(new Rectangle(60, 0, 150, 10000), SectorLayer.onCanvas(designer, "g"));
            Rectangle main = canvas.toCanvas(d.elementView("a").bounds());

            d.zoom(1);

            assertEquals(1.25, d.zoomLevel(), 1e-9);
            Point origin = canvas.viewportPosition();
            assertEquals("fixed in the viewport", new Rectangle(origin.x, origin.y, 150, 10000), SectorLayer.onCanvas(designer, "g"));
            Rectangle zoomed = canvas.toCanvas(d.elementView("a").bounds());
            assertEquals("the content did scale", main.x * 1.25, zoomed.x, 1);

            viewport.setViewPosition(new Point(20, 0));
            assertEquals("fixed while scrolling", new Rectangle(20, 0, 150, 10000), SectorLayer.onCanvas(designer, "g"));

            BufferedImage image = paint(canvas);
            assertEquals("the header is painted in viewport pixels", new Color(SectorLayer.HEADER.getRGB()), new Color(image.getRGB(20 + 5, 5)));
        }
    }

    @Test
    public void ctrlWheelZoomsThroughTheLevelsAroundThePointer() {
        SampleProvider.register(getTestRootDisposable());
        try (var d = open("legend-view-space.adpsample")) {
            DiagramCanvas canvas = d.designer().canvas();
            viewport(d, 300, 200);
            Rectangle visible = canvas.getVisibleRect();
            Point pointer = new Point(visible.x + visible.width / 2, visible.y + visible.height / 2);
            Point2D under = canvas.toDiagram(pointer);
            Point inViewport = new Point(pointer.x - canvas.viewportPosition().x, pointer.y - canvas.viewportPosition().y);

            d.zoom(1);

            assertEquals(1.25, d.zoomLevel(), 1e-9);
            Point origin = canvas.viewportPosition();
            Point2D now = canvas.toDiagram(new Point(origin.x + inViewport.x, origin.y + inViewport.y));
            assertEquals("the point under the pointer stays there", under.getX(), now.getX(), 1);

            d.zoom(-2);
            assertEquals(0.75, d.zoomLevel(), 1e-9);
            d.zoom(-10);
            assertEquals("the first level", 0.25, d.zoomLevel(), 1e-9);
        }
    }

    @Test
    public void theZoomActionsWorkWithZoomOn() {
        SampleProvider.register(getTestRootDisposable());
        try (var d = open("lanes.adpsample")) {
            for (String id : List.of(ZOOM_IN, ZOOM_OUT, ZOOM_RESET)) {
                assertTrue(id, d.driver().presentation(id).isEnabled());
            }
            d.driver().run(ZOOM_IN);
            assertEquals(1.25, d.zoomLevel(), 1e-9);
            d.driver().run(ZOOM_RESET);
            assertEquals(1.0, d.zoomLevel(), 1e-9);
        }
    }

    @Test
    public void withZoomOffTheZoomActionsAreDisabledAndCtrlWheelDoesNothing() {
        SampleProvider.register(getTestRootDisposable(), SampleDefinition.builder().view(v -> v.zoom(false)));
        try (var d = open("legend-view-space.adpsample")) {
            DiagramCanvas canvas = d.designer().canvas();
            JViewport viewport = viewport(d, 300, 200);
            viewport.setViewPosition(new Point(50, 0));
            String text = d.driver().text();
            for (String id : List.of(ZOOM_IN, ZOOM_OUT, ZOOM_RESET)) {
                assertFalse(id, d.driver().presentation(id).isEnabled());
            }

            d.driver().run(ZOOM_IN);
            d.zoom(2);
            d.zoom(-3);

            assertEquals(1.0, d.zoomLevel(), 1e-9);
            assertEquals("not scrolled either", new Point(50, 0), canvas.viewportPosition());
            assertEquals(text, d.driver().text());
        }
    }

    @Test
    public void middleDragAndSpaceDragPanWithoutEditing() {
        SampleProvider.register(getTestRootDisposable());
        try (var d = open("legend-view-space.adpsample")) {
            DiagramCanvas canvas = d.designer().canvas();
            viewport(d, 300, 200);
            String text = d.driver().text();

            drag(canvas, new Point(200, 150), new Point(150, 150), InputEvent.BUTTON2_DOWN_MASK, MouseEvent.BUTTON2);
            assertEquals("middle-drag", new Point(50, 0), canvas.viewportPosition());

            Point main = canvas.toCanvas(centre(d.elementView("a").bounds()));
            Point onMain = new Point(main.x - 50, main.y);
            key(canvas, KeyEvent.KEY_PRESSED, KeyEvent.VK_SPACE);
            drag(canvas, onMain, new Point(onMain.x + 30, onMain.y), InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1);
            key(canvas, KeyEvent.KEY_RELEASED, KeyEvent.VK_SPACE);
            assertEquals("Space-drag, even over an element", new Point(20, 0), canvas.viewportPosition());

            assertEquals("nothing moved", text, d.driver().text());
            assertEquals("nothing selected", List.of(), d.driver().selectedKeys());
        }
    }

    @Test
    public void withPanOffSpaceDragAndMiddleDragDoNothing() {
        SampleProvider.register(getTestRootDisposable(), SampleDefinition.builder().view(v -> v.pan(false)));
        try (var d = open("legend-view-space.adpsample")) {
            DiagramCanvas canvas = d.designer().canvas();
            JViewport viewport = viewport(d, 300, 200);
            viewport.setViewPosition(new Point(50, 0));
            String text = d.driver().text();

            drag(canvas, new Point(200, 150), new Point(100, 150), InputEvent.BUTTON2_DOWN_MASK, MouseEvent.BUTTON2);
            key(canvas, KeyEvent.KEY_PRESSED, KeyEvent.VK_SPACE);
            drag(canvas, new Point(250, 150), new Point(150, 150), InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1);
            key(canvas, KeyEvent.KEY_RELEASED, KeyEvent.VK_SPACE);

            assertEquals(new Point(50, 0), canvas.viewportPosition());
            assertEquals(text, d.driver().text());
        }
    }

    /**
     * Give the designer's scroll pane a size, as a window would, and lay it out; headless, nothing
     * else sizes it.
     */
    static JViewport viewport(DiagramDriver d, int width, int height) {
        DiagramCanvas canvas = d.designer().canvas();
        JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, canvas);
        for (Component c = pane; c != null; c = c.getParent()) {
            c.setBounds(0, 0, width, height);
        }
        pane.doLayout();
        pane.getViewport().doLayout();
        d.driver().settle();
        return pane.getViewport();
    }

    static BufferedImage paint(DiagramCanvas canvas) {
        BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** Press, drag in steps and release, at points in viewport pixels, as a real pointer would while the canvas scrolls under it. */
    static void drag(DiagramCanvas canvas, Point from, Point to, int buttonMask, int button) {
        mouse(canvas, MouseEvent.MOUSE_PRESSED, from, buttonMask, button, 1);
        for (int step = 1; step <= 4; step++) {
            mouse(canvas, MouseEvent.MOUSE_DRAGGED, new Point(from.x + (to.x - from.x) * step / 4, from.y + (to.y - from.y) * step / 4), buttonMask,
                    MouseEvent.NOBUTTON, 0);
        }
        mouse(canvas, MouseEvent.MOUSE_RELEASED, to, 0, button, 1);
    }

    private static void mouse(DiagramCanvas canvas, int id, Point inViewport, int modifiers, int button, int clickCount) {
        Point origin = canvas.viewportPosition();
        canvas.dispatchEvent(new MouseEvent(canvas, id, System.currentTimeMillis(), modifiers, inViewport.x + origin.x, inViewport.y + origin.y,
                clickCount, false, button));
    }

    /** A key to the canvas's own listeners; headless, the focus manager drops key events for a component that is not showing. */
    static void key(DiagramCanvas canvas, int id, int keyCode) {
        KeyEvent event = new KeyEvent(canvas, id, System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : canvas.getKeyListeners()) {
            if (id == KeyEvent.KEY_PRESSED) {
                listener.keyPressed(event);
            } else {
                listener.keyReleased(event);
            }
        }
    }

    private static Point2D centre(Rectangle box) {
        return new Point2D.Double(box.getCenterX(), box.getCenterY());
    }

    private static void assertScaled(Rectangle before, Rectangle after) {
        assertEquals("x of " + after, before.x * 2, after.x, 1);
        assertEquals("y of " + after, before.y * 2, after.y, 1);
        assertEquals("width of " + after, before.width * 2, after.width, 1);
        assertEquals("height of " + after, before.height * 2, after.height, 1);
    }
}
