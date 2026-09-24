package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.example;
import static etalii.adp.freemind.FreeMindAsserts.key;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Set;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.AdpDataKeys;
import etalii.adp.core.ViewState;
import etalii.adp.testing.DesignerDriver;

/** Spec 001 FR-017, FR-027, US1-AS1: zoom, pan, selection and keyboard navigation. */
@RunWith(JUnit4.class)
public class ViewerInteractionTest extends FileEditorManagerTestCase {

    static final String MAP = """
            <map version="1.0.1">
            <node ID="R" TEXT="Root">
            <node ID="B" POSITION="right" TEXT="B">
            <node ID="B1" TEXT="B1"/>
            </node>
            <node ID="C" POSITION="right" TEXT="C"/>
            <node ID="E" POSITION="right" TEXT="E"/>
            <node ID="L" POSITION="left" TEXT="L"/>
            </node>
            </map>
            """;

    static final String ZOOM_IN = "etalii.adp.core.ZoomIn";
    static final String ZOOM_OUT = "etalii.adp.core.ZoomOut";
    static final String ZOOM_RESET = "etalii.adp.core.ZoomReset";
    static final String SELECT_ALL = "etalii.adp.core.SelectAll";

    @Override
    public void setUp() {
        super.setUp();
    }

    private DesignerDriver open() {
        return DesignerDriver.openText(myFixture, "view.mm", MAP);
    }

    @Test
    public void zoomInAndOutThroughThePlatformCommands() {
        try (var d = open()) {
            MindMapCanvas canvas = LayoutTest.designer(d).canvas();
            double before = d.designer().viewState().zoom();
            int width = canvas.getPreferredSize().width;
            Rectangle root = canvas.boundsOf(key("R"));

            d.run(ZOOM_IN);
            assertTrue(d.designer().viewState().zoom() > before);
            assertTrue("the map is drawn larger", canvas.getPreferredSize().width > width);
            assertTrue(canvas.boundsOf(key("R")).width > root.width);
            assertEquals("layout boxes stay unzoomed", root.width, d.viewOf(key("R")).bounds().width);

            d.run(ZOOM_OUT);
            assertEquals(before, d.designer().viewState().zoom(), 1e-9);
            assertEquals(width, canvas.getPreferredSize().width);

            d.run(ZOOM_OUT).run(ZOOM_RESET);
            assertEquals(1.0, d.designer().viewState().zoom(), 1e-9);
        }
    }

    @Test
    public void zoomStopsAtItsLimits() {
        try (var d = open()) {
            for (int i = 0; i < 20; i++) {
                d.run(ZOOM_IN);
            }
            assertEquals(ViewState.ZOOM_LEVELS[ViewState.ZOOM_LEVELS.length - 1], d.designer().viewState().zoom(), 1e-9);
            for (int i = 0; i < 20; i++) {
                d.run(ZOOM_OUT);
            }
            assertEquals(ViewState.ZOOM_LEVELS[0], d.designer().viewState().zoom(), 1e-9);
        }
    }

    @Test
    public void clicksHitTheZoomedBoxes() {
        try (var d = open()) {
            d.run(ZOOM_IN).run(ZOOM_IN);
            d.click(key("C"), 1, 0);
            assertEquals(List.of(key("C")), d.selectedKeys());
            MindMapCanvas canvas = LayoutTest.designer(d).canvas();
            Rectangle c = canvas.boundsOf(key("C"));
            assertEquals(key("C"), canvas.keyAt(new Point(c.x + 1, c.y + 1)));
            assertNull(canvas.keyAt(new Point(c.x - 3, c.y - 3)));
        }
    }

