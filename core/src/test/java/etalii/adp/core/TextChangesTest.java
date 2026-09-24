package etalii.adp.core;

import static etalii.adp.core.TextChange.delete;
import static etalii.adp.core.TextChange.insert;
import static etalii.adp.core.TextChange.replace;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Text changes keep the ordering and overlap rules the edit catalogue was written against. */
class TextChangesTest {

    private static final String TEXT = "<map>\n</map>\n";

    @Test
    void insertsDeletesAndReplacesApplyAgainstTheOriginalOffsets() {
        TextChanges changes = TextChanges.of(insert(6, "<node TEXT=\"a\"/>\n"), insert(0, "<!-- c -->\n"), replace(1, 3, "MAP"),
                delete(12, 1));

        assertEquals("<!-- c -->\n<MAP>\n<node TEXT=\"a\"/>\n</map>", changes.applyTo(TEXT));
    }

    @Test
    void insertsAtOneOffsetKeepTheirOrder() {
        TextChanges changes = TextChanges.of(insert(6, "b"), insert(6, "c"), insert(0, "a"));

        assertEquals("a<map>\nbc</map>\n", changes.applyTo(TEXT));
    }

    @Test
    void anInsertComesBeforeAChangeStartingAtItsOffset() {
        TextChanges changes = TextChanges.of(replace(6, 6, "</MAP>"), insert(6, "<node/>"));

        assertEquals("<map>\n<node/></MAP>\n", changes.applyTo(TEXT));
    }

    @Test
    void anInsertAtTheEndOfAChangeIsNotAnOverlap() {
        TextChanges changes = TextChanges.of(insert(5, "!"), replace(0, 5, "<m>"));

        assertEquals("<m>!\n</map>\n", changes.applyTo(TEXT));
    }

    @Test
    void overlappingChangesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TextChanges.of(replace(0, 5, "x"), replace(4, 2, "y")));
        assertThrows(IllegalArgumentException.class, () -> TextChanges.of(delete(0, 5), insert(3, "x")));
    }

    /** Spec 001's bad edit: a change outside the text is refused, and nothing is applied. */
    @Test
    void aBadEditLeavesTheDocumentUnchanged() {
        TextChanges changes = TextChanges.of(insert(0, "x"), insert(1000, "y"));

        assertThrows(IllegalArgumentException.class, () -> changes.applyTo(TEXT));
    }

    @Test
    void changesAreListedInTextOrder() {
        TextChanges changes = new TextChanges(List.of(delete(9, 1), insert(2, "b"), insert(2, "a")));

        assertEquals(List.of(insert(2, "b"), insert(2, "a"), delete(9, 1)), changes.changes());
    }
}
