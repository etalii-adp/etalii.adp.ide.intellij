package etalii.adp.core.diagram;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.diagram.edit.RefusalFeedback;
import etalii.adp.core.diagram.sample.SampleDefinition;
import etalii.adp.core.diagram.sample.SampleFiles;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.diagram.toolbox.ToolboxTest;
import etalii.adp.core.diagram.view.Handle;
import etalii.adp.testing.DiagramDriver;

/** T054, US2-1 to US2-5 on the sample designer: build and edit a diagram from the toolbox and the canvas. */
@RunWith(JUnit4.class)
public class EditingScenariosTest extends FileEditorManagerTestCase {

    private static final String TWO_TASKS = "two-tasks.adpsample";

    @Override
    public void setUp() {
        super.setUp();
        SampleProvider.register(getTestRootDisposable());
        ToolboxTest.install(getProject(), getTestRootDisposable());
    }

    private DiagramDriver open(String name) {
        return DiagramDriver.open(myFixture, SampleFiles.directory().resolve(name));
    }

    @Test
    public void us2_1_anElementDraggedFromTheToolboxAppearsAtTheDropPointAndTheFileGainsExactlyThatElement() {
        try (var d = open(TWO_TASKS)) {
            assertEquals("only the declared types", SampleDefinition.DEFINITION.toolbox(), d.toolboxEntries());
            String before = d.driver().text();

            d.dragFromToolbox("task", 300, 200);

            assertEquals(List.of("a", "b", "task1"), d.elementKeys());
            assertEquals(300, d.elementView("task1").bounds().x);
            assertEquals(200, d.elementView("task1").bounds().y);
            assertEquals("Task", d.elementView("task1").texts().get("title"));
            assertEquals(before.replace("submit</link>", "submit</link>\n  <box id=\"task1\" type=\"task\" x=\"300\" y=\"200\">Task</box>"),
                    d.driver().text());
        }
    }

    @Test
    public void us2_2_aConnectionFromAnInputAnchorIsRefusedAndTheCanvasShowsWhy() {
        try (var d = open(TWO_TASKS)) {
            String before = d.driver().text();

            d.connect("flow", "b", "in", "a", "out");

            assertEquals("'flow' may not start at anchor 'in' of 'task'", d.refusal());
            assertEquals(before, d.driver().text());
            assertEquals(List.of("f1"), d.connectionKeys());
            assertEquals("the reason is shown", "'flow' may not start at anchor 'in' of 'task'",
                    RefusalFeedback.of(d.designer().canvas()).lastBalloon());
            assertNull(d.driver().undoLabel());
        }
    }

    @Test
    public void us2_3_aRuleRefusingToDeleteTheLastTaskKeepsItAndSaysWhy() {
        try (var d = open(TWO_TASKS)) {
            d.driver().select("a").press("DELETE");
            assertEquals(List.of("b"), d.elementKeys());
            String before = d.driver().text();

            d.driver().select("b").press("DELETE");

            assertEquals(List.of("b"), d.elementKeys());
            assertEquals(before, d.driver().text());
            assertEquals("a diagram needs at least one task", d.refusal());
            assertEquals("a diagram needs at least one task", RefusalFeedback.of(d.designer().canvas()).lastBalloon());
        }
    }

    @Test
    public void us2_4_aHorizontallyResizableElementOffersOnlyHorizontalHandles() {
        try (var d = open("invisible-anchors.adpsample")) {
            d.driver().select("a");
            assertEquals(List.of(Handle.E, Handle.W), d.handlesOf("a"));
            d.driver().select("d");
            assertEquals("a fixed-size decision offers none", List.of(), d.handlesOf("d"));

            String before = d.driver().text();
            d.resize("a", Handle.E, 40, 25);
            int width = d.elementView("a").bounds().width;
            assertTrue("wider: " + d.driver().text(), d.driver().text().contains("w=\"" + width + "\""));
            assertFalse("the height is not written", d.driver().text().contains(" h=\""));
            assertEquals("Undo Resize", d.driver().undoLabel());
            d.driver().undo();
            assertEquals(before, d.driver().text());
        }
    }

    @Test
    public void us2_5_undoingAddConnectMoveAndDeleteGivesTheFileByteForByte() throws Exception {
        try (var d = open(TWO_TASKS)) {
            byte[] opened = java.nio.file.Files.readAllBytes(SampleFiles.directory().resolve(TWO_TASKS));

            d.dragFromToolbox("task", 540, 40);
            assertEquals("Undo Add Task", d.driver().undoLabel());
            d.driver().select();
            d.connect("b", "out", "task1", "in");
            assertEquals("Undo Connect Flow", d.driver().undoLabel());
            assertEquals(List.of("f1", "flow1"), d.connectionKeys());
            d.moveBy(0, 100, "a");
            assertEquals("Undo Move", d.driver().undoLabel());
            d.driver().select("a").press("DELETE");
            assertEquals("Undo Delete", d.driver().undoLabel());
            assertEquals(List.of("b", "task1"), d.elementKeys());
            assertEquals("a's flow went with it", List.of("flow1"), d.connectionKeys());
            assertTrue(d.driver().isModified());

            for (int i = 0; i < 4; i++) {
                d.driver().undo();
            }

            assertEquals(new String(opened, UTF_8), d.driver().text());
            assertNull("nothing more to undo", d.driver().undoLabel());
            assertFalse(d.driver().isModified());
            assertArrayEquals(opened, d.driver().savedBytes());
        }
    }
}
