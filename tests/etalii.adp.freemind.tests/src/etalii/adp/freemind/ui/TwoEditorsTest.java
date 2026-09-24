package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.AddNodeTest.node;
import static etalii.adp.freemind.ui.DeleteTest.DELETE;
import static etalii.adp.freemind.ui.RenameTest.RENAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.FileEditorInput;
import org.junit.jupiter.api.Test;

import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.ui.figures.NodeFigure;
import etalii.adp.testing.DesignerDriver;

/** FR-006 and the "same file in two editors" edge case: one document, one history. */
class TwoEditorsTest {

    @Test
    void anEditInOneEditorShowsInTheOtherAndTheyShareOneUndoHistory() throws Exception {
        try (var d = DesignerDriver.openText("twice.mm", MAP, MindMapEditor.ID)) {
            IWorkbenchPage page = d.editor().getSite().getPage();
            IEditorPart opened = page.openEditor(new FileEditorInput(d.file()), MindMapEditor.ID, true, IWorkbenchPage.MATCH_NONE);
            settle();
            try {
                assertTrue(opened instanceof MindMapEditor, String.valueOf(opened));
                MindMapEditor second = (MindMapEditor) opened;
                assertNotSame(d.editor(), second, "a second editor, not the first one reused");
                assertSame(d.editor().document(), second.document(), "both edit the one shared document");

                // An edit in the first editor shows in the second.
                d.select(key("B")).run(RENAME).typeInPlace("From the first");
                String afterRename = d.text();
                assertEquals(afterRename, second.document().get());
                assertEquals("From the first", nodeText(second, key("B")));
                assertEquals("From the first", ((NodeFigure) figureOf(second, key("B"))).text());
                assertTrue(second.isDirty());

                // An edit in the second editor shows in the first.
                page.activate(second);
                settle();
                second.viewer().setSelection(new StructuredSelection(partOf(second, key("L"))));
                settle();
                execute(second, DELETE, null);
                settle();
                String afterDelete = second.document().get();
                assertFalse(afterDelete.contains("ID=\"L\""), afterDelete);
                assertEquals(afterDelete, d.text());
                assertNull(d.figureOf(key("L")), "the first editor erases the node deleted in the second");
                assertNull(figureOf(second, key("L")));

                // One undo history: undo in the first reverts the second's edit in both.
                d.undo();
                assertEquals(afterRename, d.text());
                assertEquals(afterRename, second.document().get());
                assertNotNull(figureOf(second, key("L")));
                assertNotNull(d.figureOf(key("L")));

                // And undo in the second reverts the first's edit in both.
                page.activate(second);
                settle();
                execute(second, "org.eclipse.ui.edit.undo", ActionFactory.UNDO.getId());
                settle();
                assertEquals(MAP, second.document().get());
                assertEquals(MAP, d.text());
                assertEquals("B", ((NodeFigure) d.figureOf(key("B"))).text());
                assertEquals("B", ((NodeFigure) figureOf(second, key("B"))).text());
                assertFalse(d.isDirty());
                assertFalse(second.isDirty());
            } finally {
                page.closeEditor(opened, false);
                settle();
            }
            assertEquals("B", node(d, key("B")).text(), "the first editor keeps working after the second closes");
        }
    }

    /**
     * Run a command in this editor through the handler service; without the operating system's
     * focus the workbench may not enable undo's handler, so the editor's global action runs instead,
     * as DesignerDriver does.
     */
    private static void execute(MindMapEditor editor, String commandId, String globalActionId) throws Exception {
        try {
            editor.getSite().getService(IHandlerService.class).executeCommand(commandId, null);
        } catch (NotEnabledException | NotHandledException e) {
            IAction action = globalActionId == null ? null : editor.getEditorSite().getActionBars().getGlobalActionHandler(globalActionId);
            if (action == null || !action.isEnabled()) {
                throw e;
            }
            action.run();
        }
    }

    private static String nodeText(MindMapEditor editor, NodeKey key) {
        MindMap map = editor.model();
        assertNotNull(map);
        return map.node(key).text();
    }

    private static Object figureOf(MindMapEditor editor, NodeKey key) {
        EditPart part = editor.viewer().getEditPartRegistry().get(key);
        return part instanceof GraphicalEditPart graphical ? graphical.getFigure() : null;
    }

    private static GraphicalEditPart partOf(MindMapEditor editor, NodeKey key) {
        EditPart part = editor.viewer().getEditPartRegistry().get(key);
        assertTrue(part instanceof GraphicalEditPart, "nothing drawn for " + key);
        return (GraphicalEditPart) part;
    }

    private static void settle() {
        Display display = Display.getCurrent();
        for (int round = 0; round < 3; round++) {
            while (display.readAndDispatch()) {
                // keep dispatching
            }
        }
    }
}
