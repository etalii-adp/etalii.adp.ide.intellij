package etalii.adp.fbl.family.json;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import etalii.adp.fbl.text.Span;

/**
 * Reads a JSON text byte for byte (FBL §4.4): every value, member name and separator with its span,
 * comments refused, a repeated member name kept and marked. The json family reads a body with it,
 * and the loader reads an FBL document with it, which is how a repeated key gets its place.
 */
public final class JsonParser {

    /** What .NET's float parsing takes, which is what the body grammar of the first host accepts. */
    private static final Pattern LENIENT_NUMBER = Pattern.compile("[+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final Pattern INTEGER = Pattern.compile("[+-]?[0-9]+");
    private static final Pattern RFC_8259_NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

    private final byte[] bytes;
    private final boolean strict;
    private int position;

    private JsonParser(byte[] bytes, int from, boolean strict) {
        this.bytes = bytes;
        this.position = from;
        this.strict = strict;
    }

    /** Reads a body: one JSON value from {@code from} to the end, with the number forms the first host accepts. */
    public static JsonValue parse(byte[] bytes, int from) throws JsonSyntaxException {
        return new JsonParser(bytes, from, false).parseText();
    }

    /** Reads a document that must be RFC 8259 and nothing more: a number has exactly that grammar. */
    public static JsonValue parseStrict(byte[] bytes, int from) throws JsonSyntaxException {
        return new JsonParser(bytes, from, true).parseText();
    }

    private JsonValue parseText() throws JsonSyntaxException {
        skipWhitespace();
        JsonValue value = parseValue();
        skipWhitespace();
        if (position < bytes.length) {
            throw new JsonSyntaxException(position, "The body has more than one JSON value.");
        }
        return value;
    }

    private int current() {
        return position < bytes.length ? bytes[position] & 0xFF : 0;
    }

    private void skipWhitespace() throws JsonSyntaxException {
        while (position < bytes.length) {
            int b = bytes[position];
            if (b == ' ' || b == '\t' || b == '\r' || b == '\n') {
                position++;
            } else if (b == '/') {
                throw new JsonSyntaxException(position, "A JSON body cannot hold comments.");
            } else {
                break;
            }
        }
    }

    private void expect(char expected) throws JsonSyntaxException {
        if (current() != expected) {
            throw new JsonSyntaxException(position, "'" + expected + "' was expected here.");
        }
        position++;
    }

    private JsonValue parseValue() throws JsonSyntaxException {
        if (position >= bytes.length) {
            throw new JsonSyntaxException(position, "The body ends where a value was expected.");
        }
        return switch (current()) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> {
                Text string = parseString();
                yield new JsonValue(JsonValue.Kind.STRING, string.span(), string.text(), string.text(), List.of());
            }
            default -> parseLiteral();
        };
    }

    private JsonValue parseObject() throws JsonSyntaxException {
        int start = position;
        position++;
        List<JsonMember> members = new ArrayList<>();
        Set<String> names = new HashSet<>();
        skipWhitespace();
        if (current() == '}') {
            position++;
            return container(JsonValue.Kind.OBJECT, start, members);
        }
        while (true) {
            skipWhitespace();
            if (current() != '"') {
                throw new JsonSyntaxException(position, "A member name in quotes was expected here.");
            }
            Text name = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            JsonValue value = parseValue();
            boolean duplicate = !names.add(name.text());
            skipWhitespace();
            if (current() == ',') {
                members.add(new JsonMember(name.text(), name.span(), value, position, duplicate));
                position++;
                continue;
            }
            members.add(new JsonMember(name.text(), name.span(), value, -1, duplicate));
            expect('}');
            break;
        }
        return container(JsonValue.Kind.OBJECT, start, members);
    }

    private JsonValue parseArray() throws JsonSyntaxException {
        int start = position;
        position++;
        List<JsonMember> items = new ArrayList<>();
        skipWhitespace();
        if (current() == ']') {
            position++;
            return container(JsonValue.Kind.ARRAY, start, items);
        }
        while (true) {
            skipWhitespace();
            JsonValue value = parseValue();
            skipWhitespace();
            if (current() == ',') {
                items.add(new JsonMember(null, null, value, position, false));
                position++;
                continue;
            }
            items.add(new JsonMember(null, null, value, -1, false));
            expect(']');
            break;
        }
        return container(JsonValue.Kind.ARRAY, start, items);
    }

    private JsonValue container(JsonValue.Kind kind, int start, List<JsonMember> members) {
        return new JsonValue(kind, new Span(start, position), "", null, members);
    }

