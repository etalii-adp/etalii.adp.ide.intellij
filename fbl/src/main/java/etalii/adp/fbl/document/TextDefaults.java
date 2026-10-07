package etalii.adp.fbl.document;

/**
 * How new text is written where the body does not show it (FBL §6.3).
 *
 * @param indent spaces per step, or 0 for a tab
 */
public record TextDefaults(String newline, int indent, boolean sequenceFlush, boolean finalNewline, String quote) {

    /** A line feed, two spaces, a final newline and double quotes. */
    public static final TextDefaults DEFAULT = new TextDefaults("\n", 2, false, true, "double");
}
