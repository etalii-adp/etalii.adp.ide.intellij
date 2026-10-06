package etalii.adp.fbl.history;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;

import etalii.adp.fbl.Edit;

/**
 * The history of one body (FBL §7.1): each edit with the splices that undo it and the digests of
 * the body before and after it. A snapshot edit keeps the whole body before it as well; its undo
 * gives the same bytes as the inverse splices.
 */
final class EditHistory {

    private final Deque<HistoryEntry> undo = new ArrayDeque<>();
    private final Deque<HistoryEntry> redo = new ArrayDeque<>();

    boolean canUndo() {
        return !undo.isEmpty();
    }

    boolean canRedo() {
        return !redo.isEmpty();
    }

    void record(byte[] before, byte[] after, Edit edit) {
        undo.push(new HistoryEntry(
                edit,
                Edit.inverse(before, edit.splices()),
                digest(before),
                digest(after),
                edit.snapshot() ? before : null));
        redo.clear();
    }

    HistoryEntry peekUndo() {
        return undo.element();
    }

    HistoryEntry peekRedo() {
        return redo.element();
    }

    void undone() {
        redo.push(undo.pop());
    }

    void redone() {
        undo.push(redo.pop());
    }

    void clear() {
        undo.clear();
        redo.clear();
    }

    /** The SHA-256 digest of {@code bytes}. */
    static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to provide SHA-256.
            throw new IllegalStateException(e);
        }
    }
}
