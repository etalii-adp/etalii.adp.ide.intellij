package etalii.adp.core.xml;

/** A character range in the text a map was parsed from. */
public record Range(int offset, int length) {

    public int end() {
        return offset + length;
    }

    /** True when {@code other} lies within this range, bounds included. */
    public boolean covers(Range other) {
        return offset <= other.offset && other.end() <= end();
    }

    /** The text this range spans in {@code text}. */
    public String of(String text) {
        return text.substring(offset, end());
    }
}
