package etalii.adp.fbl.expression;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A compiled subset expression with a match timeout (FBL §16): a match that takes too long is a finding, not a hang. */
public final class BoundedRegex {

    /** Far enough to mean no bound, near enough that a deadline never overflows. */
    private static final long LONGEST_NANOS = Long.MAX_VALUE / 4;

    private final String expression;
    private final Duration timeout;
    private final long timeoutNanos;
    private final Pattern pattern;
    private final List<String> groupNames;
    private final Map<String, String> javaNames;

    /** Throws {@link java.util.regex.PatternSyntaxException} when the expression does not compile. */
    public BoundedRegex(String expression, boolean caseInsensitive, Duration timeout) {
        this.expression = expression;
        this.timeout = timeout;
        long nanos;
        try {
            nanos = timeout.toNanos();
        } catch (ArithmeticException e) {
            nanos = LONGEST_NANOS;
        }
        this.timeoutNanos = Math.min(nanos, LONGEST_NANOS);
        RegexSubset.Translation translation = RegexSubset.translate(expression, caseInsensitive);
        this.pattern = Pattern.compile(translation.pattern(), RegexSubset.FLAGS);
        this.groupNames = translation.names();
        this.javaNames = translation.javaNames();
    }

    /** The expression as the binding spells it. */
    public String expression() {
        return expression;
    }

    /** The names of the expression's named groups as the binding spells them, in the order they first appear. */
    public List<String> groupNames() {
        return groupNames;
    }

    /** The first match in {@code input}, null when there is none; throws {@link RegexTimeoutException} when the bound is exceeded. */
    public RegexMatch match(String input) {
        Matcher matcher = pattern.matcher(bounded(input));
        if (!matcher.find()) {
            return null;
        }
        Map<String, RegexMatch.Group> groups = new LinkedHashMap<>();
        for (String name : groupNames) {
            String javaName = javaNames.get(name);
            int start = matcher.start(javaName);
            groups.put(name, start < 0 ? RegexMatch.Group.NONE : group(input, start, matcher.end(javaName)));
        }
        return new RegexMatch(group(input, matcher.start(), matcher.end()), groupNames, groups);
    }

    /** Whether {@code input} holds a match; throws {@link RegexTimeoutException} when the bound is exceeded. */
    public boolean isMatch(String input) {
        return pattern.matcher(bounded(input)).find();
    }

    private static RegexMatch.Group group(String input, int start, int end) {
        return new RegexMatch.Group(true, input.substring(start, end), start, end);
    }

    private Bounded bounded(String input) {
        return new Bounded(input, 0, input.length(), System.nanoTime() + timeoutNanos);
    }

    /**
     * The input as the engine reads it. The engine has no timeout of its own, but it cannot match
     * without reading characters, so reading one fails once the deadline has passed.
     */
    private final class Bounded implements CharSequence {

        private final String input;
        private final int start;
        private final int end;
        private final long deadline;
        private int reads;

        Bounded(String input, int start, int end, long deadline) {
            this.input = input;
            this.start = start;
            this.end = end;
            this.deadline = deadline;
        }

        @Override
        public char charAt(int index) {
            // The clock is read once in 64 characters: a match that runs away reads millions.
            if ((++reads & 0x3F) == 0 && System.nanoTime() - deadline > 0) {
                throw new RegexTimeoutException(expression, timeout);
            }
            return input.charAt(start + index);
        }

        @Override
        public int length() {
            return end - start;
        }

        @Override
        public CharSequence subSequence(int from, int to) {
            return new Bounded(input, start + from, start + to, deadline);
        }

        @Override
        public String toString() {
            return input.substring(start, end);
        }
    }
}
