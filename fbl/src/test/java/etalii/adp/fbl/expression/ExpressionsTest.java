package etalii.adp.fbl.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The regular expression subset (FBL §2.5) and the CEL subset (FBL §2.4). The counterparts of
 * standalone's {@code Expressions/Expressions.Tests.cs}, and this host's own test of the time bound.
 */
class ExpressionsTest {

    @ParameterizedTest
    @MethodSource("constructsOutsideTheSubset")
    void aConstructOutsideTheSubsetIsRejectedByName(String expression, String named) {
        String problem = RegexSubset.check(expression);

        assertNotNull(problem);
        assertTrue(problem.contains(named), problem);
    }

    static Stream<Arguments> constructsOutsideTheSubset() {
        return Stream.of(
                Arguments.of("(a)\\1", "backreference"),
                Arguments.of("(?<n>a)\\k<n>", "named backreference"),
                Arguments.of("\\p{L}", "Unicode property"),
                Arguments.of("(?<=a)b", "Lookbehind"),
                Arguments.of("a(?=b)", "Lookahead"),
                Arguments.of("(?>a)", "Atomic"),
                Arguments.of("(?i)a", "Inline flags"),
                Arguments.of("a++", "Possessive"),
                Arguments.of("[a", "not closed"));
    }

    @ParameterizedTest
    @MethodSource("expressionsInTheSubset")
    void anExpressionInTheSubsetIsAccepted(String expression) {
        assertNull(RegexSubset.check(expression));
    }

    static Stream<Arguments> expressionsInTheSubset() {
        return Stream.of(
                Arguments.of("^(?<name>[A-Za-z_]\\w*)\\s*->\\s*\"(?<label>[^\"]*)\"$"),
                Arguments.of("^\\d{4}-\\d{2}-\\d{2}$"),
                Arguments.of("^(?:a|b)*?c$"));
    }

    @Test
    void digitsAndWordCharactersAreAsciiOnly() {
        BoundedRegex regex = new BoundedRegex("^\\d\\w$", false, Duration.ofSeconds(1));

        // An Arabic-Indic digit and a letter with a diacritic are outside \d and \w, as in RE2.
        assertTrue(regex.isMatch("1a"));
        assertFalse(regex.isMatch("١a"));
        assertFalse(regex.isMatch("1é"));
    }

    @ParameterizedTest
    @MethodSource("celExpressions")
    void aCelExpressionEvaluatesOnAnEntry(String expression, Object expected) {
        CelMap entry = new CelMap();
        entry.put("end", "2026-10-01");
        entry.put("kind", "task");
        entry.put("count", 3L);
        entry.put("tags", new ArrayList<Object>(List.of("a", "b")));
        CelProgram program = CelCompiler.compile(expression, CelContext.TREE);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("entry", entry);

        Object value = program.evaluate(variables);

        assertEquals(expected, value instanceof List<?> list ? list.stream().map(String::valueOf).collect(Collectors.joining(",")) : value);
    }

    static Stream<Arguments> celExpressions() {
        return Stream.of(
                Arguments.of("has(entry.end)", true),
                Arguments.of("!has(entry.missing)", true),
                Arguments.of("entry.kind == 'task' && size(entry.tags) == 2", true),
                Arguments.of("entry.tags.exists(t, t.startsWith('b'))", true),
                Arguments.of("entry.tags.all(t, t.matches('^[a-z]+$'))", true),
                Arguments.of("entry.count > 2 ? 'many' : 'few'", "many"),
                Arguments.of("entry.tags.map(t, t.upperAscii())", "A,B"),
                Arguments.of("'b' in entry.tags", true),
                Arguments.of("int(entry.count) + 1", 4L));
    }

    @ParameterizedTest
    @MethodSource("expressionsOutsideTheSubset")
    void anExpressionOutsideTheSubsetFailsToCompile(String expression, CelContext context, String message) {
        RuntimeException refused = assertThrows(RuntimeException.class, () -> CelCompiler.compile(expression, context));

        assertEquals(CelException.class, refused.getClass());
        assertTrue(refused.getMessage().contains(message), refused.getMessage());
    }

    static Stream<Arguments> expressionsOutsideTheSubset() {
        return Stream.of(
                Arguments.of("groups.name == 'x'", CelContext.TREE, "'groups' is not a variable here"),
                Arguments.of("entry.x.y.z()", CelContext.TREE, ""),
                Arguments.of("timestamp('2026-01-01')", CelContext.TREE, ""));
    }

    /** FBL §16 and FR-013: a match that takes too long is stopped, not waited for. This host's own test. */
    @Test
    void aMatchThatRunsTooLongIsStopped() {
        BoundedRegex regex = new BoundedRegex("^(.*?,){25}X", false, Duration.ofMillis(50));
        String hostile = ",".repeat(60);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            long started = System.nanoTime();
            assertThrows(RegexTimeoutException.class, () -> regex.isMatch(hostile));
            assertThrows(RegexTimeoutException.class, () -> regex.match(hostile));
            assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(2)) < 0);
        });
    }
}
