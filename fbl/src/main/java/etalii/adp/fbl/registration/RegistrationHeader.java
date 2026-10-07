package etalii.adp.fbl.registration;

import etalii.adp.fbl.text.Span;

/** One {@code key: value} header of a registration (FBL §8.1), with the span of its whole line. */
public record RegistrationHeader(String key, String value, Span line) {
}
