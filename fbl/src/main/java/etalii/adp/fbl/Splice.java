package etalii.adp.fbl;

/** The replacement of the bytes {@code start} to {@code end} with {@code text}. */
public record Splice(SpliceOperation operation, int start, int end, String text) {

    @Override
    public String toString() {
        return operation.fblName() + " " + start + "-" + end + " \"" + text + "\"";
    }
}
