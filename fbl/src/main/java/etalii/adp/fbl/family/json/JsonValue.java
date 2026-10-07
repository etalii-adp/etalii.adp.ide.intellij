package etalii.adp.fbl.family.json;

import java.util.ArrayList;
import java.util.List;

import etalii.adp.fbl.text.Span;

/**
 * A value of a JSON text with its span as written. An object keeps every member in document order,
 * a repeated name included and marked; an array keeps its items the same way, without names.
 */
public final class JsonValue {

    public enum Kind {
        OBJECT,
        ARRAY,
        STRING,
        NUMBER,
        TRUE,
        FALSE,
        NULL,
    }

    private final Kind kind;
    private final Span span;
    private final String text;
    private final Object typed;
    private final List<JsonMember> members;

    JsonValue(Kind kind, Span span, String text, Object typed, List<JsonMember> members) {
        this.kind = kind;
        this.span = span;
        this.text = text;
        this.typed = typed;
        this.members = List.copyOf(members);
    }

    public Kind kind() {
        return kind;
    }

    /** The value as written: a string with its quotes, a container from its opening to its closing bracket. */
    public Span span() {
        return span;
    }

    /** A string decoded; a number or literal as written; empty for a container. */
    public String text() {
        return text;
    }

    /** A string, a {@link Long}, a {@link Double}, a {@link Boolean} or null; null for a container. */
    public Object typed() {
        return typed;
    }

    /** The members of an object or the items of an array, in document order. */
    public List<JsonMember> members() {
        return members;
    }

    /** An object's members without the repeats of a name, or an array's items. */
    public List<JsonMember> distinctMembers() {
        List<JsonMember> distinct = new ArrayList<>(members.size());
        for (JsonMember member : members) {
            if (!member.duplicate()) {
                distinct.add(member);
            }
        }
        return distinct;
    }

    public boolean isObject() {
        return kind == Kind.OBJECT;
    }

    public boolean isArray() {
        return kind == Kind.ARRAY;
    }

    public boolean isString() {
        return kind == Kind.STRING;
    }

    public boolean isNumber() {
        return kind == Kind.NUMBER;
    }

    /** The first member of that name, or null: also null when this is not an object. */
    public JsonValue get(String name) {
        if (kind != Kind.OBJECT) {
            return null;
        }
        for (JsonMember member : members) {
            if (name.equals(member.name())) {
                return member.value();
            }
        }
        return null;
    }

    /** The string value of the member of that name, or null when it is missing or not a string. */
    public String string(String name) {
        JsonValue value = get(name);
        return value != null && value.kind == Kind.STRING ? value.text : null;
    }

    /** Whether the member of that name is the literal {@code true}. */
    public boolean isTrue(String name) {
        JsonValue value = get(name);
        return value != null && value.kind == Kind.TRUE;
    }

    @Override
    public String toString() {
        return kind + " " + span;
    }
}
