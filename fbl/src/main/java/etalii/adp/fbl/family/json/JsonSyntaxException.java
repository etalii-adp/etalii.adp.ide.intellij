package etalii.adp.fbl.family.json;

/** A JSON text that is not well-formed, with the byte offset of the cause. */
public final class JsonSyntaxException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int offset;

    public JsonSyntaxException(int offset, String message) {
        super(message);
        this.offset = offset;
    }

    public int offset() {
        return offset;
    }
}
