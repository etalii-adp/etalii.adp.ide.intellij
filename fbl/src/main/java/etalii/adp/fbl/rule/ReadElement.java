package etalii.adp.fbl.rule;

import java.util.LinkedHashMap;
import java.util.Map;

import etalii.adp.fbl.document.Rule;

/** An element or relation as the engine keeps it: the public element plus where each value lives. */
public final class ReadElement {

    private final Rule rule;
    private final Candidate candidate;
    private String id = "";
    private boolean idStored;
    private SlotRead idRead;
    private String key = "";
    private String keyAttribute;
    private final Map<String, SlotRead> slots = new LinkedHashMap<>();
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private ReadElement parent;
    private SlotRead sourceRead;
    private SlotRead targetRead;
    private ReadElement sourceElement;
    private ReadElement targetElement;
    private int line;

    public ReadElement(Rule rule, Candidate candidate) {
        this.rule = rule;
        this.candidate = candidate;
    }

    public Rule rule() {
        return rule;
    }

    public Candidate candidate() {
        return candidate;
    }

    public Entry entry() {
        return candidate.entry();
    }

    public String id() {
        return id;
    }

    public void setId(String value) {
        id = value;
    }

    public boolean idStored() {
        return idStored;
    }

    public void setIdStored(boolean value) {
        idStored = value;
    }

    /** How the id was read, or null. */
    public SlotRead idRead() {
        return idRead;
    }

    public void setIdRead(SlotRead value) {
        idRead = value;
    }

    /** The value references name this element by: its stored id, or the attribute its own rules reference it by. */
    public String key() {
        return key;
    }

    public void setKey(String value) {
        key = value;
    }

    /** The attribute whose value is {@link #key()}, when it is an attribute; else null. */
    public String keyAttribute() {
        return keyAttribute;
    }

    public void setKeyAttribute(String value) {
        keyAttribute = value;
    }

    /** How each attribute was read, by attribute name, in the order they were read. The map is the element's own: put into it. */
    public Map<String, SlotRead> slots() {
        return slots;
    }

    /** The value of each attribute, by attribute name, in the order they were read; a value may be null. The map is the element's own: put into it. */
    public Map<String, Object> attributes() {
        return attributes;
    }

    /** The element this one is in, or null. */
    public ReadElement parent() {
        return parent;
    }

    public void setParent(ReadElement value) {
        parent = value;
    }

    /** How a relation's source was read, or null. */
    public SlotRead sourceRead() {
        return sourceRead;
    }

    public void setSourceRead(SlotRead value) {
        sourceRead = value;
    }

    /** How a relation's target was read, or null. */
    public SlotRead targetRead() {
        return targetRead;
    }

    public void setTargetRead(SlotRead value) {
        targetRead = value;
    }

    /** The element a relation starts at, or null. */
    public ReadElement sourceElement() {
        return sourceElement;
    }

    public void setSourceElement(ReadElement value) {
        sourceElement = value;
    }

    /** The element a relation ends at, or null. */
    public ReadElement targetElement() {
        return targetElement;
    }

    public void setTargetElement(ReadElement value) {
        targetElement = value;
    }

    public int line() {
        return line;
    }

    public void setLine(int value) {
        line = value;
    }

    public boolean isRelation() {
        return rule.isRelation();
    }

    @Override
    public String toString() {
        return rule.name() + " " + id;
    }
}
