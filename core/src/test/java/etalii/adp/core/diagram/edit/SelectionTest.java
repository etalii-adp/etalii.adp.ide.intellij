package etalii.adp.core.diagram.edit;

import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.Direction;
import etalii.adp.core.diagram.Sizing;
import etalii.adp.core.diagram.sample.SampleFiles;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.testing.DiagramDriver;

/** T055, FR-017: click, Ctrl-click, Shift-click, marquee, connections, non-selectable types and Select All. */
@RunWith(JUnit4.class)
public class SelectionTest extends FileEditorManagerTestCase {

    private DiagramDriver open(String name) {
        return DiagramDriver.open(myFixture, SampleFiles.directory().resolve(name));
    }

    @Test
    public void clickCtrlClickAndShiftClick() {
        SampleProvider.register(getTestRootDisposable());
        try (var d = open("two-tasks.adpsample")) {
            d.driver().click("a", 1, 0);
            assertEquals(List.of("a"), d.driver().selectedKeys());
            d.driver().click("b", 1, InputEvent.CTRL_DOWN_MASK);
            assertEquals(List.of("a", "b"), d.driver().selectedKeys());
            d.driver().click("a", 1, InputEvent.CTRL_DOWN_MASK);
            assertEquals("Ctrl-click toggles", List.of("b"), d.driver().selectedKeys());
            d.driver().click("a", 1, InputEvent.SHIFT_DOWN_MASK);
            assertEquals("Shift-click adds", List.of("b", "a"), d.driver().selectedKeys());
            d.driver().click("a", 1, InputEvent.SHIFT_DOWN_MASK);
            assertEquals("Shift-click keeps what is selected", List.of("b", "a"), d.driver().selectedKeys());
            d.driver().click("b", 1, 0);
            assertEquals("a plain click on a selected item selects only it", List.of("b"), d.driver().selectedKeys());

            DiagramCanvas canvas = d.tool().canvas();
            click(canvas, canvas.toCanvas(new java.awt.geom.Point2D.Double(700, 400)), 0);
            assertEquals("a click on empty canvas clears the selection", List.of(), d.driver().selectedKeys());
        }
    }

    @Test
    public void connectionsAreSelectable() {
        SampleProvider.register(getTestRootDisposable());
        try (var d = open("two-tasks.adpsample")) {
            List<Point> route = d.connectionView("f1").route();
            Point p = route.get(0);
            Point q = route.get(1);
            DiagramCanvas canvas = d.tool().canvas();
            click(canvas, canvas.toCanvas(new java.awt.geom.Point2D.Double((p.x + q.x) / 2.0, (p.y + q.y) / 2.0)), 0);
            assertEquals(List.of("f1"), d.driver().selectedKeys());
            d.driver().click("a", 1, InputEvent.CTRL_DOWN_MASK);
            assertEquals(List.of("f1", "a"), d.driver().selectedKeys());
        }
    }

    @Test
    public void aMarqueeSelectsWhatLiesWhollyInsideAndAddsWithCtrl() {
        SampleProvider.register(getTestRootDisposable());
        try (var d = open("two-tasks.adpsample")) {
            d.marquee(10, 10, 700, 400);
            assertEquals(List.of("a", "b", "f1"), d.driver().selectedKeys());

            d.marquee(30, 30, 170, 110);
            assertEquals("only a lies wholly inside", List.of("a"), d.driver().selectedKeys());

            DiagramCanvas canvas = d.tool().canvas();
            var b = d.elementView("b").bounds();
            drag(canvas, canvas.toCanvas(new java.awt.geom.Point2D.Double(b.x - 5, b.y - 5)),
                    canvas.toCanvas(new java.awt.geom.Point2D.Double(b.getMaxX() + 5, b.getMaxY() + 5)), InputEvent.CTRL_DOWN_MASK);
            assertEquals("Ctrl adds what the marquee takes", List.of("a", "b"), d.driver().selectedKeys());

            d.marquee(600, 300, 700, 400);
            assertEquals("an empty marquee clears the selection", List.of(), d.driver().selectedKeys());
        }
    }

    @Test
    public void nonSelectableTypesAreIgnoredByClicksMarqueesAndSelectAll() {
        SampleProvider.register(getTestRootDisposable(), DiagramDefinition.builder("sample")
                .element("task", e -> e.selectable(false).movable(false)
                        .text("title", t -> t.property("title"))
                        .anchor("in", a -> a.at(0, 0.5).accepts("flow", Direction.IN))
                        .anchor("out", a -> a.at(1, 0.5).accepts("flow", Direction.OUT))
                        .property("title", p -> p.label("Title")))
                .element("decision", e -> e.sizing(Sizing.fixed(100, 60))
                        .text("title", t -> t.property("title"))
                        .anchor("in", a -> a.at(0, 0.5).accepts("flow", Direction.IN))
                        .property("title", p -> p.label("Title")))
                .connection("flow", c -> c.label("Flow")));
        try (var d = open("invisible-anchors.adpsample")) {
            d.driver().click("d", 1, 0);
            assertEquals(List.of("d"), d.driver().selectedKeys());
            d.driver().click("a", 1, 0);
            assertEquals("a click on a non-selectable element is a click on empty canvas", List.of(), d.driver().selectedKeys());

            d.marquee(10, 10, 700, 400);
            assertEquals(List.of("d", "f1"), d.driver().selectedKeys());

            d.driver().select();
            d.driver().run("etalii.adp.core.SelectAll");
            assertEquals(List.of("d", "f1"), d.driver().selectedKeys());
        }
    }

    @Test
    public void selectAllSelectsEveryElementAndConnection() {
        SampleProvider.register(getTestRootDisposable());
        try (var d = open("two-tasks.adpsample")) {
            d.driver().run("etalii.adp.core.SelectAll");
            assertEquals(List.of("a", "b", "f1"), d.driver().selectedKeys());
        }
    }

    static void click(DiagramCanvas canvas, Point at, int modifiers) {
        mouse(canvas, MouseEvent.MOUSE_PRESSED, at, 1, InputEvent.BUTTON1_DOWN_MASK | modifiers);
        mouse(canvas, MouseEvent.MOUSE_RELEASED, at, 1, modifiers);
        mouse(canvas, MouseEvent.MOUSE_CLICKED, at, 1, modifiers);
    }

    static void drag(DiagramCanvas canvas, Point from, Point to, int modifiers) {
        mouse(canvas, MouseEvent.MOUSE_PRESSED, from, 1, InputEvent.BUTTON1_DOWN_MASK | modifiers);
        for (int step = 1; step <= 4; step++) {
            mouse(canvas, MouseEvent.MOUSE_DRAGGED, new Point(from.x + (to.x - from.x) * step / 4, from.y + (to.y - from.y) * step / 4), 0,
                    InputEvent.BUTTON1_DOWN_MASK | modifiers);
        }
        mouse(canvas, MouseEvent.MOUSE_RELEASED, to, 1, modifiers);
    }

    static void mouse(DiagramCanvas canvas, int id, Point at, int clickCount, int modifiers) {
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        canvas.dispatchEvent(new MouseEvent(canvas, id, System.currentTimeMillis(), modifiers, at.x, at.y, clickCount, false, button));
    }
}
