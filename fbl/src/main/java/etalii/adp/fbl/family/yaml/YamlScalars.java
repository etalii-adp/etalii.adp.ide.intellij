package etalii.adp.fbl.family.yaml;

import java.util.Locale;
import java.util.regex.Pattern;

/** Scalars as the YAML 1.2 core schema reads them, and as FBL §6.3 writes them. */
public final class YamlScalars {

    // In every expression the end is the end of the text or the line feed that ends it, and no other line break.
    private static final Pattern DECIMAL = Pattern.compile("^[-+]?[0-9]+$", Pattern.UNIX_LINES);

    private static final Pattern OCTAL = Pattern.compile("^0o[0-7]+$", Pattern.UNIX_LINES);

    private static final Pattern HEXADECIMAL = Pattern.compile("^0x[0-9a-fA-F]+$", Pattern.UNIX_LINES);

    private static final Pattern FLOAT = Pattern.compile("^[-+]?(\\.[0-9]+|[0-9]+(\\.[0-9]*)?)([eE][-+]?[0-9]+)?$", Pattern.UNIX_LINES);

    private static final Pattern SPECIAL = Pattern.compile("^[-+]?(\\.inf|\\.Inf|\\.INF)$|^(\\.nan|\\.NaN|\\.NAN)$", Pattern.UNIX_LINES);

    private static final Pattern TIMESTAMP = Pattern.compile("^[0-9][0-9][0-9][0-9]-[0-9][0-9]?-[0-9][0-9]?([Tt ]|$)", Pattern.UNIX_LINES);

    private static final Pattern YAML11_NUMBER = Pattern.compile(
            "^[-+]?([0-9][0-9_]*)?\\.?[0-9_]*([eE][-+]?[0-9]+)?$|^0b[01_]+$|^[-+]?0[0-7_]+$|^[-+]?[0-9][0-9_]*(:[0-5]?[0-9])+(\\.[0-9_]*)?$",
            Pattern.UNIX_LINES);

    private YamlScalars() {
    }

