package etalii.adp.fbl.rule;

import etalii.adp.fbl.text.Span;

/** A mapping member, a sequence item, or the document root (FBL §4.1.1). */
public final class TreeEntry extends Entry {

    private final boolean root;
    private final Span keySpan;
    private TreeValue value;
    private int separator = -1;

    /**
     * @param name a member's key; null for an item or the root
     * @param indent the byte column of the entry's first byte on its line
     * @param keySpan a member's key as written, quotes included; null for an item or the root
     */
    public TreeEntry(Span own, String name, int indent, boolean root, Span keySpan, TreeValue value) {
        super(own, name, indent);
        this.root = root;
        this.keySpan = keySpan;
        this.value = value;
    }

    /** The document root. */
    public static TreeEntry root(Span own, int indent, TreeValue value) {
        return new TreeEntry(own, null, indent, true, null, value);
    }

    /** A mapping member. */
    public static TreeEntry member(String name, Span keySpan, Span own, int indent, TreeValue value) {
        return new TreeEntry(own, name, indent, false, keySpan, value);
    }

    /** A sequence item. */
    public static TreeEntry item(Span own, int indent, TreeValue value) {
        return new TreeEntry(own, null, indent, false, null, value);
    }

    public boolean isRoot() {
        return root;
    }

    /** A member's key as written, quotes included; null for an item or the root. */
    public Span keySpan() {
        return keySpan;
    }

    public TreeValue value() {
        return value;
    }

    public void setValue(TreeValue value) {
        this.value = value;
    }

    /** json: the offset of the comma after this entry, or -1. */
    public int separator() {
        return separator;
    }

    public void setSeparator(int value) {
        separator = value;
    }

    /** Whether the entry is a sequence item: neither the root nor a member. */
    public boolean isItem() {
        return !root && keySpan == null;
    }
}
