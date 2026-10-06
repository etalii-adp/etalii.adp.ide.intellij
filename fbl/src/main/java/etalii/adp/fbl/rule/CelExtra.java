package etalii.adp.fbl.rule;

/**
 * A variable a family gives the CEL of a rule besides the ones every family gives: {@code path} or {@code groups}.
 *
 * @param value the value of the variable; may be null
 */
public record CelExtra(String name, Object value) {
}