    @Test
    public void panByScrolling() {
        try (var d = DesignerDriver.open(myFixture, example("freeplane-large-map.mm"))) {
            MindMapCanvas canvas = LayoutTest.designer(d).canvas();
            JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, canvas);
            assertNotNull("the canvas scrolls", scroll);
            scroll.setSize(300, 200);
            scroll.doLayout();
            scroll.getViewport().doLayout();
            assertTrue(canvas.getSize() + " " + canvas.getPreferredSize() + " " + scroll.getViewport().getSize(),
                    canvas.getWidth() > 300 && canvas.getHeight() > 200);

            Point before = scroll.getViewport().getViewPosition();
            scroll.getHorizontalScrollBar().setValue(before.x + 50);
            scroll.getVerticalScrollBar().setValue(before.y + 80);
            assertEquals(new Point(before.x + 50, before.y + 80), scroll.getViewport().getViewPosition());
        }
    }

    @Test
    public void singleAndMultipleSelection() {
        try (var d = open()) {
            d.click(key("B"), 1, 0);
            assertEquals(List.of(key("B")), d.selectedKeys());
            d.click(key("L"), 1, InputEvent.CTRL_DOWN_MASK);
            assertEquals(List.of(key("B"), key("L")), d.selectedKeys());
            d.click(key("B"), 1, InputEvent.CTRL_DOWN_MASK);
            assertEquals("Ctrl+click toggles", List.of(key("L")), d.selectedKeys());
            d.click(key("C"), 1, InputEvent.SHIFT_DOWN_MASK);
            assertEquals(List.of(key("L"), key("C")), d.selectedKeys());
            d.click(key("E"), 1, 0);
            assertEquals(List.of(key("E")), d.selectedKeys());

            d.select(key("B"), key("L"));
            assertEquals(List.of(key("B"), key("L")), d.selectedKeys());
        }
    }

    @Test
    public void clickingEmptySpaceClearsTheSelection() {
        try (var d = open()) {
            d.click(key("B"), 1, 0);
            MindMapCanvas canvas = LayoutTest.designer(d).canvas();
            press(canvas, new Point(1, 1), new Point(1, 1), 0);
            assertEquals(List.of(), d.selectedKeys());
        }
    }

    @Test
    public void marqueeSelectsTheNodesInside() {
        try (var d = open()) {
            MindMapCanvas canvas = LayoutTest.designer(d).canvas();
            Rectangle area = canvas.boundsOf(key("C")).union(canvas.boundsOf(key("E")));
            area.grow(3, 3);
            press(canvas, area.getLocation(), new Point((int) area.getMaxX(), (int) area.getMaxY()), 0);
            assertEquals(Set.of(key("C"), key("E")), Set.copyOf(d.selectedKeys()));
            assertNull("the marquee is gone after release", canvas.marquee());
        }
    }

    @Test
    public void selectAllSelectsEveryNode() {
        try (var d = open()) {
            d.run(SELECT_ALL);
            assertEquals(List.of(key("R"), key("B"), key("B1"), key("C"), key("E"), key("L")), d.selectedKeys());
        }
    }

    @Test
    public void arrowKeysMoveTheSelectionBetweenNodes() {
        try (var d = open()) {
            d.select(key("B"));
            d.press("DOWN");
            assertEquals(List.of(key("C")), d.selectedKeys());
            d.press("DOWN");
            assertEquals(List.of(key("E")), d.selectedKeys());
            d.press("DOWN");
            assertEquals("the last sibling stays", List.of(key("E")), d.selectedKeys());
            d.press("UP").press("UP");
            assertEquals(List.of(key("B")), d.selectedKeys());
            d.press("RIGHT");
            assertEquals("right goes to a right branch's child", List.of(key("B1")), d.selectedKeys());
            d.press("LEFT").press("LEFT");
            assertEquals("left goes back to the parent, then the root", List.of(key("R")), d.selectedKeys());
            d.press("LEFT");
            assertEquals("left of the root is a left branch", List.of(key("L")), d.selectedKeys());
            d.press("RIGHT");
            assertEquals(List.of(key("R")), d.selectedKeys());
        }
    }

    @Test
    public void keyboardFocusStartsOnTheRoot() {
        try (var d = open()) {
            MindMapCanvas canvas = LayoutTest.designer(d).canvas();
            assertTrue(canvas.isFocusable());
            assertSame(canvas, d.designer().getPreferredFocusedComponent());
            d.press("DOWN");
            assertEquals("the first key selects the root", List.of(key("R")), d.selectedKeys());
            assertEquals(key("R"), canvas.focusKey());
        }
    }

    @Test
    public void theSelectionIsPublishedAsNodeKeys() {
        try (var d = open()) {
            d.select(key("C"), key("L"));
            Object[] items = d.dataContext().getData(PlatformCoreDataKeys.SELECTED_ITEMS);
            assertNotNull(items);
            assertEquals(List.of(key("C"), key("L")), List.of(items));
            assertSame(d.designer(), d.dataContext().getData(AdpDataKeys.ADP_DESIGNER));
        }
    }

    /** Press at {@code from}, drag to {@code to} and release, as a mouse would. */
    static void press(MindMapCanvas canvas, Point from, Point to, int modifiers) {
        long now = System.currentTimeMillis();
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, now, modifiers | InputEvent.BUTTON1_DOWN_MASK, from.x, from.y, 1,
                false, MouseEvent.BUTTON1));
        if (!from.equals(to)) {
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, now, modifiers | InputEvent.BUTTON1_DOWN_MASK,
                    (from.x + to.x) / 2, (from.y + to.y) / 2, 0, false, MouseEvent.NOBUTTON));
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, now, modifiers | InputEvent.BUTTON1_DOWN_MASK, to.x, to.y, 0,
                    false, MouseEvent.NOBUTTON));
        }
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, now, modifiers, to.x, to.y, 1, false, MouseEvent.BUTTON1));
        if (from.equals(to)) {
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, now, modifiers, to.x, to.y, 1, false, MouseEvent.BUTTON1));
        }
    }
}
