package etalii.adp.fbl.document;

import java.util.Objects;

/**
 * A slot (FBL §5.2): exactly one place a value is read from and written to. Exactly one of the
 * slot kinds is set and the others are null or false; {@code child}, {@code word} and {@code flag}
 * qualify it.
 */
public sealed class Slot permits AttributeBinding {

    private final String key;
    private final String xmlAttribute;
    private final boolean text;
    private final String child;
    private final String group;
    private final String parent;
    private final String capture;
    private final String value;
    private final String word;
    private final boolean flag;

    public Slot(String key, String xmlAttribute, boolean text, String child, String group, String parent, String capture, String value, String word,
            boolean flag) {
        this.key = key;
        this.xmlAttribute = xmlAttribute;
        this.text = text;
        this.child = child;
        this.group = group;
        this.parent = parent;
        this.capture = capture;
        this.value = value;
        this.word = word;
        this.flag = flag;
    }

    /** A slot that is only a key. */
    public static Slot ofKey(String key) {
        return new Slot(key, null, false, null, null, null, null, null, null, false);
    }

    public String key() {
        return key;
    }

    public String xmlAttribute() {
        return xmlAttribute;
    }

    public boolean text() {
        return text;
    }

    public String child() {
        return child;
    }

    public String group() {
        return group;
    }

    public String parent() {
        return parent;
    }

    public String capture() {
        return capture;
    }

    /** The CEL expression of a computed slot, or null. */
    public String value() {
        return value;
    }

    public String word() {
        return word;
    }

    public boolean flag() {
        return flag;
    }

    public boolean isComputed() {
        return value != null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Slot slot && getClass() == other.getClass() && text == slot.text && flag == slot.flag && Objects.equals(key, slot.key)
                && Objects.equals(xmlAttribute, slot.xmlAttribute) && Objects.equals(child, slot.child) && Objects.equals(group, slot.group)
                && Objects.equals(parent, slot.parent) && Objects.equals(capture, slot.capture) && Objects.equals(value, slot.value)
                && Objects.equals(word, slot.word);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, xmlAttribute, text, child, group, parent, capture, value, word, flag);
    }

    @Override
    public String toString() {
        return key != null ? "key " + key
                : xmlAttribute != null ? "attribute " + xmlAttribute
                : text ? "text"
                : group != null ? "group " + group
                : parent != null ? "parent " + parent
                : capture != null ? "capture " + capture
                : value != null ? "value" : "slot";
    }
}
