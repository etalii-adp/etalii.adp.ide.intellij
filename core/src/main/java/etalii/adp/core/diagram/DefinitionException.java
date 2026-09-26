package etalii.adp.core.diagram;

import java.util.List;

/** A definition that is inconsistent, with every problem, each naming its declaration (FR-002). */
public final class DefinitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<String> problems;

    public DefinitionException(List<String> problems) {
        super("The diagram definition has problems:\n  " + String.join("\n  ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}