package etalii.adp.fbl.document;

/** A problem in an FBL document, at the JSON Pointer of its location (FBL §14.1). */
public record Problem(String pointer, ProblemSeverity severity, String message) {

    @Override
    public String toString() {
        String name = switch (severity) {
            case INFO -> "Info";
            case WARNING -> "Warning";
            case ERROR -> "Error";
        };
        return name + " at " + (pointer.isEmpty() ? "/" : pointer) + ": " + message;
    }
}
