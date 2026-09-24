package etalii.adp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.OperationHistoryFactory;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The undo bridge (research R2): the plan's first proof, gating all editor code. */
class DocumentEditOperationTest {

    private final IOperationHistory history = OperationHistoryFactory.getOperationHistory();
    private Document document;
    private IDocumentUndoManager undoManager;
    private IUndoContext context;

    @BeforeEach
    void connect() {
        document = new Document("<map>\n</map>\n");
        DocumentUndoManagerRegistry.connect(document);
        undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        undoManager.connect(this);
        context = undoManager.getUndoContext();
    }

    @AfterEach
    void disconnect() {
        history.dispose(context, true, true, true);
        undoManager.disconnect(this);
        DocumentUndoManagerRegistry.disconnect(document);
    }

    @Test
    void oneLabelledEntryThatUndoesAndRedoesInOneStep() throws Exception {
        String original = document.get();
        long stamp = document.getModificationStamp();

        IStatus status = new DocumentEditOperation("Add Child Node", document, twoInsertions()).runIn(history);

        assertTrue(status.isOK(), status.toString());
        String edited = document.get();
        assertEquals("<!-- c -->\n<map>\n<node TEXT=\"a\"/>\n</map>\n", edited);
        assertEquals(1, history.getUndoHistory(context).length);
        assertEquals("Add Child Node", history.getUndoOperation(context).getLabel());

        history.undo(context, null, null);
        assertEquals(original, document.get());
        assertEquals(stamp, document.getModificationStamp());
        assertEquals(0, history.getUndoHistory(context).length);
        assertEquals("Add Child Node", history.getRedoOperation(context).getLabel());

        history.redo(context, null, null);
        assertEquals(edited, document.get());
        assertEquals(1, history.getUndoHistory(context).length);
        assertEquals("Add Child Node", history.getUndoOperation(context).getLabel());

        history.undo(context, null, null);
        assertEquals(original, document.get());
        assertEquals(stamp, document.getModificationStamp());
    }

    @Test
    void typingAfterAnEditIsItsOwnEntry() throws Exception {
        new DocumentEditOperation("Add Child Node", document, twoInsertions()).runIn(history);
        String edited = document.get();

        document.replace(0, 0, "x");

        assertEquals(2, history.getUndoHistory(context).length);
        history.undo(context, null, null);
        assertEquals(edited, document.get());
        assertEquals("Add Child Node", history.getUndoOperation(context).getLabel());
    }

    @Test
    void aBadEditLeavesTheDocumentUnchanged() throws Exception {
        String original = document.get();

        IStatus status = new DocumentEditOperation("Broken", document, new InsertEdit(1000, "x")).runIn(history);

        assertFalse(status.isOK());
        assertEquals(original, document.get());
        assertEquals(0, history.getUndoHistory(context).length);
    }

    private static MultiTextEdit twoInsertions() {
        MultiTextEdit edit = new MultiTextEdit();
        edit.addChild(new InsertEdit(6, "<node TEXT=\"a\"/>\n"));
        edit.addChild(new InsertEdit(0, "<!-- c -->\n"));
        return edit;
    }
}
