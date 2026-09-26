package etalii.adp.core.xml;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import etalii.adp.core.FormatProblem;
import etalii.adp.core.xml.XmlScanner.Attribute;
import etalii.adp.core.xml.XmlScanner.Token;

/**
 * The elements of an XML text with their exact ranges, built on {@link XmlScanner} (research R20).
 * It checks that tags balance and that there is one root element; it does not validate further.
 */
public final class XmlTree {

    private final XmlElement root;

    private XmlTree(XmlElement root) {
        this.root = root;
    }

    public XmlElement root() {
        return root;
    }

    /** {@link #of} for a text that was read before, so it parses: for building edits on it. */
    public static XmlTree reread(CharSequence text) {
        try {
            return of(text);
        } catch (FormatProblem problem) {
            throw new IllegalStateException("An edit was asked for a text that cannot be read", problem);
        }
    }
    /** Throws {@link FormatProblem} at the offending offset when the tags do not balance. */
    public static XmlTree of(CharSequence source) throws FormatProblem {
        String text = source.toString();
        Deque<Open> open = new ArrayDeque<>();
        XmlElement root = null;
        for (Token token : XmlScanner.scan(text)) {
            switch (token.kind()) {
            case START_TAG -> {
                if (open.isEmpty() && root != null) {
                    throw new FormatProblem("There is more than one root element", token.offset());
                }
                Open element = new Open(token, text);
                if (token.selfClosing()) {
                    XmlElement closed = element.close(text, token.range(), new Range(token.end(), 0));
                    if (open.isEmpty()) {
                        root = closed;
                    } else {
                        open.peek().children.add(closed);
                    }
                } else {
                    open.push(element);
                }
            }
            case END_TAG -> {
                if (open.isEmpty()) {
                    throw new FormatProblem("The end tag </" + token.name() + "> has no start tag", token.offset());
                }
                Open element = open.pop();
                if (!element.token.name().equals(token.name())) {
                    throw new FormatProblem("The end tag </" + token.name() + "> does not close <" + element.token.name() + ">",
                            token.offset());
                }
                XmlElement closed = element.close(text, new Range(element.token.offset(), token.end() - element.token.offset()),
                        new Range(element.token.end(), token.offset() - element.token.end()));
                if (open.isEmpty()) {
                    root = closed;
                } else {
                    open.peek().children.add(closed);
                }
            }
            case TEXT -> {
                if (!open.isEmpty()) {
                    open.peek().text.append(XmlScanner.decode(text.substring(token.offset(), token.end())));
                } else if (!text.substring(token.offset(), token.end()).isBlank()) {
                    throw new FormatProblem("There is text outside the root element", token.offset());
                }
            }
            case CDATA -> {
                if (!open.isEmpty()) {
                    open.peek().text.append(text, token.offset() + "<![CDATA[".length(), token.end() - "]]>".length());
                }
            }
            default -> {
                // comments, processing instructions and the DOCTYPE are not part of the tree
            }
            }
        }
        if (!open.isEmpty()) {
            throw new FormatProblem("The element <" + open.peek().token.name() + "> is not closed", open.peek().token.offset());
        }
        if (root == null) {
            throw new FormatProblem("There is no root element", 0);
        }
        return new XmlTree(root);
    }

    /** An element whose end tag is not reached yet. */
    private static final class Open {

        final Token token;
        final List<XmlElement> children = new ArrayList<>();
        final StringBuilder text = new StringBuilder();
        final String indent;

        Open(Token token, String source) {
            this.token = token;
            this.indent = indentOf(source, token.offset());
        }

        XmlElement close(String source, Range range, Range content) {
            Map<String, XmlAttribute> attributes = new LinkedHashMap<>();
            int attributeInsertPoint = token.offset() + 1 + token.name().length();
            for (Attribute attribute : token.attributes()) {
                attributes.put(attribute.name(), new XmlAttribute(attribute.name(), attribute.start(), attribute.value(), attribute.quote(),
                        attribute.decodedValue(source)));
                attributeInsertPoint = attribute.end();
            }
            int childInsertPoint = children.isEmpty() ? token.end() : children.get(children.size() - 1).range().end();
            return new XmlElement(token.name(), range, token.range(), attributes, children, attributeInsertPoint, childInsertPoint, indent,
                    content, text.toString());
        }
    }

    /**
     * The name of the first element in the start of a file, after a byte order mark, the XML
     * declaration, comments, a DOCTYPE and whitespace; {@code null} when there is none. For
     * sniffing: it reads only what it is given and never throws.
     */
    public static String rootName(byte[] head) {
        String start = new String(head, StandardCharsets.UTF_8);
        int at = start.startsWith("﻿") ? 1 : 0;
        while (true) {
            while (at < start.length() && Character.isWhitespace(start.charAt(at))) {
                at++;
            }
            String close = start.startsWith("<?", at) ? "?>" : start.startsWith("<!--", at) ? "-->" : start.startsWith("<!", at) ? ">" : null;
            if (close == null) {
                break;
            }
            int end = start.indexOf(close, at + 2);
            if (end < 0) {
                return null;
            }
            at = end + close.length();
        }
        if (!start.startsWith("<", at)) {
            return null;
        }
        int end = at + 1;
        while (end < start.length() && !Character.isWhitespace(start.charAt(end)) && "/>".indexOf(start.charAt(end)) < 0) {
            end++;
        }
        return end > at + 1 ? start.substring(at + 1, end) : null;
    }

    /** The spaces and tabs before {@code offset} when only they precede it on its line, else empty. */
    static String indentOf(String text, int offset) {
        int start = offset;
        while (start > 0 && (text.charAt(start - 1) == ' ' || text.charAt(start - 1) == '\t')) {
            start--;
        }
        boolean lineStart = start == 0 || text.charAt(start - 1) == '\n' || text.charAt(start - 1) == '\r';
        return lineStart ? text.substring(start, offset) : "";
    }
}
