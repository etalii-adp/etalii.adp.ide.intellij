package etalii.adp.core.diagram.toolbox;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.RegisterToolWindowTask;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.diagram.sample.SampleDefinition;
import etalii.adp.core.diagram.sample.SampleFiles;
import etalii.adp.core.diagram.sample.SampleMapping;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramEditorProvider;
import etalii.adp.core.diagram.view.ElementView;
import etalii.adp.testing.ToolDriver;
import etalii.adp.testing.DiagramDriver;
import etalii.adp.testing.Layout;

/**
 * T053, FR-016: the ADP Toolbox tool window lists the selected diagram's toolbox, follows the
 * selected editor, drops an element where the drag ends on the diagram side, and adds one at the
 * centre of the visible canvas on Enter. It proves the tool window and drag and drop seams.
 */
@RunWith(JUnit4.class)
public class ToolboxTest extends FileEditorManagerTestCase {

    @Override
    public void setUp() {
        super.setUp();
        SampleProvider.register(getTestRootDisposable());
        install(getProject(), getTestRootDisposable());
    }

    /**
     * Register ADP Toolbox and fill it with its factory, as the IDE does from the descriptor. The
     * headless tool window manager does not create tool windows from descriptors.
     */
    public static ToolWindow install(Project project, Disposable disposable) {
        ToolWindowManager manager = ToolWindowManager.getInstance(project);
        ToolWindow window = manager.registerToolWindow(RegisterToolWindowTask.notClosable(ToolboxToolWindowFactory.ID, ToolWindowAnchor.RIGHT));
        new ToolboxToolWindowFactory().createToolWindowContent(project, window);
        Disposer.register(disposable, () -> {
            window.getContentManager().removeAllContents(true);
            manager.unregisterToolWindow(ToolboxToolWindowFactory.ID);
        });
        return window;
    }

    private DiagramDriver open(String name) {
        return DiagramDriver.open(myFixture, SampleFiles.directory().resolve(name));
    }

    private ToolboxPanel panel() {
        ToolWindow window = ToolWindowManager.getInstance(getProject()).getToolWindow(ToolboxToolWindowFactory.ID);
        for (Content content : window.getContentManager().getContents()) {
            if (content.getComponent() instanceof ToolboxPanel panel) {
                return panel;
            }
        }
        throw new AssertionError("ADP Toolbox shows no toolbox panel");
    }

    @Test
    public void listsExactlyTheDeclaredEntriesInToolboxOrder() {
        try (var d = open("two-tasks.adpsample")) {
            assertEquals(List.of("task", "decision", "flow", "note"), d.toolboxEntries());
            assertEquals(SampleDefinition.DEFINITION.toolbox(), panel().entries());
            assertFalse(panel().emptyStateShown());
            for (String entry : panel().entries()) {
                assertNotNull("every entry has an icon: " + entry, panel().iconOf(entry));
            }
            assertEquals("Elements", panel().groupOf("task"));
            assertEquals("Elements", panel().groupOf("decision"));
            assertEquals("Connections", panel().groupOf("flow"));
            assertEquals("Connections", panel().groupOf("note"));
        }
    }

    @Test
    public void followsTheSelectedEditorIncludingANonAdpFileAndAnotherTool() {
        assertEquals(List.of(), panel().entries());
        assertTrue("empty before any diagram is open", panel().emptyStateShown());
        assertEquals("Open an ADP diagram to see its toolbox", ToolboxPanel.EMPTY_TEXT);

        OtherProvider.register(getTestRootDisposable());
        try (var d = open("two-tasks.adpsample")) {
            assertEquals(List.of("task", "decision", "flow", "note"), panel().entries());

            VirtualFile notes = ToolDriver.createFile(myFixture, "notes.txt", "plain text\n".getBytes(UTF_8));
            FileEditorManager editors = FileEditorManager.getInstance(getProject());
            editors.openFile(notes, true);
            d.driver().settleUntil("the toolbox leaves the diagram for a text file", () -> panel().tool() == null);
            assertEquals(List.of(), panel().entries());
            assertTrue("empty after switching to a non-ADP file", panel().emptyStateShown());

            editors.openFile(d.driver().file(), true);
            d.driver().settleUntil("the toolbox follows back to the sample diagram", () -> panel().tool() == d.tool());
            assertEquals(List.of("task", "decision", "flow", "note"), panel().entries());
            assertSame(d.tool(), panel().tool());

            try (var other = DiagramDriver.openText(myFixture, "other.adpother", SampleFiles.read("two-tasks.adpsample"))) {
                assertNotNull(other.tool());
                other.driver().settleUntil("the toolbox follows the other diagram", () -> panel().tool() == other.tool());
                assertEquals(List.of("decision", "task"), panel().entries());
                assertSame(other.tool(), panel().tool());
            }
            editors.closeFile(notes);
        }
        panel().refreshFromSelectedEditor();
        assertTrue("empty once every diagram is closed", panel().emptyStateShown());
    }

