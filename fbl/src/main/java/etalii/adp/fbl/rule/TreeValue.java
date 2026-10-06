package etalii.adp.fbl.rule;

import java.util.ArrayList;
import java.util.List;

import etalii.adp.fbl.text.Span;

/** A value of a yaml or json tree (FBL §4.1.1): a scalar, a mapping or a sequence, with its span as written. */
public final class TreeValue {

    private final ValueKind kind;
    private final ValueStyle style;
    private Span span;
    private final String text;
    private final Object typed;
    private final List<TreeEntry> entries = new ArrayList<>();
    private final Object flow;
    private final boolean viaAlias;
    private final List<TreeValue> merged = new ArrayList<>();

    /** A value with no text: a collection, to which its entries are added. */
    public TreeValue(ValueKind kind, ValueStyle style, Span span) {
        this(kind, style, span, "", null, null, false);
    }

    /** A scalar with its text and its typed value. */
    public TreeValue(ValueKind kind, ValueStyle style, Span span, String text, Object typed) {
        this(kind, style, span, text, typed, null, false);
    }

    /**
     * @param text a scalar's text, decoded; empty for a collection
     * @param typed a scalar's value: a String, a Long, a Double, a Boolean or null
     * @param flow a flow collection's members as CEL sees them, or null
     * @param viaAlias whether the value is reached through an alias or a merge key
     */
    public TreeValue(ValueKind kind, ValueStyle style, Span span, String text, Object typed, Object flow, boolean viaAlias) {
        this.kind = kind;
        this.style = style;
        this.span = span;
        this.text = text;
        this.typed = typed;
        this.flow = flow;
        this.viaAlias = viaAlias;
    }

    public ValueKind kind() {
        return kind;
    }

    public ValueStyle style() {
        return style;
    }

    /** The value as written: quotes included; a block collection from its first to its last entry's last byte. */
    public Span span() {
        return span;
    }

    public void setSpan(Span value) {
        span = value;
    }

    /** A scalar's text, decoded. */
    public String text() {
        return text;
    }

    /** A scalar's value by the YAML 1.2 core schema or JSON's types: a String, a Long, a Double, a Boolean or null. */
    public Object typed() {
        return typed;
    }

    /** The members of a mapping or the items of a sequence, in order. The list is the value's own: add to it. */
    public List<TreeEntry> entries() {
        return entries;
    }

    /** A flow collection's members as CEL sees them: readable, but writable only as a whole (FBL §4.3). Null for any other value. */
    public Object flow() {
        return flow;
    }

    /** Reached through an alias or a merge key: readable, never writable (FBL §4.3). */
    public boolean viaAlias() {
        return viaAlias;
    }

    /** yaml: the mappings a {@code <<} key merges into this one, for reading. The list is the value's own: add to it. */
    public List<TreeValue> merged() {
        return merged;
    }

    /** The first member with that key, or null. */
    public TreeEntry member(String key) {
        for (TreeEntry entry : entries) {
            if (key.equals(entry.name())) {
                return entry;
            }
        }
        return null;
    }
}
