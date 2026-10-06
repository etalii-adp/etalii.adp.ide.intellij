package etalii.adp.fbl;

/** DISL's source location: the file relative to the subject, and the line, column and length of the entry's own span. */
public record SourceLocation(String file, int line, int column, int length) {

    @Override
    public String toString() {
        return file + ":" + line + ":" + column;
    }
}
