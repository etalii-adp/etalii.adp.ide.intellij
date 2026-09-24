package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.ADD_CHILD;
import static etalii.adp.freemind.ui.AddNodeTest.ADD_SIBLING;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.AddNodeTest.inPlaceEditor;
import static etalii.adp.freemind.ui.DeleteTest.DELETE;
import static etalii.adp.freemind.ui.FoldTest.TOGGLE_FOLD;
import static etalii.adp.freemind.ui.MoveNodeTest.INDENT;
import static etalii.adp.freemind.ui.MoveNodeTest.MOVE_DOWN;
import static etalii.adp.freemind.ui.MoveNodeTest.MOVE_UP;
import static etalii.adp.freemind.ui.MoveNodeTest.OUTDENT;
import static etalii.adp.freemind.ui.RenameTest.RENAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.gef.EditPart;
import org.eclipse.gef.Request;
import org.eclipse.gef.RequestConstants;
import org.junit.jupiter.api.Test;

import etalii.adp.testing.DesignerDriver;

/** FR-008: a read-only file is shown with the reason, every edit command is disabled, and folding is view-only. */
class ReadOnlyTest {

    static final List<String> EDIT_COMMANDS = List.of(ADD_CHILD, ADD_SIBLING, RENAME, DELETE, MOVE_UP, MOVE_DOWN, INDENT, OUTDENT);

    @Test
    void everyEditCommandIsDisabled() {
        try (var d = DesignerDriver.openText("readonly.mm", MAP, MindMapEditor.ID)) {
            assertNull(d.editor().readOnlyMessage());
            d.setReadOnly(true);
            assertNotNull(d.editor().readOnlyMessage());
            assertTrue(d.editor().readOnlyMessage().contains("read-only"), d.editor().readOnlyMessage());
            assertFalse(d.editor().isEditable());

            // A2 has siblings on both sides and a grandparent: on a writable file every command applies.
            for (String command : EDIT_COMMANDS) {
                d.select(key("A2"));
                assertThrows(IllegalStateException.class, () -> d.run(command), command);
                assertNull(inPlaceEditor(d), command);
            }
            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());
            assertNull(d.undoLabel());
        }
    }

    @Test
    void doubleClickDoesNotOpenAnEditor() {
        try (var d = DesignerDriver.openText("readonly.mm", MAP, MindMapEditor.ID)) {
            d.setReadOnly(true);
            d.select(key("B"));
            EditPart part = d.editor().viewer().getEditPartRegistry().get(key("B"));
            part.performRequest(new Request(RequestConstants.REQ_OPEN));
            d.settle();
            assertNull(inPlaceEditor(d));
            assertEquals(MAP, d.text());
        }
    }

    @Test
    void foldingIsViewOnly() {
        try (var d = DesignerDriver.openText("readonly.mm", MAP, MindMapEditor.ID)) {
            d.setReadOnly(true);
            d.select(key("A")).run(TOGGLE_FOLD);
            assertNull(d.figureOf(key("A1")));
            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());
            assertNull(d.undoLabel());
        }
    }

    @Test
    void theSameCommandsRunOnceWritableAgain() {
        try (var d = DesignerDriver.openText("readonly.mm", MAP, MindMapEditor.ID)) {
            d.setReadOnly(true).setReadOnly(false);
            assertNull(d.editor().readOnlyMessage());
            d.select(key("A2")).run(DELETE);
            assertEquals("Delete Node", d.undoLabel());
        }
    }
}
