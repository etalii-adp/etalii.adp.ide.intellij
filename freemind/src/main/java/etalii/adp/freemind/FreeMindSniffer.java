package etalii.adp.freemind;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import java.util.regex.Pattern;

/**
 * Decides from the start of a file whether it is a FreeMind map (FR-002, research R3): after an
 * optional UTF-8 byte order mark, an optional XML declaration, whitespace and comments, the root
 * start tag is {@code <map} with a {@code version} attribute. A DOCTYPE before it is skipped too, so
 * the designer can explain why it refuses one (FR-009) rather than leave the map unclaimed.
 * Anything else, such as Objective-C++ source, is rejected, so it opens as it would without the
 * plug-in. Only the first {@link #LIMIT} bytes are looked at; this never throws.
 */
public final class FreeMindSniffer {

    /** How much of a file is looked at, at most. */
    public static final int LIMIT = 4096;

    private static final byte[] BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    /** A {@code version} attribute, preceded by whitespace so {@code xversion} does not count. */
    private static final Pattern VERSION = Pattern.compile("\\sversion\\s*=\\s*[\"']");

    private FreeMindSniffer() {
    }

    /** True when {@code head}, the start of a file, is a FreeMind map. */
    public static boolean isFreeMind(byte[] head) {
        try {
            return head != null && rootIsMap(text(head));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The bytes one to one as characters, so offsets stay byte offsets; the markup looked for is ASCII. */
    private static String text(byte[] head) {
        int start = startsWith(head, BOM) ? BOM.length : 0;
        int end = Math.min(head.length, LIMIT);
        return start >= end ? "" : new String(head, start, end - start, ISO_8859_1);
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean rootIsMap(String text) {
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                i++;
            } else if (text.startsWith("<?", i)) {
                i = skipPast(text, "?>", i);
            } else if (text.startsWith("<!--", i)) {
                i = skipPast(text, "-->", i + 4);
            } else if (text.startsWith("<!DOCTYPE", i)) {
                i = skipDoctype(text, i);
            } else {
                return isMapTag(text, i);
            }
            if (i < 0) {
                return false;
            }
        }
        return false;
    }

    private static boolean isMapTag(String text, int start) {
        int nameEnd = start + "<map".length();
        if (!text.startsWith("<map", start) || nameEnd >= text.length() || !Character.isWhitespace(text.charAt(nameEnd))) {
            return false;
        }
        int end = text.indexOf('>', nameEnd);
        return end > 0 && VERSION.matcher(text.substring(nameEnd - 1, end)).find();
    }

    private static int skipPast(String text, String terminator, int from) {
        int end = text.indexOf(terminator, from);
        return end < 0 ? -1 : end + terminator.length();
    }

    /** Past a DOCTYPE, including an internal subset in brackets. */
    private static int skipDoctype(String text, int start) {
        int bracket = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') {
                bracket++;
            } else if (c == ']') {
                bracket--;
            } else if (c == '>' && bracket <= 0) {
                return i + 1;
            }
        }
        return -1;
    }
}
