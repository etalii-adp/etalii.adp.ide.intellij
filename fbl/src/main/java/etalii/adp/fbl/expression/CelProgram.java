package etalii.adp.fbl.expression;

import java.util.Map;

/** A compiled CEL expression. */
public final class CelProgram {

    private static final int STEP_BUDGET = 100_000;

    /** The steps one evaluation has taken. */
    static final class Steps {

        private int count;
    }

    private final String source;
    private final CelNode root;

    CelProgram(String source, CelNode root) {
        this.source = source;
        this.root = root;
    }

    public String source() {
        return source;
    }

    CelNode root() {
        return root;
    }

    /** Evaluates with {@code variables}; a value of {@link CelError} when evaluation fails. The result may be null. */
    public Object evaluate(Map<String, Object> variables) {
        Steps steps = new Steps();
        try {
            return root.evaluate(new CelScope(variables, null), steps);
        } catch (CelException e) {
            return new CelError(e.getMessage());
        }
    }

    /** True only when the expression evaluates to true. */
    public boolean isTrue(Map<String, Object> variables) {
        return Boolean.TRUE.equals(evaluate(variables));
    }

    static void step(Steps steps) {
        if (++steps.count > STEP_BUDGET) {
            throw new CelException("The expression exceeded its evaluation budget.");
        }
    }
}
