package etalii.adp.core.xml;

import java.util.ArrayList;
import java.util.List;

import etalii.adp.core.FormatProblem;

/**
 * Lexes XML text into tokens that tile it exactly, recording the range of every start tag, end
 * tag, attribute name and value, comment, CDATA section and processing instruction (research R4).
 * No JDK parser reports attribute offsets, and minimal edits need them.
 *
 * <p>
 * It does not judge well-formedness: callers check that ({@link XmlTree} balances the tags). It only
 * fails on constructs it cannot delimit.
 */
public final class XmlScanner {

    public enum Kind {
        START_TAG, END_TAG, TEXT, COMMENT, CDATA, PI, DOCTYPE
    }

    /**
     * One attribute. {@code start} is the whitespace before its name; {@code value} excludes the
     * quotes.
     */
    public record Attribute(String name, int start, int nameOffset, Range value, char quote) {

        /** Just after the closing quote. */
        public int end() {
            return value.end() + 1;
        }

        /** The value with references decoded and literal whitespace normalised, as XML reads it. */
        public String decodedValue(String text) {
            return decode(normaliseWhitespace(value.of(text)));
        }
    }

    /**
     * One token. {@code name} is the element name for tags and the target for processing
     * instructions, else {@code null}. {@code attributes} is empty except for start tags.
     */
    public record Token(Kind kind, int offset, int length, String name, List<Attribute> attributes, boolean selfClosing) {

        public int end() {
            return offset + length;
        }

        public Range range() {
            return new Range(offset, length);
        }

        /** The named attribute, or {@code null}. */
        public Attribute attribute(String attributeName) {
            for (Attribute attribute : attributes) {
                if (attribute.name().equals(attributeName)) {
                    return attribute;
                }
            }
            return null;
        }

        /** The decoded value of the named attribute, or {@code null}. */
        public String value(String text, String attributeName) {
            Attribute attribute = attribute(attributeName);
            return attribute == null ? null : attribute.decodedValue(text);
        }
    }

    private final String text;
    private final List<Token> tokens = new ArrayList<>();
    private int position;

    private XmlScanner(String text) {
        this.text = text;
    }

    public static List<Token> scan(String text) throws FormatProblem {
        XmlScanner scanner = new XmlScanner(text);
        scanner.run();
        return scanner.tokens;
    }

    private void run() throws FormatProblem {
        while (position < text.length()) {
            int lt = text.indexOf('<', position);
            if (lt < 0) {
                add(Kind.TEXT, position, text.length(), null, List.of(), false);
                return;
            }
            if (lt > position) {
                add(Kind.TEXT, position, lt, null, List.of(), false);
            }
            position = lt;
            if (text.startsWith("<!--", lt)) {
                delimited(Kind.COMMENT, "-->", 4, null);
            } else if (text.startsWith("<![CDATA[", lt)) {
                delimited(Kind.CDATA, "]]>", 9, null);
            } else if (text.startsWith("<?", lt)) {
                delimited(Kind.PI, "?>", 2, nameAt(lt + 2));
            } else if (text.startsWith("<!", lt)) {
                doctype();
            } else if (text.startsWith("</", lt)) {
                String name = nameAt(lt + 2);
                int gt = text.indexOf('>', lt + 2 + name.length());
                if (gt < 0) {
                    throw new FormatProblem("The end tag </" + name + "> is not closed", lt);
                }
                add(Kind.END_TAG, lt, gt + 1, name, List.of(), false);
            } else {
                startTag();
            }
        }
    }

    private void delimited(Kind kind, String terminator, int openerLength, String name) throws FormatProblem {
        int start = position;
        int close = text.indexOf(terminator, start + openerLength);
        if (close < 0) {
            throw new FormatProblem("This " + kind.name().toLowerCase().replace('_', ' ') + " is not closed", start);
        }
        add(kind, start, close + terminator.length(), name, List.of(), false);
    }

