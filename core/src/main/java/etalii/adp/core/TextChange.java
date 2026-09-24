package etalii.adp.core;

/**
 * Replace {@code length} characters at {@code offset} with {@code replacement}. An insert has
 * length 0; a delete has an empty replacement.
 */
public record TextChange(int offset, int length, String replacement) {

    public TextChange {
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("A text change has a non-negative offset and length");
        }
        if (replacement == null) {
            throw new IllegalArgumentException("A text change has a replacement, empty for a delete");
        }
    }

    public static TextChange insert(int offset, String text) {
        return new TextChange(offset, 0, text);
    }

    public static TextChange delete(int offset, int length) {
        return new TextChange(offset, length, "");
    }

    public static TextChange replace(int offset, int length, String text) {
        return new TextChange(offset, length, text);
    }

    public int end() {
        return offset + length;
    }
}
