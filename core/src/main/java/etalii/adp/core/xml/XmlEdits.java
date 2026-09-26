package etalii.adp.core.xml;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import etalii.adp.core.TextChange;
import etalii.adp.core.TextChanges;

/**
 * Minimal edits to XML text, keeping quotes, indentation and line separators as they are (research
 * R20). Each method returns one {@link TextChange} against the text the element was read from.
 */
public final class XmlEdits {

    private XmlEdits() {
    }

    /** Replace the value, or insert the attribute after the last one when absent. */
    public static TextChange setAttribute(XmlElement e, String name, String value) {
        XmlAttribute attribute = e.attributes().get(name);
        if (attribute != null) {
            return TextChange.replace(attribute.valueRange().offset(), attribute.valueRange().length(), escapeAttribute(value, attribute.quote()));
        }
        char quote = e.attributes().isEmpty() ? '"' : e.attributes().values().iterator().next().quote();
        return TextChange.insert(e.attributeInsertPoint(), " " + name + "=" + quote + escapeAttribute(value, quote) + quote);
    }

    /**
     * Give the attribute this value: set it, remove it when {@code value} is {@code null} or empty,
     * or {@code null} ("nothing to do") when it already has that value.
     */
    public static TextChange update(XmlElement e, String name, String value) {
        if (value == null || value.isEmpty()) {
            return removeAttribute(e, name);
        }
        return value.equals(e.attribute(name)) ? null : setAttribute(e, name, value);
    }

    /**
     * Give the attribute a coordinate, written to two decimals, or {@code null} when it has that
     * value already, as stored or as it would be written, so an unmoved coordinate stored with more
     * decimals is left alone; an absent attribute counts as 0.
     */
    public static TextChange updateNumber(XmlElement e, String name, double value) {
        double current = e.number(name, 0);
        double rounded = Math.round(value * 100) / 100.0;
        return Math.abs(current - value) < 1e-6 || Math.abs(current - rounded) < 1e-6 ? null : setAttribute(e, name, number(rounded));
    }
    /** Remove the attribute with the whitespace before it, or {@code null} when it is absent. */
    public static TextChange removeAttribute(XmlElement e, String name) {
        XmlAttribute attribute = e.attributes().get(name);
        return attribute == null ? null : TextChange.delete(attribute.start(), attribute.end() - attribute.start());
    }

    /**
     * Insert {@code elementXml} as the child at {@code index}, on its own line with the siblings'
     * indentation (or the parent's plus one level) and the text's line separator.
     */
    public static TextChange insertChild(CharSequence text, XmlElement parent, int index, String elementXml) {
        String separator = lineSeparator(text);
        List<XmlElement> children = parent.children();
        String childIndent = children.isEmpty() ? parent.indent() + (parent.indent().contains("\t") ? "\t" : "  ")
                : children.get(Math.min(index, children.size() - 1)).indent();
        if (index < children.size()) {
            XmlElement next = children.get(index);
            return next.indent().isEmpty() ? TextChange.insert(next.range().offset(), elementXml)
                    : TextChange.insert(next.range().offset() - next.indent().length(), childIndent + elementXml + separator);
        }
        if (!children.isEmpty()) {
            XmlElement last = children.get(children.size() - 1);
            return TextChange.insert(last.range().end(), (last.indent().isEmpty() ? "" : separator + childIndent) + elementXml);
        }
        String block = separator + childIndent + elementXml + separator + parent.indent();
        if (parent.selfClosing()) {
            return TextChange.replace(parent.attributeInsertPoint(), parent.startTag().end() - parent.attributeInsertPoint(),
                    ">" + block + "</" + parent.name() + ">");
        }
        Range content = parent.content();
        return text.subSequence(content.offset(), content.end()).toString().isBlank()
                ? TextChange.replace(content.offset(), content.length(), block)
                : TextChange.insert(content.end(), elementXml);
    }

