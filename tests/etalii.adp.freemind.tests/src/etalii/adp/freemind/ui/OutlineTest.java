package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.example;
import static etalii.adp.freemind.MindMapAsserts.key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.views.contentoutline.ContentOutline;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.testing.DesignerDriver;

/** US4: the standard Outline lists the node tree, with selection linked both ways (FR-026, FR-027). */
class OutlineTest {

    private static final String FOLDED_MAP = """
            <map version="1.0.1">
            <node CREATED="1" ID="ID_1" MODIFIED="1" TEXT="Root">
            <node CREATED="2" FOLDED="true" ID="ID_2" MODIFIED="2" POSITION="right" TEXT="Folded">
            <node CREATED="3" ID="ID_3" MODIFIED="3" TEXT="Hidden">
            <node CREATED="4" ID="ID_4" MODIFIED="4" TEXT="Deep"/>
            </node>
            </node>
            <node CREATED="5" ID="ID_5" MODIFIED="5" POSITION="left" TEXT="Open"/>
            </node>
            </map>
            """;

    @AfterEach
    void hideOutline() {
        IWorkbenchPage page = PlatformUI.getWorkbench().getWorkbenchWindows()[0].getActivePage();
        if (page.findView(IPageLayout.ID_OUTLINE) != null) {
            page.hideView(page.findView(IPageLayout.ID_OUTLINE));
        }
    }

    @Test
    void listsTheNodeTreeOfALargeMap() throws Exception {
        try (var d = DesignerDriver.open(example("freeplane-large-map.mm"), MindMapEditor.ID)) {
            TreeViewer tree = outline(d).getTreeViewer();

            MindMap map = (MindMap) d.editor().model();
            assertEquals(List.of(map.root().key()), topLevel(tree));
            assertEquals(map.nodesByKey().size(), allKeys(tree).size());
            assertEquals(map.root().text(), tree.getTree().getItem(0).getText());
        }
    }

    @Test
    void followsEdits() throws Exception {
        try (var d = DesignerDriver.openText("outline.mm", FOLDED_MAP, MindMapEditor.ID)) {
            TreeViewer tree = outline(d).getTreeViewer();
            MindMapEdits.Edit add = MindMapEdits.addChild((MindMap) d.editor().model(), key("ID_5"), "Added");

            d.editor().execute(add.label(), add.textEdit());
            d.settle();

            assertTrue(allKeys(tree).contains(add.created()));
        }
    }

    @Test
    void selectingInTheOutlineRevealsTheNodeWithoutAnEdit() throws Exception {
        try (var d = DesignerDriver.openText("outline.mm", FOLDED_MAP, MindMapEditor.ID)) {
            MindMapOutlinePage outline = outline(d);
            assertNull(d.figureOf(key("ID_4")), "hidden inside a folded branch");

            outline.getTreeViewer().setSelection(new StructuredSelection(key("ID_4")), true);
            d.settle();

            assertNotNull(d.figureOf(key("ID_4")));
            assertEquals(List.of(key("ID_4")), d.selectedModels());
            assertFalse(d.isDirty());
            assertEquals(FOLDED_MAP, d.text());
        }
    }

    @Test
    void selectingInTheDesignerSelectsInTheOutline() throws Exception {
        try (var d = DesignerDriver.openText("outline.mm", FOLDED_MAP, MindMapEditor.ID)) {
            MindMapOutlinePage outline = outline(d);

            d.select(key("ID_5"));

            assertEquals(List.of(key("ID_5")), ((IStructuredSelection) outline.getTreeViewer().getSelection()).toList());
        }
    }

    /** Shows the Outline view, then gives the editor focus back, as a person would. */
    private static MindMapOutlinePage outline(DesignerDriver d) throws PartInitException {
        IWorkbenchPage page = d.editor().getSite().getPage();
        ContentOutline view = (ContentOutline) page.showView(IPageLayout.ID_OUTLINE);
        page.activate(d.editor());
        d.settle();
        IContentOutlinePage current = (IContentOutlinePage) view.getCurrentPage();
        return (MindMapOutlinePage) current;
    }

    private static List<NodeKey> topLevel(TreeViewer tree) {
        List<NodeKey> keys = new ArrayList<>();
        for (TreeItem item : tree.getTree().getItems()) {
            keys.add((NodeKey) item.getData());
        }
        return keys;
    }

    private static List<NodeKey> allKeys(TreeViewer tree) {
        tree.expandAll();
        List<NodeKey> keys = new ArrayList<>();
        collect(tree.getTree().getItems(), keys);
        return keys;
    }

    private static void collect(TreeItem[] items, List<NodeKey> keys) {
        for (TreeItem item : items) {
            keys.add((NodeKey) item.getData());
            collect(item.getItems(), keys);
        }
    }
}
