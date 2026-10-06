package etalii.adp.fbl.family.yaml;

import java.util.ArrayList;
import java.util.List;

import etalii.adp.fbl.expression.CelMap;
import etalii.adp.fbl.expression.CelValues;

/**
 * Reads a YAML flow collection ({@code [a, b]}, <code>{k: v}</code>) into the values CEL sees (FBL §4.3):
 * its members are readable, while its only writable span is the whole collection.
 */
final class FlowReader {

    private final String text;
    private final boolean asWritten;
    private int position;

    FlowReader(String text) {
        this(text, false);
    }

    /**
     * @param asWritten whether a plain scalar is kept as its text instead of being read by the core
     *        schema: what a reader needs that compares keys and values as they are written
     */
    FlowReader(String text, boolean asWritten) {
        this.text = text;
        this.asWritten = asWritten;
    }

    /** The collection: a list, a {@link CelMap}, or for a scalar a String, a Long, a Double, a Boolean or null. */
    Object read() {
        Object value = value();
        return value;
    }

    private void skip() {
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                position++;
            } else if (c == '#' && (position == 0 || text.charAt(position - 1) == ' ' || text.charAt(position - 1) == '\t' || text.charAt(position - 1) == '\n')) {
                while (position < text.length() && text.charAt(position) != '\n') {
                    position++;
                }
            } else {
                break;
            }
        }
    }

    private Object value() {
        skip();
        if (position >= text.length()) {
            return null;
        }
        switch (text.charAt(position)) {
            case '[': {
                position++;
                List<Object> list = new ArrayList<>();
                while (true) {
                    skip();
                    if (position >= text.length()) {
                        return list;
                    }
                    if (text.charAt(position) == ']') {
                        position++;
                        return list;
                    }
                    list.add(value());
                    skip();
                    if (position < text.length() && text.charAt(position) == ',') {
                        position++;
                    }
                }
            }
            case '{': {
                position++;
                CelMap map = new CelMap();
                while (true) {
                    skip();
                    if (position >= text.length()) {
                        return map;
                    }
                    if (text.charAt(position) == '}') {
                        position++;
                        return map;
                    }
                    Object key = scalar(true);
                    skip();
                    Object value = null;
                    if (position < text.length() && text.charAt(position) == ':') {
                        position++;
                        value = value();
                    }
                    map.put(keyText(key), value);
                    skip();
                    if (position < text.length() && text.charAt(position) == ',') {
                        position++;
                    }
                }
            }
            default:
                return scalar(false);
        }
    }

    private Object scalar(boolean key) {
        char c = text.charAt(position);
        if (c == '"' || c == '\'') {
            int start = ++position;
            while (position < text.length()) {
                if (c == '"' && text.charAt(position) == '\\') {
                    position += 2;
                    continue;
                }
                if (text.charAt(position) == c) {
                    if (c == '\'' && position + 1 < text.length() && text.charAt(position + 1) == '\'') {
                        position += 2;
                        continue;
                    }
                    break;
                }
                position++;
            }
            String inner = text.substring(start, Math.min(position, text.length()));
            position++;
            return c == '"' ? YamlScalars.decodeDouble(inner) : YamlScalars.decodeSingle(inner);
        }
        int begin = position;
        while (position < text.length()) {
            char d = text.charAt(position);
            if (d == ',' || d == ']' || d == '}') {
                break;
            }
            if (d == ':' && (key || position + 1 >= text.length() || text.charAt(position + 1) == ' ' || text.charAt(position + 1) == ','
                    || text.charAt(position + 1) == ']' || text.charAt(position + 1) == '}')) {
                break;
            }
            position++;
        }
        String plain = YamlScalars.fold(trim(text.substring(begin, position)));
        return asWritten ? plain : YamlScalars.typed(plain);
    }

    /** The text without the white space, as Unicode counts it, at its start and its end. */
    private static String trim(String value) {
        int from = 0;
        int to = value.length();
        while (from < to && isWhiteSpace(value.charAt(from))) {
            from++;
        }
        while (to > from && isWhiteSpace(value.charAt(to - 1))) {
            to--;
        }
        return value.substring(from, to);
    }

    private static boolean isWhiteSpace(char c) {
        return c == ' ' || c >= '\t' && c <= '\r' || c == '\u0085' || c == ' '
                || switch (Character.getType(c)) {
                    case Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
                    default -> false;
                };
    }

    /**
     * A key as the text a map is keyed by, as the first host writes a value that is not a string:
     * nothing for null, {@code True} or {@code False}, an integer in decimal, and for any other
     * number the shortest text that reads back as the same number.
     */
    private static String keyText(Object key) {
        return switch (key) {
            case null -> "";
            case String s -> s;
            case Boolean b -> b ? "True" : "False";
            case Double d -> CelValues.format(d.doubleValue());
            default -> String.valueOf(key);
        };
    }
}
