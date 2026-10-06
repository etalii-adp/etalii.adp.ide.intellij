package etalii.adp.fbl.expression;

/** A CEL expression that does not compile, or an evaluation that failed. */
public final class CelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CelException(String message) {
        super(message);
    }
}
