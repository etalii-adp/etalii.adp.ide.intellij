package etalii.adp.freemind.model;

/**
 * Where one attribute sits in a start tag. {@code start} is the whitespace before its name, so
 * {@code [start, end)} removes it cleanly, and inserting {@code " NAME=\"v\""} at {@code start}
 * places a new attribute just before it.
 */
public record AttributeRange(int start, int end, Range value) {
}
