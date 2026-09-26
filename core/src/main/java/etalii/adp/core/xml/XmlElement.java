package etalii.adp.core.xml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One element of an {@link XmlTree}, with the exact ranges minimal edits need.
 *
 * @param range from {@code <} of the start tag to {@code >} of the end tag
 * @param startTag the start tag; equal to {@code range} for a self-closing element
 * @param attributes in document order
 * @param children the child elements; text, comments and CDATA are not children
 * @param attributeInsertPoint just after the last attribute, or after the name when there is none
 * @param childInsertPoint just after the last child, or after the start tag when there is none
 * @param indent the whitespace before the start tag when it begins its line, else empty
 * @param content between the start and end tag; empty just after the start tag when self-closing
 * @param text the decoded text directly inside, CDATA included and comments left out
 */
public record XmlElement(String name, Range range, Range startTag, Map<String, XmlAttribute> attributes, List<XmlElement> children,
        int attributeInsertPoint, int childInsertPoint, String indent, Range content, String text) {

    public XmlElement {
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        children = List.copyOf(children);
    }

    public boolean selfClosing() {
        return range.equals(startTag);
    }

    /** The decoded value of the named attribute, or {@code null} when it is absent. */
    public String attribute(String attributeName) {
        XmlAttribute attribute = attributes.get(attributeName);
        return attribute == null ? null : attribute.value();
    }

    /** The decoded value of the named attribute, or {@code fallback} when it is absent. */
    public String attribute(String attributeName, String fallback) {
        String value = attribute(attributeName);
        return value == null ? fallback : value;
    }

    /** The named attribute as a number, or {@code fallback} when it is absent or not a number. */
    public double number(String attributeName, double fallback) {
        String value = attribute(attributeName);
        try {
            return value == null ? fallback : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The first child element with this name, or {@code null}. */
    public XmlElement child(String childName) {
        return children.stream().filter(child -> child.name.equals(childName)).findFirst().orElse(null);
    }
    /** This element and every element below it, in document order. */
    public List<XmlElement> descendants() {
        List<XmlElement> all = new ArrayList<>();
        collect(this, all);
        return all;
    }

    private static void collect(XmlElement element, List<XmlElement> all) {
        all.add(element);
        element.children.forEach(child -> collect(child, all));
    }
}