    @Test
    public void aDragLandsOnThePreviewSideAtTheDropPoint() {
        try (var d = open("two-tasks.adpsample")) {
            d.driver().showLayout(Layout.SPLIT);
            d.tool().zoomIn();
            String before = d.driver().text();
            DiagramCanvas canvas = d.tool().canvas();
            assertTrue("the canvas is the preview side", SwingUtilities.isDescendingFrom(canvas, d.driver().composite().getPreviewEditor().getComponent()));

            d.dragFromToolbox("decision", 403, 197);

            ElementView added = d.elementView("decision1");
            assertNotNull("a decision was added: " + d.elementKeys(), added);
            assertEquals("at the drop point, snapped to the grid", new Rectangle2D.Double(400, 200, JBUI.scale(100), JBUI.scale(60)), added.bounds());
            assertEquals(before.replace("submit</link>", "submit</link>\n  <box id=\"decision1\" type=\"decision\" x=\"400\" y=\"200\" w=\"100\" h=\"60\">?</box>"),
                    d.driver().text());
            assertEquals("Undo Add Decision", d.driver().undoLabel());
            assertEquals(List.of("decision1"), d.driver().selectedKeys());
            d.driver().undo();
            assertEquals(before, d.driver().text());
        }
    }

    @Test
    public void enterAddsAnElementAtTheCentreOfTheVisibleCanvas() {
        try (var d = open("two-tasks.adpsample")) {
            DiagramCanvas canvas = d.tool().canvas();
            canvas.setSize(800, 600);
            Rectangle2D visible = canvas.visibleArea();

            d.addFromToolboxWithKeyboard("decision");

            ElementView added = d.elementView("decision1");
            assertNotNull(added);
            Point2D centre = new Point2D.Double(visible.getCenterX(), visible.getCenterY());
            assertEquals("centred: " + added.bounds() + " in " + visible, centre.getX(), added.bounds().getCenterX(), 5);
            assertEquals("centred: " + added.bounds() + " in " + visible, centre.getY(), added.bounds().getCenterY(), 5);
            assertEquals("snapped to the grid", 0, added.bounds().x % 10);
            assertEquals("Undo Add Decision", d.driver().undoLabel());
        }
    }

    @Test
    public void aConnectionEntryArmsItsTypeOnTheTool() {
        try (var d = open("two-tasks.adpsample")) {
            String before = d.driver().text();
            assertNull(d.tool().armedConnectionType());
            d.addFromToolboxWithKeyboard("note");
            assertEquals("note", d.tool().armedConnectionType());
            d.addFromToolboxWithKeyboard("flow");
            assertEquals("flow", d.tool().armedConnectionType());
            assertEquals("arming changes nothing in the file", before, d.driver().text());
        }
    }

    /** A second diagram with its own toolbox, for following the selected editor from one diagram to another. */
    public static final class OtherProvider extends DiagramEditorProvider {

        OtherProvider() {
            super(SampleDefinition.builder().toolbox("decision", "task"), SampleMapping::new);
        }

        static void register(Disposable disposable) {
            FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new OtherProvider(), disposable);
        }

        @Override
        protected Set<String> extensions() {
            return Set.of("adpother");
        }

        @Override
        protected boolean sniff(byte[] head) {
            return new String(head, UTF_8).contains("<sample");
        }

        @Override
        protected String toolName() {
            return "Other Sample Diagram";
        }

        @Override
        public String getEditorTypeId() {
            return "etalii.adp.sample.other";
        }
    }
}
