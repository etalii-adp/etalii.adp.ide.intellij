package etalii.adp.core.diagram.edit;

import static etalii.adp.core.diagram.edit.SelectionTest.drag;

import java.awt.Point;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.diagram.sample.SampleFiles;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.diagram.toolbox.ToolboxTest;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.Handle;
import etalii.adp.testing.DiagramDriver;

/**
 * T057, FR-020 and SC-004: every gesture is one undo step with its contract label, redoes, and
 * marks the file modified; a read-only file blocks every gesture; an external change keeps the
 * selection that still exists; an element's connections are deleted with it in the same step.
 */
@RunWith(JUnit4.class)
public class EditUndoTest extends FileEditorManagerTestCase {

    private static final String TWO_TASKS = "two-tasks.adpsample";

    @Override
    public void setUp() {
        super.setUp();
        SampleProvider.register(getTestRootDisposable());
        ToolboxTest.install(getProject(), getTestRootDisposable());
    }

    private DiagramDriver open() {
        return DiagramDriver.open(myFixture, SampleFiles.directory().resolve(TWO_TASKS));
    }

    /** One gesture: one step labelled {@code label}, undone to the opened text and redone to the edited one. */
    private void assertOneStep(String label, Consumer<DiagramDriver> gesture) {
        try (var d = open()) {
            String opened = d.driver().text();
            gesture.accept(d);
            String edited = d.driver().text();
            assertFalse(label + " changed the file", opened.equals(edited));
            assertEquals("Undo " + label, d.driver().undoLabel());
            assertTrue(d.driver().isModified());

            d.driver().undo();
            assertEquals(opened, d.driver().text());
            assertNull("one step", d.driver().undoLabel());
            assertFalse(d.driver().isModified());
            assertEquals("Redo " + label, d.driver().redoLabel());

            d.driver().redo();
            assertEquals(edited, d.driver().text());
            assertEquals("Undo " + label, d.driver().undoLabel());
        }
    }

    @Test
    public void addFromTheToolbox() {
        assertOneStep("Add Task", d -> d.dragFromToolbox("task", 300, 200));
    }

    @Test
    public void addWithTheKeyboard() {
        assertOneStep("Add Decision", d -> d.addFromToolboxWithKeyboard("decision"));
    }

    @Test
    public void connect() {
        assertOneStep("Connect Note", d -> d.connect("note", "a", "bottom", "b", "top"));
    }

    @Test
    public void reconnect() {
        assertOneStep("Reconnect Flow", d -> {
            d.driver().select("f1");
            DiagramCanvas canvas = d.designer().canvas();
            List<Point> route = d.connectionView("f1").route();
            Point end = route.get(route.size() - 1);
            java.awt.geom.Point2D to = d.anchorsOf("a").stream().filter(a -> a.id().equals("in")).findFirst().orElseThrow().position();
            drag(canvas, canvas.toCanvas(new java.awt.geom.Point2D.Double(end.x, end.y)), canvas.toCanvas(to), 0);
            d.driver().settle();
            assertTrue(d.driver().text(), d.driver().text().contains("to=\"a\" toAnchor=\"in\""));
            assertEquals(List.of("f1"), d.driver().selectedKeys());
        });
    }

    @Test
    public void moveOne() {
        assertOneStep("Move", d -> {
            d.moveBy(33, 97, "a");
            assertTrue(d.driver().text(), d.driver().text().contains("<box id=\"a\" type=\"task\" x=\"70\" y=\"140\""));
        });
    }

    @Test
    public void moveMany() {
        assertOneStep("Move", d -> {
            d.moveBy(0, 200, "a", "b");
            assertTrue(d.driver().text().contains("<box id=\"a\" type=\"task\" x=\"40\" y=\"240\""));
            assertTrue(d.driver().text().contains("<box id=\"b\" type=\"task\" x=\"240\" y=\"240\""));
            assertEquals(List.of("a", "b"), d.driver().selectedKeys());
        });
    }

    @Test
    public void resize() {
        assertOneStep("Resize", d -> d.resize("a", Handle.W, -20, 0));
    }

    @Test
    public void deleteTakesTheElementsConnectionsInTheSameStep() {
        assertOneStep("Delete", d -> {
            d.driver().select("a").press("DELETE");
            assertEquals(List.of("b"), d.elementKeys());
            assertEquals(List.of(), d.connectionKeys());
        });
    }

    @Test
    public void deleteAConnection() {
        assertOneStep("Delete", d -> {
            d.driver().select("f1").run("etalii.adp.core.Delete");
            assertEquals(List.of(), d.connectionKeys());
        });
    }

    @Test
    public void deleteIsEnabledOnlyWithSomethingSelectedAndTheCanvasFocused() {
        try (var d = open()) {
            assertFalse(d.driver().presentation("etalii.adp.core.Delete").isEnabled());
            d.driver().select("a");
            assertTrue(d.driver().presentation("etalii.adp.core.Delete").isEnabled());
        }
    }

    @Test
    public void aReadOnlyFileBlocksEveryGesture() {
        try (var d = open()) {
            String opened = d.driver().text();
            d.driver().setReadOnly(true);

            d.dragFromToolbox("task", 300, 200);
            d.addFromToolboxWithKeyboard("decision");
            d.connect("note", "a", "bottom", "b", "top");
            d.moveBy(100, 0, "a");
            d.resize("a", Handle.E, 40, 0);
            d.driver().select("a").press("DELETE");
            d.driver().select("f1").run("etalii.adp.core.Delete");

            assertEquals(opened, d.driver().text());
            assertNull(d.driver().undoLabel());
            assertFalse(d.driver().isModified());
            d.driver().setReadOnly(false);
        }
    }

    @Test
    public void anExternalChangeKeepsTheSelectionOfItemsThatStillExist() {
        try (var d = open()) {
            d.driver().select("a", "b", "f1");
            String changed = d.driver().text().replaceAll("\\s*<box id=\"b\"[^\\n]*</box>", "").replaceAll("\\s*<link id=\"f1\"[^\\n]*</link>", "");
            d.driver().changeOnDisk(changed);
            assertEquals(changed, d.driver().text());
            assertEquals(List.of("a"), d.elementKeys());
            assertEquals(List.of("a"), d.driver().selectedKeys());
        }
    }
}
