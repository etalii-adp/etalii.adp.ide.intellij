package etalii.adp.fbl.rule;

import java.util.List;

import etalii.adp.fbl.text.Span;

/**
 * What reading one slot of one entry gave.
 *
 * @param value the value read, or null
 * @param span where the value is written, or null when it has no place of its own
 * @param reason why the value is not writable, or null
 * @param node the family's own node for the slot (a yaml member, an xml attribute, a lines group), for writing; or null
 * @param words for a group read as words: every word of the group, so references inside it can be rewritten one by one; else null
 * @param quote the quote the value is written in on the wire, when the span includes one; else null
 * @param wire the value as written, before maps or conversion, for keeping a map's wire value; or null
 */
public record SlotRead(Object value, Span span, boolean present, boolean writable, String reason, Object node, List<Word> words, String quote, String wire) {

    public static final SlotRead ABSENT = new SlotRead(null, null, false, true);

    public SlotRead(Object value, Span span, boolean present, boolean writable) {
        this(value, span, present, writable, null);
    }

    public SlotRead(Object value, Span span, boolean present, boolean writable, String reason) {
        this(value, span, present, writable, reason, null, null, null, null);
    }

    public static SlotRead readOnlyAbsent(String reason) {
        return new SlotRead(null, null, false, false, reason);
    }

    public SlotRead withWritable(boolean value) {
        return new SlotRead(this.value, span, present, value, reason, node, words, quote, wire);
    }

    public SlotRead withReason(String value) {
        return new SlotRead(this.value, span, present, writable, value, node, words, quote, wire);
    }

    public SlotRead withNode(Object value) {
        return new SlotRead(this.value, span, present, writable, reason, value, words, quote, wire);
    }

    public SlotRead withWords(List<Word> value) {
        return new SlotRead(this.value, span, present, writable, reason, node, value, quote, wire);
    }

    public SlotRead withQuote(String value) {
        return new SlotRead(this.value, span, present, writable, reason, node, words, value, wire);
    }

    public SlotRead withWire(String value) {
        return new SlotRead(this.value, span, present, writable, reason, node, words, quote, value);
    }
}
