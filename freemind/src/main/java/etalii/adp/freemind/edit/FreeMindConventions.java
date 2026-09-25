package etalii.adp.freemind.edit;

import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * How FreeMind 1.0.1 writes what the designer adds (research R5), so edited maps stay normal
 * FreeMind maps (FR-011).
 */
public final class FreeMindConventions {

    private FreeMindConventions() {
    }

    /**
     * An attribute value as FreeMind writes it: the five XML entities, and every character outside
     * printable ASCII as a hexadecimal numeric reference.
     */
    public static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(c -> {
            switch (c) {
            case '&' -> out.append("&amp;");
            case '<' -> out.append("&lt;");
            case '>' -> out.append("&gt;");
            case '"' -> out.append("&quot;");
            case '\'' -> out.append("&apos;");
            default -> {
                if (c < 32 || c > 126) {
                    out.append("&#x").append(Integer.toHexString(c)).append(';');
                } else {
                    out.append((char) c);
                }
            }
            }
        });
        return out.toString();
    }

    /** {@code ID_} and a random positive int, drawn until {@code taken} rejects it. */
    public static String newId(Predicate<String> taken, RandomGenerator random) {
        while (true) {
            String id = "ID_" + random.nextInt(1, Integer.MAX_VALUE);
            if (!taken.test(id)) {
                return id;
            }
        }
    }

    /**
     * A new map as FreeMind 1.0.1 writes it: one root node, "New Mindmap", with {@code id} and
     * {@code now} as its timestamps, lines ending in {@code separator}.
     */
    public static String newMapText(String id, long now, String separator) {
        return "<map version=\"1.0.1\">" + separator
                + "<!-- To view this file, download free mind mapping software FreeMind from http://freemind.sourceforge.net -->" + separator
                + "<node CREATED=\"" + now + "\" ID=\"" + id + "\" MODIFIED=\"" + now + "\" TEXT=\"New Mindmap\"/>" + separator
                + "</map>" + separator;
    }

    /** The clock read for one operation; every timestamp the operation writes uses it. */
    public static long now() {
        return System.currentTimeMillis();
    }

    /** The most frequent line separator in {@code text}, {@code \n} when it has none. */
    public static String detectLineSeparator(String text) {
        int crlf = 0;
        int lf = 0;
        int cr = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    crlf++;
                    i++;
                } else {
                    cr++;
                }
            } else if (c == '\n') {
                lf++;
            }
        }
        if (crlf > lf && crlf >= cr) {
            return "\r\n";
        }
        return cr > lf ? "\r" : "\n";
    }

    /**
     * The whitespace one nesting level adds: the shortest non-empty indentation of a line that
     * starts with {@code <node}. Empty when nodes are not indented, as FreeMind writes them.
     */
    public static String detectIndentUnit(String text) {
        String unit = "";
        int lineStart = 0;
        while (lineStart < text.length()) {
            int i = lineStart;
            while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
                i++;
            }
            if (i > lineStart && text.startsWith("<node", i) && (unit.isEmpty() || i - lineStart < unit.length())) {
                unit = text.substring(lineStart, i);
            }
            int next = nextLine(text, i);
            if (next < 0) {
                break;
            }
            lineStart = next;
        }
        return unit;
    }

    private static int nextLine(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                return i + 1;
            }
            if (c == '\r') {
                return i + 1 < text.length() && text.charAt(i + 1) == '\n' ? i + 2 : i + 1;
            }
        }
        return -1;
    }
}