    private record Text(String text, Span span) {
    }

    private Text parseString() throws JsonSyntaxException {
        int start = position;
        position++;
        StringBuilder builder = new StringBuilder();
        int runStart = position;
        while (true) {
            if (position >= bytes.length) {
                throw new JsonSyntaxException(start, "A string is never closed.");
            }
            int b = bytes[position] & 0xFF;
            if (b == '"') {
                break;
            }
            if (b < 0x20) {
                throw new JsonSyntaxException(position, "A string holds a control character that is not escaped.");
            }
            if (b != '\\') {
                position++;
                continue;
            }
            builder.append(new String(bytes, runStart, position - runStart, UTF_8));
            position++;
            int escape = current();
            position++;
            switch (escape) {
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                case '/' -> builder.append('/');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    int code = position + 4 <= bytes.length ? hex4(position) : -1;
                    if (code < 0) {
                        throw new JsonSyntaxException(position, "A \\u escape needs four hexadecimal digits.");
                    }
                    builder.append((char) code);
                    position += 4;
                }
                default -> throw new JsonSyntaxException(position - 1, "This escape is not one JSON knows.");
            }
            runStart = position;
        }
        builder.append(new String(bytes, runStart, position - runStart, UTF_8));
        position++;
        return new Text(builder.toString(), new Span(start, position));
    }

    /**
     * The code the four bytes at {@code at} name, or -1 when they name none. A document takes four
     * hexadecimal digits and nothing else. A body takes what the first host accepts: white space
     * before the digits, and white space and then NUL bytes after them, as long as one digit is there.
     */
    private int hex4(int at) {
        int end = at + 4;
        int i = at;
        if (!strict) {
            while (i < end && isHexWhite(bytes[i])) {
                i++;
            }
        }
        int code = 0;
        int digits = 0;
        while (i < end) {
            int digit = Character.digit(bytes[i] & 0xFF, 16);
            if (digit < 0) {
                break;
            }
            code = code * 16 + digit;
            digits++;
            i++;
        }
        if (strict) {
            return digits == 4 ? code : -1;
        }
        if (digits == 0) {
            return -1;
        }
        while (i < end && isHexWhite(bytes[i])) {
            i++;
        }
        while (i < end && bytes[i] == 0) {
            i++;
        }
        return i == end ? code : -1;
    }

    private static boolean isHexWhite(byte b) {
        return b == ' ' || b >= 0x09 && b <= 0x0D;
    }

    private JsonValue parseLiteral() throws JsonSyntaxException {
        int start = position;
        while (position < bytes.length && isLiteralByte(bytes[position])) {
            position++;
        }
        String raw = new String(bytes, start, position - start, UTF_8);
        Span span = new Span(start, position);
        switch (raw) {
            case "true":
                return new JsonValue(JsonValue.Kind.TRUE, span, raw, Boolean.TRUE, List.of());
            case "false":
                return new JsonValue(JsonValue.Kind.FALSE, span, raw, Boolean.FALSE, List.of());
            case "null":
                return new JsonValue(JsonValue.Kind.NULL, span, raw, null, List.of());
            default:
                Object number = number(raw);
                if (number == null) {
                    throw new JsonSyntaxException(start, raw.isEmpty() ? "A value was expected here." : "'" + raw + "' is not a JSON value.");
                }
                return new JsonValue(JsonValue.Kind.NUMBER, span, raw, number, List.of());
        }
    }

    private static boolean isLiteralByte(byte b) {
        return b >= 'a' && b <= 'z' || b >= '0' && b <= '9' || b == '-' || b == '+' || b == '.' || b == 'E';
    }

    /** A {@link Long} where the text is an integer that fits one, else a {@link Double}; null when it is no number. */
    private Object number(String raw) {
        if (strict && !RFC_8259_NUMBER.matcher(raw).matches()) {
            return null;
        }
        if (INTEGER.matcher(raw).matches()) {
            try {
                return Long.valueOf(raw);
            } catch (NumberFormatException e) {
                // Too large for a long: read as a double, below.
            }
        }
        if (raw.startsWith("+")) {
            return null;
        }
        if (LENIENT_NUMBER.matcher(raw).matches()) {
            return Double.valueOf(raw);
        }
        if (strict) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "infinity" -> Double.POSITIVE_INFINITY;
            case "-infinity" -> Double.NEGATIVE_INFINITY;
            case "nan", "-nan" -> Double.NaN;
            default -> null;
        };
    }
}