    /** A plain scalar's value by the YAML 1.2 core schema: null, a Boolean, a Long, a Double, else the String. */
    public static Object typed(String plain) {
        switch (plain) {
            case "", "~", "null", "Null", "NULL":
                return null;
            case "true", "True", "TRUE":
                return true;
            case "false", "False", "FALSE":
                return false;
            default:
                break;
        }
        if (DECIMAL.matcher(plain).find()) {
            try {
                return Long.parseLong(plain);
            } catch (NumberFormatException e) {
                // Too large for an integer, or followed by a line feed: it is read as a float below.
            }
        }
        if (OCTAL.matcher(plain).find()) {
            return Long.parseUnsignedLong(plain.substring(2), 8);
        }
        if (HEXADECIMAL.matcher(plain).find()) {
            return Long.parseUnsignedLong(plain.substring(2, plain.endsWith("\n") ? plain.length() - 1 : plain.length()), 16);
        }
        if (FLOAT.matcher(plain).find()) {
            return Double.parseDouble(plain);
        }
        if (SPECIAL.matcher(plain).find()) {
            if (plain.toLowerCase(Locale.ROOT).contains("nan")) {
                return Double.NaN;
            }
            return plain.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        return plain;
    }

    /**
     * FBL §6.3: a string is plain-safe when it is not empty; has no leading or trailing whitespace;
     * no line break or control character; does not start with an indicator; contains neither ': '
     * nor ' #' and does not end with ':'; and reads back as the same string under the YAML 1.2 core
     * schema and as a string under YAML 1.1, unless the attribute's type is what it would read as
     * ({@code timeTyped} for a date or date-time attribute).
     */
    public static boolean isPlainSafe(String value, boolean timeTyped) {
        if (value.isEmpty()) {
            return false;
        }
        if (isWhiteSpace(value.charAt(0)) || isWhiteSpace(value.charAt(value.length() - 1))) {
            return false;
        }
        boolean digit = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\r' || Character.isISOControl(c)) {
                return false;
            }
            digit |= c >= '0' && c <= '9';
        }
        if ("-?:,[]{}#&*!|>'\"%@`".indexOf(value.charAt(0)) >= 0) {
            return false;
        }
        if (value.contains(": ") || value.contains(" #") || value.endsWith(":")) {
            return false;
        }
        if (!(typed(value) instanceof String)) {
            return false;
        }
        switch (value.toLowerCase(Locale.ROOT)) {
            case "yes", "no", "on", "off", "y", "n", "true", "false", "null", "~":
                return false;
            default:
                break;
        }
        if (YAML11_NUMBER.matcher(value).find() && digit) {
            return false;
        }
        if (TIMESTAMP.matcher(value).find()) {
            return timeTyped;
        }
        return true;
    }

    /** Whether Unicode counts the character as white space: a space separator, a line or paragraph separator, or one of the white space controls. */
    private static boolean isWhiteSpace(char c) {
        return c == ' ' || c >= '\t' && c <= '\r' || c == '\u0085' || c == ' '
                || switch (Character.getType(c)) {
                    case Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
                    default -> false;
                };
    }

    public static String doubleQuoted(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\t' -> builder.append("\\t");
                case '\r' -> builder.append("\\r");
                default -> {
                    if (Character.isISOControl(c)) {
                        String hex = Integer.toHexString(c).toUpperCase(Locale.ROOT);
                        builder.append("\\u").repeat('0', 4 - hex.length()).append(hex);
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }

    public static String singleQuoted(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    /** Decodes a double-quoted scalar's content (without its quotes), folding line breaks as YAML does. */
    public static String decodeDouble(String content) {
        StringBuilder builder = new StringBuilder();
        String folded = fold(content);
        for (int i = 0; i < folded.length(); i++) {
            char c = folded.charAt(i);
            if (c != '\\' || i + 1 >= folded.length()) {
                builder.append(c);
                continue;
            }
            char e = folded.charAt(++i);
            switch (e) {
                case 'n' -> builder.append('\n');
                case 't', '\t' -> builder.append('\t');
                case 'r' -> builder.append('\r');
                case '0' -> builder.append('\0');
                case 'a' -> builder.append('\u0007');
                case 'b' -> builder.append('\b');
                case 'e' -> builder.append('\u001b');
                case 'f' -> builder.append('\f');
                case 'v' -> builder.append('\u000b');
                case ' ' -> builder.append(' ');
                case '/' -> builder.append('/');
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                case 'N' -> builder.append('\u0085');
                case '_' -> builder.append(' ');
                case 'x' -> i = hex(builder, folded, i, 2);
                case 'u' -> i = hex(builder, folded, i, 4);
                case 'U' -> i = hex(builder, folded, i, 8);
                default -> builder.append('\\').append(e);
            }
        }
        return builder.toString();
    }

    /**
     * Appends the character the hexadecimal digits after {@code i} name, when they name one, and
     * returns the index of the last of them; returns {@code i} when they name none.
     */
    private static int hex(StringBuilder builder, String text, int i, int digits) {
        int available = Math.min(digits, text.length() - i - 1);
        if (available <= 0) {
            return i;
        }
        // The digits may stand between white space, as the first host reads them.
        int start = i + 1;
        int end = start + available;
        while (start < end && isNumberSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isNumberSpace(text.charAt(end - 1))) {
            end--;
        }
        if (start == end) {
            return i;
        }
        int code = 0;
        for (int p = start; p < end; p++) {
            int value = Character.digit(text.charAt(p), 16);
            if (value < 0 || text.charAt(p) > 'f') {
                return i;
            }
            code = code << 4 | value;
        }
        if (code >= 0 && code <= 0x10FFFF && !(code >= 0xD800 && code <= 0xDFFF)) {
            builder.appendCodePoint(code);
        }
        return i + available;
    }

    private static boolean isNumberSpace(char c) {
        return c == ' ' || c >= '\t' && c <= '\r';
    }

    public static String decodeSingle(String content) {
        return fold(content).replace("''", "'");
    }

    /** Line folding of flow scalars: a line break and the whitespace around it become a space, an empty line a newline. */
    public static String fold(String content) {
        if (content.indexOf('\n') < 0) {
            return content;
        }
        String[] lines = content.replace("\r\n", "\n").split("\n", -1);
        StringBuilder builder = new StringBuilder(trim(lines[0], false, true));
        int empty = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = i == lines.length - 1 ? trim(lines[i], true, false) : trim(lines[i], true, true);
            if (line.isEmpty() && i < lines.length - 1) {
                empty++;
                continue;
            }
            if (empty > 0) {
                builder.repeat('\n', empty);
            } else {
                builder.append(' ');
            }
            empty = 0;
            builder.append(line);
        }
        return builder.toString();
    }

    /** The line without the spaces and tabs at its start, at its end, or both. */
    private static String trim(String line, boolean start, boolean end) {
        int from = 0;
        int to = line.length();
        while (start && from < to && (line.charAt(from) == ' ' || line.charAt(from) == '\t')) {
            from++;
        }
        while (end && to > from && (line.charAt(to - 1) == ' ' || line.charAt(to - 1) == '\t')) {
            to--;
        }
        return line.substring(from, to);
    }
}
