package etalii.adp.fbl.expression;

/** The result of an evaluation that failed: a missing key, a type mismatch, a division by zero. */
public record CelError(String message) {
}
