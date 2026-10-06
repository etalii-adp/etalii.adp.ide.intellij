package etalii.adp.fbl.document;

/**
 * xml: how a missing child element holding the value is written.
 *
 * @param before the sibling the new child is written before, or null
 */
public record CreateChild(String emit, String place, String before) {
}
