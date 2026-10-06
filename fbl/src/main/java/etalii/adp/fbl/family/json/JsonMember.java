package etalii.adp.fbl.family.json;

import etalii.adp.fbl.text.Span;

/**
 * A member of an object or an item of an array.
 *
 * @param name the member's name, decoded; null for an item
 * @param keySpan the name as written, quotes included; null for an item
 * @param value the value
 * @param separator the offset of the comma after this entry, or -1
 * @param duplicate whether an earlier member of the same object has this name
 */
public record JsonMember(String name, Span keySpan, JsonValue value, int separator, boolean duplicate) {
}
