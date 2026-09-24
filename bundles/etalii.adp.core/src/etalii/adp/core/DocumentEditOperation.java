package etalii.adp.core;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.AbstractOperation;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.TriggeredOperations;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.UndoEdit;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;

/**
 * One labelled change to a document, in the document's own undo context (research R2).
 *
 * <p>
 * {@link #runIn(IOperationHistory)} runs it inside a {@link TriggeredOperations}, so the change the
 * document undo manager records while it applies becomes a child of one history entry that carries
 * this label. {@code TriggeredOperations} only asks its trigger to undo and redo, so this operation
 * does both itself, through the {@link UndoEdit}s, and restores the document's modification stamp
 * each way. The file buffer compares that stamp with its saved one, so undoing every change clears
 * the dirty marker.
 */
public class DocumentEditOperation extends AbstractOperation {

    private final IDocument document;
    private final IDocumentUndoManager undoManager;
    private TextEdit pending;
    private long stampBefore;
    private long stampAfter;

    public DocumentEditOperation(String label, IDocument document, TextEdit edit) {
        super(label);
        this.document = document;
        this.pending = edit;
        this.undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        if (undoManager == null) {
            throw new IllegalStateException("The document has no connected undo manager");
        }
        addContext(undoManager.getUndoContext());
    }

    /** Execute this operation as one entry of {@code history}. */
    public IStatus runIn(IOperationHistory history) {
        history.openOperation(new TriggeredOperations(this, history), IOperationHistory.EXECUTE);
        IStatus status;
        try {
            status = execute(null, null);
        } catch (RuntimeException e) {
            history.closeOperation(false, false, IOperationHistory.EXECUTE);
            throw e;
        }
        history.closeOperation(status.isOK(), status.isOK(), IOperationHistory.EXECUTE);
        return status;
    }

    @Override
    public IStatus execute(IProgressMonitor monitor, IAdaptable info) {
        stampBefore = stamp();
        IStatus status = apply();
        stampAfter = stamp();
        return status;
    }

    @Override
    public IStatus undo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
        IStatus status = apply();
        restoreStamp(stampBefore);
        return status;
    }

    @Override
    public IStatus redo(IProgressMonitor monitor, IAdaptable info) throws ExecutionException {
        IStatus status = apply();
        restoreStamp(stampAfter);
        return status;
    }

    @Override
    public boolean canUndo() {
        return pending != null;
    }

    @Override
    public boolean canRedo() {
        return pending != null;
    }

    /** Apply the pending edit as one compound change, keeping its inverse for the next step. */
    private IStatus apply() {
        undoManager.beginCompoundChange();
        try {
            UndoEdit inverse = pending.apply(document, TextEdit.CREATE_UNDO);
            pending = inverse;
            return Status.OK_STATUS;
        } catch (MalformedTreeException | BadLocationException e) {
            return Status.error(e.getMessage() == null ? "The edit does not fit the document" : e.getMessage(), e);
        } finally {
            undoManager.endCompoundChange();
        }
    }

    private long stamp() {
        return document instanceof IDocumentExtension4 extension ? extension.getModificationStamp()
                : IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP;
    }

    /** An empty replace is the only way to set a document's modification stamp. */
    private void restoreStamp(long stamp) {
        if (document instanceof IDocumentExtension4 extension) {
            try {
                extension.replace(0, 0, "", stamp);
            } catch (BadLocationException e) {
                throw new IllegalStateException("Offset 0 is always valid", e);
            }
        }
    }
}
