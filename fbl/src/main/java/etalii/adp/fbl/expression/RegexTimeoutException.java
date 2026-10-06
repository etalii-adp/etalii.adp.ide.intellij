package etalii.adp.fbl.expression;

import java.time.Duration;

/** A match that did not finish within its bound (FBL §16). The caller reports it and reads the input as not matching. */
public final class RegexTimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String expression;
    private final Duration timeout;

    public RegexTimeoutException(String expression, Duration timeout) {
        super("The expression '" + expression + "' did not finish matching within " + timeout.toMillis() + " ms.");
        this.expression = expression;
        this.timeout = timeout;
    }

    /** The expression as the binding spells it. */
    public String expression() {
        return expression;
    }

    public Duration timeout() {
        return timeout;
    }
}