    /** A DOCTYPE runs to the {@code >} outside quotes and outside its internal subset. */
    private void doctype() throws FormatProblem {
        int start = position;
        int depth = 0;
        char quote = 0;
        for (int i = start + 2; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == '>' && depth <= 0) {
                add(Kind.DOCTYPE, start, i + 1, nameAt(start + 2), List.of(), false);
                return;
            }
        }
        throw new FormatProblem("This declaration is not closed", start);
    }

    private void startTag() throws FormatProblem {
        int start = position;
        String name = nameAt(start + 1);
        if (name.isEmpty()) {
            throw new FormatProblem("A '<' does not start a tag", start);
        }
        int i = start + 1 + name.length();
        List<Attribute> attributes = new ArrayList<>();
        while (true) {
            int whitespace = i;
            i = skipWhitespace(i);
            if (i >= text.length()) {
                throw new FormatProblem("The start tag <" + name + "> is not closed", start);
            }
            char c = text.charAt(i);
            if (c == '>') {
                add(Kind.START_TAG, start, i + 1, name, attributes, false);
                return;
            }
            if (c == '/' && text.startsWith("/>", i)) {
                add(Kind.START_TAG, start, i + 2, name, attributes, true);
                return;
            }
            String attributeName = nameAt(i);
            if (attributeName.isEmpty()) {
                throw new FormatProblem("Unexpected '" + c + "' in the start tag <" + name + ">", i);
            }
            int nameOffset = i;
            i = skipWhitespace(i + attributeName.length());
            if (i >= text.length() || text.charAt(i) != '=') {
                throw new FormatProblem("The attribute " + attributeName + " has no value", nameOffset);
            }
            i = skipWhitespace(i + 1);
            char quote = i < text.length() ? text.charAt(i) : 0;
            if (quote != '"' && quote != '\'') {
                throw new FormatProblem("The value of " + attributeName + " is not quoted", i);
            }
            int close = text.indexOf(quote, i + 1);
            if (close < 0) {
                throw new FormatProblem("The value of " + attributeName + " is not closed", i);
            }
            attributes.add(new Attribute(attributeName, whitespace, nameOffset, new Range(i + 1, close - i - 1), quote));
            i = close + 1;
        }
    }

    private String nameAt(int offset) {
        int end = offset;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (Character.isWhitespace(c) || c == '/' || c == '>' || c == '=' || c == '?' || c == '<' || c == '[') {
                break;
            }
            end++;
        }
        return text.substring(offset, end);
    }

    private int skipWhitespace(int offset) {
        while (offset < text.length() && Character.isWhitespace(text.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    private void add(Kind kind, int start, int end, String name, List<Attribute> attributes, boolean selfClosing) {
        tokens.add(new Token(kind, start, end - start, name, List.copyOf(attributes), selfClosing));
        position = end;
    }

    /** Literal line breaks and tabs in an attribute value read as spaces; a CRLF is one space. */
    static String normaliseWhitespace(String raw) {
        if (raw.indexOf('\n') < 0 && raw.indexOf('\r') < 0 && raw.indexOf('\t') < 0) {
            return raw;
        }
        return raw.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    }

    /** Decodes the five predefined entities and numeric character references. */
    public static String decode(String raw) {
        int amp = raw.indexOf('&');
        if (amp < 0) {
            return raw;
        }
        StringBuilder out = new StringBuilder(raw.length());
        int i = 0;
        while (amp >= 0) {
            out.append(raw, i, amp);
            int semicolon = raw.indexOf(';', amp);
            String replacement = semicolon < 0 ? null : entity(raw.substring(amp + 1, semicolon));
            if (replacement == null) {
                out.append('&');
                i = amp + 1;
            } else {
                out.append(replacement);
                i = semicolon + 1;
            }
            amp = raw.indexOf('&', i);
        }
        return out.append(raw, i, raw.length()).toString();
    }

    /** A predefined entity or numeric reference, named without {@code &} and {@code ;}, or {@code null}. */
    public static String entity(String name) {
        switch (name) {
        case "amp":
            return "&";
        case "lt":
            return "<";
        case "gt":
            return ">";
        case "quot":
            return "\"";
        case "apos":
            return "'";
        default:
            return numericReference(name);
        }
    }

    private static String numericReference(String name) {
        if (name.length() < 2 || name.charAt(0) != '#') {
            return null;
        }
        try {
            boolean hex = name.charAt(1) == 'x' || name.charAt(1) == 'X';
            int codePoint = Integer.parseInt(name.substring(hex ? 2 : 1), hex ? 16 : 10);
            return Character.isValidCodePoint(codePoint) ? Character.toString(codePoint) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
