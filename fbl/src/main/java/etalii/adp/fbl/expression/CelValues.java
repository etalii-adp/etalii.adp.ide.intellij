package etalii.adp.fbl.expression;

import java.math.BigDecimal;
import java.text.BreakIterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** How CEL values convert, compare and print. */
final class CelValues {

    /** How far an int and a double may lie apart and still be equal: the single-precision 0.000001, widened, as the first host has it. */
    private static final double TOLERANCE = 0.000001f;

    private CelValues() {
    }

    static String asString(Object value) {
        if (value instanceof String s) {
            return s;
        }
        throw new CelException("A string was expected.");
    }

    static long asInt(Object value) {
        if (value instanceof Long l) {
            return l;
        }
        throw new CelException("An int was expected.");
    }

    static double asDouble(Object value) {
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Double d) {
            return d;
        }
        throw new CelException("A number was expected.");
    }

    /**
     * Equality as CEL has it here: an int and a double compare by value, two lists item by item, and
     * anything else as itself. Two maps are equal only when they are the same map, and a zero equals
     * its negative.
     */
    static boolean equal(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a instanceof Long x && b instanceof Double y) {
            return Math.abs(x - y) < TOLERANCE;
        }
        if (a instanceof Double x && b instanceof Long y) {
            return Math.abs(x - y) < TOLERANCE;
        }
        if (a instanceof List<?> x && b instanceof List<?> y) {
            if (x.size() != y.size()) {
                return false;
            }
            for (int i = 0; i < x.size(); i++) {
                if (!equal(x.get(i), y.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof Double x && b instanceof Double y) {
            return x.doubleValue() == y.doubleValue() || (x.isNaN() && y.isNaN());
        }
        if (a instanceof Map<?, ?> || a instanceof List<?>) {
            return a == b;
        }
        return a.equals(b);
    }

    static int compare(Object a, Object b) {
        if (a instanceof String x && b instanceof String y) {
            return x.compareTo(y);
        }
        if (a instanceof Boolean x && b instanceof Boolean y) {
            return Boolean.compare(x, y);
        }
        double x = asDouble(a);
        double y = asDouble(b);
        if (x < y) {
            return -1;
        }
        if (x > y) {
            return 1;
        }
        if (x == y) {
            return 0;
        }
        // Not a number sorts before every number and beside itself.
        if (Double.isNaN(x)) {
            return Double.isNaN(y) ? 0 : -1;
        }
        return 1;
    }

    /** The quotient of two ints; the one quotient that does not fit is an error, not a wrong number. */
    static long divide(long a, long b) {
        if (a == Long.MIN_VALUE && b == -1) {
            throw new ArithmeticException("Arithmetic operation resulted in an overflow.");
        }
        return a / b;
    }

    static long remainder(long a, long b) {
        if (a == Long.MIN_VALUE && b == -1) {
            throw new ArithmeticException("Arithmetic operation resulted in an overflow.");
        }
        return a % b;
    }

    static String format(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (value instanceof Long l) {
            return Long.toString(l);
        }
        if (value instanceof Double d) {
            return format(d.doubleValue());
        }
        return value.toString();
    }

    /**
     * The shortest text that reads back as the same double, as the first host prints it: no fraction
     * for a whole number, and an exponent ({@code 1E+15}, {@code 1E-05}) from fifteen digits before
     * the point or four zeros after it.
     */
    private static String format(double d) {
        if (Double.isNaN(d)) {
            return "NaN";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "Infinity" : "-Infinity";
        }
        String sign = d < 0 || (d == 0 && 1 / d < 0) ? "-" : "";
        if (d == 0) {
            return sign + "0";
        }
        BigDecimal value = new BigDecimal(Double.toString(Math.abs(d))).stripTrailingZeros();
        String digits = value.unscaledValue().toString();
        int exponent = digits.length() - 1 - value.scale();
        if (exponent >= -4 && exponent < 15) {
            return sign + value.toPlainString();
        }
        StringBuilder builder = new StringBuilder(sign).append(digits.charAt(0));
        if (digits.length() > 1) {
            builder.append('.').append(digits, 1, digits.length());
        }
        builder.append('E').append(exponent < 0 ? '-' : '+');
        int magnitude = Math.abs(exponent);
        if (magnitude < 10) {
            builder.append('0');
        }
        return builder.append(magnitude).toString();
    }

    /** The number of text elements, what a reader sees as characters, in {@code text}. */
    static long textElements(String text) {
        BreakIterator elements = BreakIterator.getCharacterInstance(Locale.ROOT);
        elements.setText(text);
        long count = 0;
        while (elements.next() != BreakIterator.DONE) {
            count++;
        }
        return count;
    }

    /** Each character's own lower case, whatever the locale; a character never becomes two. */
    static String lower(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        text.codePoints().forEach(c -> builder.appendCodePoint(Character.toLowerCase(c)));
        return builder.toString();
    }

    /** Each character's own upper case, whatever the locale; a character never becomes two. */
    static String upper(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        text.codePoints().forEach(c -> builder.appendCodePoint(Character.toUpperCase(c)));
        return builder.toString();
    }

    /** The int {@code text} spells, with an optional sign and white space around it; null when it spells none or one that does not fit. */
    static Long parseInt(String text) {
        String trimmed = trim(text);
        int digits = !trimmed.isEmpty() && (trimmed.charAt(0) == '-' || trimmed.charAt(0) == '+') ? 1 : 0;
        if (digits == trimmed.length()) {
            return null;
        }
        for (int i = digits; i < trimmed.length(); i++) {
            if (!isAsciiDigit(trimmed.charAt(i))) {
                return null;
            }
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The double {@code text} spells: an optional sign, digits with or without thousands separators,
     * a fraction, an exponent, or one of {@code NaN} and {@code Infinity}, with white space around
     * it. Throws {@link NumberFormatException} when it spells none.
     */
    static double parseDouble(String text) {
        String trimmed = trim(text);
        int i = 0;
        boolean negative = false;
        if (i < trimmed.length() && (trimmed.charAt(i) == '-' || trimmed.charAt(i) == '+')) {
            negative = trimmed.charAt(i) == '-';
            i++;
        }
        String unsigned = trimmed.substring(i);
        if (unsigned.equalsIgnoreCase("NaN")) {
            return Double.NaN;
        }
        if (unsigned.equalsIgnoreCase("Infinity")) {
            return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        StringBuilder number = new StringBuilder();
        int mantissa = 0;
        while (i < trimmed.length() && (isAsciiDigit(trimmed.charAt(i)) || trimmed.charAt(i) == ',')) {
            if (trimmed.charAt(i) != ',') {
                number.append(trimmed.charAt(i));
                mantissa++;
            }
            i++;
        }
        if (i < trimmed.length() && trimmed.charAt(i) == '.') {
            number.append('.');
            i++;
            while (i < trimmed.length() && isAsciiDigit(trimmed.charAt(i))) {
                number.append(trimmed.charAt(i++));
                mantissa++;
            }
        }
        if (mantissa == 0) {
            throw notANumber(text);
        }
        if (i < trimmed.length() && (trimmed.charAt(i) == 'e' || trimmed.charAt(i) == 'E')) {
            number.append('E');
            i++;
            if (i < trimmed.length() && (trimmed.charAt(i) == '-' || trimmed.charAt(i) == '+')) {
                number.append(trimmed.charAt(i++));
            }
            int exponent = i;
            while (i < trimmed.length() && isAsciiDigit(trimmed.charAt(i))) {
                number.append(trimmed.charAt(i++));
            }
            if (i == exponent) {
                throw notANumber(text);
            }
        }
        if (i < trimmed.length()) {
            throw notANumber(text);
        }
        double value = Double.parseDouble(number.toString());
        return negative ? -value : value;
    }

    private static NumberFormatException notANumber(String text) {
        return new NumberFormatException("The input string '" + text + "' was not in a correct format.");
    }

    /** {@code text} without the white space a number may stand in: tab to carriage return and the space, and null characters after it. */
    private static String trim(String text) {
        int start = 0;
        int end = text.length();
        while (end > start && text.charAt(end - 1) == '\0') {
            end--;
        }
        while (start < end && isNumberSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isNumberSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static boolean isNumberSpace(char c) {
        return (c >= '\t' && c <= '\r') || c == ' ';
    }

    static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