    /** Remove the element, with its whole line when nothing else is on it. */
    public static TextChange remove(CharSequence text, XmlElement e) {
        int start = e.range().offset();
        int end = e.range().end();
        int lineStart = start;
        while (lineStart > 0 && isBlank(text.charAt(lineStart - 1))) {
            lineStart--;
        }
        int lineEnd = end;
        while (lineEnd < text.length() && isBlank(text.charAt(lineEnd))) {
            lineEnd++;
        }
        boolean aloneBefore = lineStart == 0 || text.charAt(lineStart - 1) == '\n';
        boolean aloneAfter = lineEnd == text.length() || text.charAt(lineEnd) == '\n' || text.charAt(lineEnd) == '\r';
        if (!aloneBefore || !aloneAfter) {
            return TextChange.delete(start, end - start);
        }
        if (lineEnd < text.length() && text.charAt(lineEnd) == '\r') {
            lineEnd++;
        }
        if (lineEnd < text.length() && text.charAt(lineEnd) == '\n') {
            lineEnd++;
        }
        return TextChange.delete(lineStart, lineEnd - lineStart);
    }

    /** Replace the element's content with escaped text; a self-closing element gains an end tag. */
    public static TextChange setContent(XmlElement e, String value) {
        if (e.selfClosing()) {
            return TextChange.replace(e.attributeInsertPoint(), e.startTag().end() - e.attributeInsertPoint(),
                    ">" + escape(value) + "</" + e.name() + ">");
        }
        return TextChange.replace(e.content().offset(), e.content().length(), escape(value));
    }

    /** Escapes {@code &}, {@code <} and {@code >} for element content. */
    public static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Escapes for an attribute value in {@code quote}; line breaks and tabs become references so they survive a read. */
    public static String escapeAttribute(String text, char quote) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "&#10;").replace("\r", "&#13;").replace("\t", "&#9;");
        return quote == '"' ? escaped.replace("\"", "&quot;") : escaped.replace("'", "&apos;");
    }

    /**
     * An element as text: {@code <name a="1"/>}, or with escaped content when {@code content} is not
     * {@code null}. Attributes with a {@code null} value are left out.
     */
    public static String element(String name, Map<String, String> attributes, String content) {
        StringBuilder xml = new StringBuilder("<").append(name);
        attributes.forEach((attribute, value) -> {
            if (value != null) {
                xml.append(' ').append(attribute).append("=\"").append(escapeAttribute(value, '"')).append('"');
            }
        });
        return xml.append(content == null ? "/>" : ">" + escape(content) + "</" + name + ">").toString();
    }

    /** Attributes in the given order, from name and value pairs; a {@code null} value is left out by {@link #element}. */
    public static Map<String, String> attributes(String... namesAndValues) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            attributes.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return attributes;
    }
    /** {@code prefix} followed by the first number no {@code id} attribute under {@code root} uses. */
    public static String uniqueId(XmlElement root, String prefix) {
        Set<String> ids = new HashSet<>();
        root.descendants().forEach(element -> ids.add(element.attribute("id")));
        int n = 1;
        while (ids.contains(prefix + n)) {
            n++;
        }
        return prefix + n;
    }

    /** A number as an attribute value: whole numbers without a decimal point, others as short as they read back. */
    public static String number(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15 ? Long.toString((long) value)
                : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** The text's line separator: CRLF when it has one, else LF. */
    public static String lineSeparator(CharSequence text) {
        return text.toString().contains("\r\n") ? "\r\n" : "\n";
    }

    /** The given changes without the {@code null}s ("nothing to do"). */
    public static TextChanges changes(TextChange... changes) {
        return changes(Arrays.asList(changes));
    }

    /** The given changes without the {@code null}s ("nothing to do"). */
    public static TextChanges changes(Collection<TextChange> changes) {
        List<TextChange> kept = new ArrayList<>(changes);
        kept.removeIf(Objects::isNull);
        return new TextChanges(kept);
    }

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\t';
    }
}
