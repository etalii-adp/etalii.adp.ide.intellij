package etalii.adp.fbl.text;

/** A range of UTF-8 byte offsets into a body, {@code end} exclusive. */
public record Span(int start, int end) {

    public int length() {
        return end - start;
    }

    public boolean contains(Span other) {
        return other.start >= start && other.end <= end;
    }

    @Override
    public String toString() {
        return "[" + start + ", " + end + ")";
    }
}
