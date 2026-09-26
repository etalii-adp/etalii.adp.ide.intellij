package etalii.adp.core.diagram.edit;

import static etalii.adp.core.diagram.edit.SelectionTest.mouse;

import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.diagram.sample.SampleFiles;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.diagram.view.AnchorView;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.testing.DiagramDriver;

/** T056, FR-018: what the definition or a rule refuses is not done, and the user sees that and why. */
@RunWith(JUnit4.class)
public class RefusalTest extends FileEditorManagerTestCase {

    @Override
    public void setUp() {
        super.setUp();
        SampleProvider.register(getTestRootDisposable());
    }

    private DiagramDriver open(String name) {
        return DiagramDriver.open(myFixture, SampleFiles.directory().resolve(name));
    }

    private static RefusalFeedback feedback(DiagramDriver d) {
        return RefusalFeedback.of(d.designer().canvas());
    }

    @Test
    public void anAnchorRefusesTheWrongDirection() {
        try (var d = open("two-tasks.adpsample")) {
            String before = d.driver().text();
            d.connect("b", "in", "a", "out");
            assertEquals("the first type the anchor accepts", "'flow' may not start at anchor 'in' of 'task'", d.refusal());
            assertEquals(before, d.driver().text());
            assertEquals(d.refusal(), feedback(d).lastBalloon());
            assertFalse("the drag feedback is gone", feedback(d).showingRefusal());
        }
    }

    @Test
    public void anAnchorRefusesAConnectionTypeItDoesNotDeclare() {
        try (var d = open("two-tasks.adpsample")) {
            String before = d.driver().text();
            d.connect("note", "a", "out", "b", "in");
            assertEquals("anchor 'out' of 'task' does not accept 'note'", d.refusal());
            assertEquals(before, d.driver().text());
            assertEquals(d.refusal(), feedback(d).lastBalloon());
        }
    }

    @Test
    public void aRuleRefusalShowsItsReasonInABalloon() {
        try (var d = open("two-tasks.adpsample")) {
            String before = d.driver().text();
            d.driver().select("a", "b").press("DELETE");
            assertEquals("a diagram needs at least one task", d.refusal());
            assertEquals("a diagram needs at least one task", feedback(d).lastBalloon());
            assertEquals(before, d.driver().text());
            assertEquals(List.of("a", "b"), d.elementKeys());
        }
    }

    @Test
    public void aRefusedTargetIsShownDuringTheDrag() {
        try (var d = open("two-tasks.adpsample")) {
            String before = d.driver().text();
            DiagramCanvas canvas = d.designer().canvas();
            Point from = canvas.toCanvas(anchor(d, "a", "out"));
            Point refused = canvas.toCanvas(anchor(d, "b", "out"));
            Point allowed = canvas.toCanvas(anchor(d, "b", "in"));

            mouse(canvas, MouseEvent.MOUSE_PRESSED, from, 1, InputEvent.BUTTON1_DOWN_MASK);
            mouse(canvas, MouseEvent.MOUSE_DRAGGED, new Point(from.x + 20, from.y + 20), 0, InputEvent.BUTTON1_DOWN_MASK);
            assertFalse("nothing refused over empty canvas", feedback(d).showingRefusal());

            mouse(canvas, MouseEvent.MOUSE_DRAGGED, refused, 0, InputEvent.BUTTON1_DOWN_MASK);
            assertTrue("a refused target is shown", feedback(d).showingRefusal());
            assertEquals("'flow' may not end at anchor 'out' of 'task'", feedback(d).refusedReason());
            Rectangle2D area = feedback(d).refusedArea();
            assertTrue("outlined at the target: " + area, area.contains(anchor(d, "b", "out")));
            assertSame(RefusalFeedback.NOT_ALLOWED, canvas.getCursor());

            mouse(canvas, MouseEvent.MOUSE_DRAGGED, allowed, 0, InputEvent.BUTTON1_DOWN_MASK);
            assertFalse("an allowed target is not refused", feedback(d).showingRefusal());

            mouse(canvas, MouseEvent.MOUSE_DRAGGED, refused, 0, InputEvent.BUTTON1_DOWN_MASK);
            mouse(canvas, MouseEvent.MOUSE_RELEASED, refused, 1, 0);
            d.driver().settle();
            assertEquals(before, d.driver().text());
            assertEquals("'flow' may not end at anchor 'out' of 'task'", feedback(d).lastBalloon());
            assertFalse(feedback(d).showingRefusal());
        }
    }

    @Test
    public void aPlaceholderCannotBeDeletedOrMoved() {
        try (var d = open("unknown-type.adpsample")) {
            String before = d.driver().text();
            d.driver().select("z").press("DELETE");
            assertEquals("unknown type is kept as it is", d.refusal());
            assertEquals("unknown type is kept as it is", feedback(d).lastBalloon());
            assertEquals(before, d.driver().text());

            d.moveBy(100, 0, "z");
            assertEquals("unknown type is kept as it is", d.refusal());
            assertEquals(before, d.driver().text());
        }
    }

    @Test
    public void escapeDuringADragChangesNothing() {
        try (var d = open("two-tasks.adpsample")) {
            String before = d.driver().text();
            DiagramCanvas canvas = d.designer().canvas();
            var box = d.elementView("a").bounds();

            d.driver().select("a");
            Point from = canvas.toCanvas(new Point2D.Double(box.getCenterX(), box.getCenterY()));
            Point to = new Point(from.x + 100, from.y + 100);
            mouse(canvas, MouseEvent.MOUSE_PRESSED, from, 1, InputEvent.BUTTON1_DOWN_MASK);
            mouse(canvas, MouseEvent.MOUSE_DRAGGED, to, 0, InputEvent.BUTTON1_DOWN_MASK);
            d.driver().press("ESCAPE");
            mouse(canvas, MouseEvent.MOUSE_RELEASED, to, 1, 0);
            d.driver().settle();
            assertEquals("a move", before, d.driver().text());
            assertEquals(box, d.elementView("a").bounds());

            Point anchor = canvas.toCanvas(anchor(d, "b", "out"));
            d.driver().select();
            mouse(canvas, MouseEvent.MOUSE_PRESSED, anchor, 1, InputEvent.BUTTON1_DOWN_MASK);
            mouse(canvas, MouseEvent.MOUSE_DRAGGED, canvas.toCanvas(anchor(d, "a", "in")), 0, InputEvent.BUTTON1_DOWN_MASK);
            d.driver().press("ESCAPE");
            mouse(canvas, MouseEvent.MOUSE_RELEASED, canvas.toCanvas(anchor(d, "a", "in")), 1, 0);
            d.driver().settle();
            assertEquals("a connection", before, d.driver().text());

            assertNull(d.driver().undoLabel());
        }
    }

    private static Point2D anchor(DiagramDriver d, Object key, String id) {
        return d.anchorsOf(key).stream().filter(a -> a.id().equals(id)).findFirst().map(AnchorView::position).orElseThrow();
    }
}
