package etalii.adp.fbl.history;

import java.security.MessageDigest;
import java.util.Objects;

import etalii.adp.fbl.Edit;

/**
 * A file written only by splices, with the one history of its edits (FBL §7.1): a body, or a
 * registration. Applying an edit applies its splices and reads the file again. Undo and redo
 * check for drift first and refuse with FBL §7.2's sentence, writing nothing (FBL §7.2).
 */
public abstract class SplicedFile {

    /** FBL §7.2's sentence for an undo refused because the file changed underneath it. */
    public static final String DRIFT_UNDO = "The file has changed since this edit, so it cannot be undone.";

    public static final String DRIFT_REDO = "The file has changed since this edit was undone, so it cannot be redone.";

    private final EditHistory history = new EditHistory();
    private byte[] bytes;

    protected SplicedFile(byte[] bytes) {
        this.bytes = bytes;
    }

    /** The file's bytes after every applied edit. */
    public final byte[] bytes() {
        return bytes;
    }

    public final boolean canUndo() {
        return history.canUndo();
    }

    public final boolean canRedo() {
        return history.canRedo();
    }

    /** Applies an edit planned against the current bytes, records it, and reads the file again. */
    public final void apply(Edit edit) {
        Objects.requireNonNull(edit, "edit");
        if (edit.splices().isEmpty()) {
            return;
        }
        byte[] before = bytes;
        byte[] after = Edit.apply(before, edit.splices());
        history.record(before, after, edit);
        replace(after);
    }

    /** Undoes the most recent edit, the file being as this object has it. */
    public final UndoResult undo() {
        return undo(null);
    }

    /**
     * Undoes the most recent edit. {@code current} is the file as it is now on disk or in another
     * editor's buffer, or null for the bytes this object has; when it differs from what the
     * history expects, the undo is refused and nothing is written.
     */
    public final UndoResult undo(byte[] current) {
        if (!history.canUndo()) {
            return new UndoResult.Refused("There is nothing to undo.");
        }
        HistoryEntry entry = history.peekUndo();
        if (!matches(current != null ? current : bytes, entry.afterDigest())) {
            return new UndoResult.Refused(DRIFT_UNDO);
        }
        history.undone();
        replace(entry.snapshot() != null ? entry.snapshot() : Edit.apply(bytes, entry.inverse()));
        return new UndoResult.Done(entry.inverse());
    }

    /** Redoes the most recently undone edit, the file being as this object has it. */
    public final UndoResult redo() {
        return redo(null);
    }

    /** Redoes the most recently undone edit, refused on drift as {@link #undo(byte[])} is. */
    public final UndoResult redo(byte[] current) {
        if (!history.canRedo()) {
            return new UndoResult.Refused("There is nothing to redo.");
        }
        HistoryEntry entry = history.peekRedo();
        if (!matches(current != null ? current : bytes, entry.beforeDigest())) {
            return new UndoResult.Refused(DRIFT_REDO);
        }
        history.redone();
        replace(Edit.apply(bytes, entry.edit().splices()));
        return new UndoResult.Done(entry.edit().splices());
    }

    /** Replaces the bytes after an external change the host has accepted (FBL §7.3): reads again and clears the history. */
    public final void reload(byte[] bytes) {
        history.clear();
        replace(bytes);
    }

    /** Called whenever the bytes change, to read them again. */
    protected abstract void reread();

    private void replace(byte[] bytes) {
        this.bytes = bytes;
        reread();
    }

    private static boolean matches(byte[] bytes, byte[] digest) {
        return MessageDigest.isEqual(EditHistory.digest(bytes), digest);
    }
}
