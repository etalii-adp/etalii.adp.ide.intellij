package etalii.adp.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.intellij.openapi.editor.Document;

/**
 * Non-overlapping {@link TextChange}s against one text, in text order. Inserts at the same offset
 * keep the order they were given in; an insert comes before a change that starts at its offset.
 */
public final class TextChanges {

    private final List<TextChange> changes;

    /** Throws {@link IllegalArgumentException} when two changes overlap. */
    public TextChanges(List<TextChange> changes) {
        List<TextChange> sorted = new ArrayList<>(changes);
        sorted.sort(Comparator.comparingInt(TextChange::offset).thenComparing(change -> change.length() > 0));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).end() > sorted.get(i).offset()) {
                throw new IllegalArgumentException("Overlapping text changes: " + sorted.get(i - 1) + " and " + sorted.get(i));
            }
        }
        this.changes = List.copyOf(sorted);
    }

    public static TextChanges of(TextChange... changes) {
        return new TextChanges(List.of(changes));
    }

    /** The changes in text order. */
    public List<TextChange> changes() {
        return changes;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /** The text with every change applied. */
    public String applyTo(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int from = 0;
        for (TextChange change : changes) {
            if (change.end() > text.length()) {
                throw new IllegalArgumentException(change + " is outside a text of length " + text.length());
            }
            out.append(text, from, change.offset()).append(change.replacement());
            from = change.end();
        }
        return out.append(text, from, text.length()).toString();
    }

    /**
     * Applies every change to {@code document}, highest offset first so the earlier offsets stay
     * valid. The caller holds the write lock, inside a command.
     */
    public void applyTo(Document document) {
        if (!changes.isEmpty() && changes.get(changes.size() - 1).end() > document.getTextLength()) {
            throw new IllegalArgumentException("The changes do not fit a document of length " + document.getTextLength());
        }
        for (int i = changes.size() - 1; i >= 0; i--) {
            TextChange change = changes.get(i);
            document.replaceString(change.offset(), change.end(), change.replacement());
        }
    }

    @Override
    public String toString() {
        return changes.toString();
    }
}
