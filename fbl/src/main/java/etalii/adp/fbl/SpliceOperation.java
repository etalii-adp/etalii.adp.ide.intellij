package etalii.adp.fbl;

/** The eleven splice operations of FBL §6.1. Every write is one of these and nothing else. */
public enum SpliceOperation {
    REPLACE_VALUE("replace-value"),
    INSERT_KEY("insert-key"),
    REMOVE_KEY("remove-key"),
    INSERT_ENTRY("insert-entry"),
    REMOVE_ENTRY("remove-entry"),
    ENSURE_CONTAINER("ensure-container"),
    REMOVE_CONTAINER("remove-container"),
    REWRITE_REFERENCE("rewrite-reference"),
    RE_EMIT_LINE("re-emit-line"),
    OPEN_BLOCK("open-block"),
    SELF_CLOSE("self-close");

    private final String fblName;

    SpliceOperation(String fblName) {
        this.fblName = fblName;
    }

    /** The operation's name as FBL writes it. */
    public String fblName() {
        return fblName;
    }

    public static SpliceOperation parse(String name) {
        for (SpliceOperation operation : values()) {
            if (operation.fblName.equals(name)) {
                return operation;
            }
        }
        throw new IllegalArgumentException("'" + name + "' is not an FBL splice operation.");
    }
}
