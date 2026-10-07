package etalii.adp.fbl.registration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import etalii.adp.fbl.text.Span;

/** One {@code key: value} entry of a block, the key being everything before the line's last {@code ": "}. */
public record RegistrationEntry(String key, String value, Span keySpan, Span valueSpan, Span line, int indent) {

    /** A number in the invariant float form: an optional sign, digits with an optional point, an optional exponent. */
    private static final Pattern FLOAT = Pattern.compile("[+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    /** The place of an element on the canvas. */
    public record Position(double x, double y) {
    }

    /** A layout entry's {@code x y}, null when the value is not two numbers. */
    public Position position() {
        List<String> parts = new ArrayList<>();
        for (String part : value.split(" ")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        if (parts.size() != 2) {
            return null;
        }
        Double x = tryParse(parts.get(0));
        if (x == null) {
            return null;
        }
        Double y = tryParse(parts.get(1));
        if (y == null) {
            return null;
        }
        return new Position(x, y);
    }

    /** The number a text is in the invariant float form, white space around it allowed; null when it is none. */
    private static Double tryParse(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isWhite(text.charAt(start))) {
            start++;
        }
        while (end > start && isWhite(text.charAt(end - 1))) {
            end--;
        }
        String number = text.substring(start, end);
        if (FLOAT.matcher(number).matches()) {
            return Double.valueOf(number);
        }
        // The first host's parser also reads the names of the values that are not finite, in any case.
        String bare = number.startsWith("+") || number.startsWith("-") ? number.substring(1) : number;
        boolean negative = number.startsWith("-");
        return switch (bare.toLowerCase(Locale.ROOT)) {
            case "nan" -> Double.NaN;
            case "infinity" -> negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            default -> null;
        };
    }

    private static boolean isWhite(char c) {
        return c == ' ' || (c >= '\t' && c <= '\r');
    }
}
