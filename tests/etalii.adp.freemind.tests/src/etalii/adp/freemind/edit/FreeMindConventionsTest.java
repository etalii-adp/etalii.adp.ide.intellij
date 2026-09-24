package etalii.adp.freemind.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

class FreeMindConventionsTest {

    @Test
    void escapingWritesTheFourEntitiesAndNumericReferences() {
        assertEquals("a &amp; b &lt; c &gt; d &quot;e&quot; &apos;f&apos;", FreeMindConventions.escape("a & b < c > d \"e\" 'f'"));
        assertEquals("caf&#xe9; &#x2713;", FreeMindConventions.escape("caf\u00e9 \u2713"));
        assertEquals("line1&#xa;line2", FreeMindConventions.escape("line1\nline2"));
        assertEquals("&#x1f600;", FreeMindConventions.escape("\ud83d\ude00"));
    }

    @Test
    void newIdsArePositiveIntsUniqueInTheMap() {
        String id = FreeMindConventions.newId(Set.of()::contains, new Random(42));
        assertTrue(id.matches("ID_[1-9][0-9]*"), id);

        assertEquals("ID_7", FreeMindConventions.newId(Set.of("ID_5")::contains, sequence(5, 5, 7)));
    }

    @Test
    void detectsTheLineSeparator() {
        assertEquals("\r\n", FreeMindConventions.detectLineSeparator("a\r\nb\r\nc\n"));
        assertEquals("\n", FreeMindConventions.detectLineSeparator("a\nb"));
        assertEquals("\n", FreeMindConventions.detectLineSeparator("abc"));
    }

    @Test
    void detectsTheIndentUnit() {
        assertEquals("", FreeMindConventions.detectIndentUnit("<map>\n<node>\n<node/>\n</node>\n</map>\n"));
        assertEquals("  ", FreeMindConventions.detectIndentUnit("<map>\n  <node>\n    <node/>\n  </node>\n</map>\n"));
        assertEquals("\t", FreeMindConventions.detectIndentUnit("<map>\n<node>\n\t<node>\n\t\t<node/>\n\t</node>\n</node>\n</map>\n"));
    }

    /** A generator returning the given ints from {@code nextInt(origin, bound)}, in order. */
    static RandomGenerator sequence(Integer... values) {
        Deque<Integer> queue = new ArrayDeque<>(List.of(values));
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int nextInt(int origin, int bound) {
                return queue.removeFirst();
            }
        };
    }
}
