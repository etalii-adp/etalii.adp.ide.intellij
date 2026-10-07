package etalii.adp.fbl.rule;

/** Thrown inside planning when a change cannot be made; the edit is refused with its message (FBL §6.4). */
public final class RefusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RefusedException(String reason) {
        super(reason);
    }
}
