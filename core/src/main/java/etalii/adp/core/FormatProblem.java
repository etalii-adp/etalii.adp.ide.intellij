package etalii.adp.core;

/**
 * The document cannot be shown by the tool. The editor turns {@link #getOffset()} into a line
 * and column for the problem panel, and never modifies the document because of it.
 */
public class FormatProblem extends Exception {

    private static final long serialVersionUID = 1L;

    private final int offset;

    public FormatProblem(String message, int offset) {
        super(message);
        this.offset = offset;
    }

    /** The character offset in the document where the problem is. */
    public int getOffset() {
        return offset;
    }
}
