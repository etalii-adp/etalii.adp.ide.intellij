package etalii.adp.fbl.expression;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The subset of CEL this library evaluates: literals, member access and indexing, the logical,
 * relational and arithmetic operators, {@code in}, the conditional, {@code has()}, the macros
 * {@code all}, {@code exists}, {@code exists_one}, {@code filter} and {@code map}, and the functions
 * {@code size}, {@code matches}, {@code startsWith}, {@code endsWith}, {@code contains}, {@code replace},
 * {@code lowerAscii}, {@code upperAscii}, {@code int}, {@code double} and {@code string}. Any other
 * construct is refused when the expression is compiled, naming the construct, so a binding that
 * needs more fails at load rather than at read.
 */
public final class CelCompiler {

    private CelCompiler() {
    }

    private static List<String> variables(CelContext context) {
        return switch (context) {
            case TREE -> List.of("entry", "parent", "path", "line", "registration");
            case LINES -> List.of("entry", "parent", "groups", "line", "registration");
            case INSERT -> List.of("attributes");
        };
    }

    /** Throws {@link CelException} when the expression is outside the subset or names a variable its context does not have. */
    public static CelProgram compile(String expression, CelContext context) {
        CelParser parser = new CelParser(expression);
        CelNode node = parser.parseExpression();
        parser.expectEnd();
        check(node, variables(context), new HashSet<>());
        return new CelProgram(expression, node);
    }

    private static void check(CelNode node, List<String> variables, Set<String> bound) {
        switch (node) {
            case CelNode.Ident ident -> {
                if (!variables.contains(ident.name()) && !bound.contains(ident.name())) {
                    throw new CelException("'" + ident.name() + "' is not a variable here; available: " + String.join(", ", variables) + ".");
                }
            }
            case CelNode.Macro macro -> {
                check(macro.target(), variables, bound);
                Set<String> inner = new HashSet<>(bound);
                inner.add(macro.variable());
                check(macro.body(), variables, inner);
            }
            default -> {
                for (CelNode child : node.children()) {
                    check(child, variables, bound);
                }
            }
        }
    }
}
