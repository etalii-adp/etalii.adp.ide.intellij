package etalii.adp.fbl.rule;

import java.util.ArrayList;
import java.util.List;

import etalii.adp.fbl.text.Span;

/**
 * A node a rule can match (FBL §4.1.1): its own span, its line span when it starts and ends its
 * lines, and the entries it contains. Two entries are equal only when they are the same entry.
 */
public abstract class Entry {

    private Span own;
    private Span lineSpan;
    private Entry parent;
    private final List<Entry> children = new ArrayList<>();
    private final String name;
    private final int indent;

    /**
     * @param name the key of a member, the name of an xml element; null for a sequence item or a statement
     * @param indent the byte column of the entry's first byte on its line
     */
    protected Entry(Span own, String name, int indent) {
        this.own = own;
        this.name = name;
        this.indent = indent;
    }

    public final Span own() {
        return own;
    }

    public final void setOwn(Span value) {
        own = value;
    }

    /** The line span (FBL §4.1.1), leading comments included; null when the entry shares a line. */
    public final Span lineSpan() {
        return lineSpan;
    }

    public final void setLineSpan(Span value) {
        lineSpan = value;
    }

    /** The entry this one is in, or null. */
    public final Entry parent() {
        return parent;
    }

    public final void setParent(Entry value) {
        parent = value;
    }

    /** The entries this one contains, in order. The list is the entry's own: add to it to add a child. */
    public final List<Entry> children() {
        return children;
    }

    /** The key of a member, the name of an xml element; null for a sequence item or a statement. */
    public final String name() {
        return name;
    }

    /** The byte column of the entry's first byte on its line. */
    public final int indent() {
        return indent;
    }

    /** The span a removal takes: the line span when there is one, else the own span. */
    public final Span removalSpan() {
        return lineSpan != null ? lineSpan : own;
    }

    @Override
    public final boolean equals(Object other) {
        return this == other;
    }

    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + (name == null ? "" : name) + " " + own;
    }
}
