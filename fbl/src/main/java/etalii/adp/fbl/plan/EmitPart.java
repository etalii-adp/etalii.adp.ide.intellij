package etalii.adp.fbl.plan;

/**
 * One part of an emit template: a placeholder or a literal, and the optional segment it is in (-1 for none).
 * The one of {@code placeholder} and {@code literal} the part is not is null.
 */
public record EmitPart(String placeholder, String literal, int segment) {
}
