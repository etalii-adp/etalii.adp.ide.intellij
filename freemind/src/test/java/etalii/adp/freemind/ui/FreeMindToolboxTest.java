package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.TextVisualSyncTest.node;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.RegisterToolWindowTask;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.toolbox.ToolboxToolWindowFactory;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.ui.FreeMindMapping.ArrowKey;
import etalii.adp.freemind.ui.FreeMindMapping.BranchKey;
import etalii.adp.testing.DiagramDriver;

/** T088: the ADP Toolbox on a FreeMind map adds child nodes and draws arrow links; branches follow the tree. */
@RunWith(JUnit4.class)
public class FreeMindToolboxTest extends FileEditorManagerTestCase {

    static final String MAP = TextVisualSyncTest.MAP;

    @Override
    public void setUp() {
        super.setUp();
        install(getProject(), getTestRootDisposable());
    }

    /** Register ADP Toolbox and fill it with its factory, as the IDE does from the descriptor; the headless tool window manager does not. */
    static ToolWindow install(Project project, Disposable disposable) {
        ToolWindowManager manager = ToolWindowManager.getInstance(project);
        ToolWindow window = manager.registerToolWindow(RegisterToolWindowTask.notClosable(ToolboxToolWindowFactory.ID, ToolWindowAnchor.RIGHT));
        new ToolboxToolWindowFactory().createToolWindowContent(project, window);
        Disposer.register(disposable, () -> {
            window.getContentManager().removeAllContents(true);
            manager.unregisterToolWindow(ToolboxToolWindowFactory.ID);
        });
        return window;
    }

    private DiagramDriver open() {
        return DiagramDriver.openText(myFixture, "toolbox.mm", MAP);
    }

    @Test
    public void theToolboxListsNodesAndArrowLinks() {
        try (var d = open()) {
            assertEquals(List.of(FreeMindDefinition.NODE, FreeMindDefinition.ARROW_LINK), d.toolboxEntries());
        }
    }

    @Test
    public void aNodeDroppedOntoANodeAddsAChild() {
        try (var d = open()) {
            d.dragFromToolboxOnto(FreeMindDefinition.NODE, key("B"));
            List<MapNode> children = node(d.driver(), key("B")).children();
            assertEquals(1, children.size());
            assertEquals("New Node", children.get(0).text());
            assertEquals("Undo Add Child Node", d.driver().undoLabel());
            assertEquals("the new node is selected", List.of(children.get(0).key()), d.driver().selectedKeys());
            d.driver().undo();
            assertEquals(MAP, d.driver().text());
        }
    }

    @Test
    public void aNodeDroppedOnEmptyCanvasIsRefused() {
        try (var d = open()) {
            d.dragFromToolbox(FreeMindDefinition.NODE, 2, 2);
            assertEquals(FreeMindDefinition.NEEDS_PARENT, d.refusal());
            assertEquals("a node needs a parent", d.refusal());
            assertEquals(MAP, d.driver().text());
            assertFalse(d.driver().isModified());
        }
    }

    @Test
    public void anArrowLinkConnectsTwoNodes() {
        try (var d = open()) {
            d.connect(FreeMindDefinition.ARROW_LINK, key("B"), FreeMindDefinition.RIGHT, key("L"), FreeMindDefinition.LEFT);
            assertTrue(d.driver().text(), d.driver().text().contains("<arrowlink DESTINATION=\"L\" ENDARROW=\"Default\" ID=\"Arrow_ID_"));
            assertEquals("Undo Connect Arrow Link", d.driver().undoLabel());
            assertNotNull("the link is drawn", d.connectionView(new ArrowKey(key("B"), 0)));
            d.driver().undo();
            assertEquals(MAP, d.driver().text());
        }
    }

    @Test
    public void aBranchIsRemovedOnlyWithItsNode() {
        try (var d = open()) {
            assertNotNull("branches are drawn from the tree", d.connectionView(new BranchKey(key("B"))));
            Verdict verdict = d.designer().commands().remove(List.of(new BranchKey(key("B"))));
            assertFalse(verdict.allowed());
            assertEquals("remove the node to remove its branch", verdict.reason());
            assertEquals(FreeMindDefinition.BRANCH_GOES_WITH_NODE, d.refusal());
            assertEquals(MAP, d.driver().text());
        }
    }
}
