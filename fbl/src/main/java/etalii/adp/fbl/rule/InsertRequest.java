package etalii.adp.fbl.rule;

import java.util.Map;

import etalii.adp.fbl.document.Rule;

/**
 * What adding an element or relation needs to know (FBL §6.2).
 *
 * @param id the id of the new element, or null
 * @param values the values by attribute name; a value may be null
 * @param parent the element the new one is placed in, or null
 * @param source the element a new relation starts at, or null
 * @param target the element a new relation ends at, or null
 */
public record InsertRequest(Rule rule, String id, Map<String, Object> values, ReadElement parent, ReadElement source, ReadElement target) {
}
