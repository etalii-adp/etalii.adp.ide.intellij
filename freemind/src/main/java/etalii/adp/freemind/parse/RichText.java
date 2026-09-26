package etalii.adp.freemind.parse;

import etalii.adp.core.xml.XmlScanner;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the HTML of a {@code richcontent} element into readable plain text (research R8): tags are
 * dropped, block tags and {@code <br>} become line breaks, source whitespace collapses as a browser
 * collapses it, and entities are decoded.
 */
public final class RichText {

    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)[^>]*>|<!--.*?-->|<![^>]*>|<\\?.*?\\?>", Pattern.DOTALL);
    private static final Set<String> BLOCKS = Set.of("p", "div", "li", "ul", "ol", "tr", "table", "h1", "h2", "h3", "h4", "h5",
            "h6", "blockquote", "pre", "dd", "dt", "dl", "body", "html", "hr");
    private static final Set<String> HIDDEN = Set.of("head", "style", "script", "title");

    // simplified: only the named entities FreeMind and Freeplane write in practice are decoded;
    // others are shown as written. Add names here if a real map shows them.
    private static final Map<String, String> NAMED = Map.ofEntries(Map.entry("nbsp", "\u00a0"), Map.entry("copy", "\u00a9"),
            Map.entry("reg", "\u00ae"), Map.entry("trade", "\u2122"), Map.entry("euro", "\u20ac"), Map.entry("hellip", "\u2026"),
            Map.entry("mdash", "\u2014"), Map.entry("ndash", "\u2013"), Map.entry("lsquo", "\u2018"), Map.entry("rsquo", "\u2019"),
            Map.entry("ldquo", "\u201c"), Map.entry("rdquo", "\u201d"), Map.entry("bull", "\u2022"), Map.entry("middot", "\u00b7"),
            Map.entry("laquo", "\u00ab"), Map.entry("raquo", "\u00bb"), Map.entry("deg", "\u00b0"), Map.entry("times", "\u00d7"),
            Map.entry("auml", "\u00e4"), Map.entry("ouml", "\u00f6"), Map.entry("uuml", "\u00fc"), Map.entry("Auml", "\u00c4"),
            Map.entry("Ouml", "\u00d6"), Map.entry("Uuml", "\u00dc"), Map.entry("szlig", "\u00df"), Map.entry("eacute", "\u00e9"),
            Map.entry("egrave", "\u00e8"), Map.entry("ecirc", "\u00ea"), Map.entry("aacute", "\u00e1"), Map.entry("agrave", "\u00e0"),
            Map.entry("acirc", "\u00e2"), Map.entry("ccedil", "\u00e7"), Map.entry("iacute", "\u00ed"), Map.entry("oacute", "\u00f3"),
            Map.entry("uacute", "\u00fa"), Map.entry("ntilde", "\u00f1"), Map.entry("Eacute", "\u00c9"));

    private RichText() {
    }

    public static String toText(String html) {
        StringBuilder out = new StringBuilder();
        Matcher tag = TAG.matcher(html);
        int hidden = 0;
        int i = 0;
        while (tag.find()) {
            if (hidden == 0) {
                appendText(out, html.substring(i, tag.start()));
            }
            String name = tag.group(2) == null ? "" : tag.group(2).toLowerCase();
            boolean closing = "/".equals(tag.group(1));
            if (HIDDEN.contains(name)) {
                hidden = Math.max(0, hidden + (closing ? -1 : tag.group().endsWith("/>") ? 0 : 1));
            } else if (hidden == 0 && name.equals("br")) {
                trimTrailingSpace(out);
                out.append('\n');
            } else if (hidden == 0 && BLOCKS.contains(name)) {
                trimTrailingSpace(out);
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
            }
            i = tag.end();
        }
        if (hidden == 0) {
            appendText(out, html.substring(i));
        }
        return out.toString().strip();
    }

    private static void appendText(StringBuilder out, String source) {
        StringBuilder collapsed = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
                boolean atLineStart = out.length() + collapsed.length() == 0
                        || (collapsed.length() > 0 ? collapsed.charAt(collapsed.length() - 1) : out.charAt(out.length() - 1)) == '\n';
                boolean afterSpace = collapsed.length() > 0 ? collapsed.charAt(collapsed.length() - 1) == ' '
                        : out.length() > 0 && out.charAt(out.length() - 1) == ' ';
                if (!atLineStart && !afterSpace) {
                    collapsed.append(' ');
                }
            } else {
                collapsed.append(c);
            }
        }
        out.append(decode(collapsed.toString()));
    }

    private static void trimTrailingSpace(StringBuilder out) {
        while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
            out.setLength(out.length() - 1);
        }
    }

    private static String decode(String text) {
        int amp = text.indexOf('&');
        if (amp < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (amp >= 0) {
            out.append(text, i, amp);
            int semicolon = text.indexOf(';', amp);
            String name = semicolon < 0 || semicolon - amp > 12 ? null : text.substring(amp + 1, semicolon);
            String replacement = name == null ? null : NAMED.get(name);
            if (replacement == null && name != null) {
                replacement = XmlScanner.entity(name);
            }
            if (replacement == null) {
                out.append('&');
                i = amp + 1;
            } else {
                out.append(replacement);
                i = semicolon + 1;
            }
            amp = text.indexOf('&', i);
        }
        return out.append(text, i, text.length()).toString();
    }
}
