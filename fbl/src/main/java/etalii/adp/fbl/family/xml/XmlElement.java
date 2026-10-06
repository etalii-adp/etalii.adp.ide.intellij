package etalii.adp.fbl.family.xml;

import java.util.ArrayList;
import java.util.List;

import etalii.adp.fbl.rule.Entry;
import etalii.adp.fbl.text.Span;

/** An element (FBL §4.5): an entry whose own span runs from its start tag's {@code <} to its end tag's {@code >}. */
final class XmlElement extends Entry {

    private final boolean isRoot;
    private Span startTag = new Span(0, 0);
    private final int nameEnd;
    private Span close = new Span(0, 0);
    private boolean selfClosed;
    private Span endTag;
    private final List<XmlAttribute> attributes = new ArrayList<>();
    private final List<Object> content = new ArrayList<>();
    private String unreadable;

    /**
     * @param name the element's name; null for the root that holds the document element
     * @param nameEnd the end of the element's name in its start tag
     */
    XmlElement(Span own, String name, int indent, boolean isRoot, int nameEnd) {
        super(own, name, indent);
        this.isRoot = isRoot;
        this.nameEnd = nameEnd;
    }

    boolean isRoot() {
        return isRoot;
    }

    Span startTag() {
        return startTag;
    }

    void setStartTag(Span value) {
        startTag = value;
    }

    /** The end of the element's name in its start tag, where a first attribute is added. */
    int nameEnd() {
        return nameEnd;
    }

    /** The {@code >} that ends the start tag, or the {@code />} of a self-closed tag. */
    Span close() {
        return close;
    }

    void setClose(Span value) {
        close = value;
    }

    boolean selfClosed() {
        return selfClosed;
    }

    void setSelfClosed(boolean value) {
        selfClosed = value;
    }

    /** The end tag, or null when the element is self-closed or not closed yet. */
    Span endTag() {
        return endTag;
    }

    void setEndTag(Span value) {
        endTag = value;
    }

    int contentStart() {
        return startTag.end();
    }

    int contentEnd() {
        return endTag != null ? endTag.start() : startTag.end();
    }

    /** The attributes in the order they are written. The list is the element's own: add to it. */
    List<XmlAttribute> attributes() {
        return attributes;
    }

    /** Text runs and child elements in document order. The list is the element's own: add to it. */
    List<Object> content() {
        return content;
    }

    /** A reference to an entity other than the five predefined ones: the element is an unreadable entry. Null when it has none. */
    String unreadable() {
        return unreadable;
    }

    void setUnreadable(String value) {
        unreadable = value;
    }

    /** The child elements in document order. */
    List<XmlElement> elements() {
        List<XmlElement> elements = new ArrayList<>();
        for (Object item : content) {
            if (item instanceof XmlElement element) {
                elements.add(element);
            }
        }
        return elements;
    }

    /** The attribute of that name, or null. */
    XmlAttribute attribute(String name) {
        for (XmlAttribute attribute : attributes) {
            if (attribute.name().equals(name)) {
                return attribute;
            }
        }
        return null;
    }
}
