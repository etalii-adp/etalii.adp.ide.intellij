package etalii.adp.fbl.family.lines;

import etalii.adp.fbl.text.Span;

/** A named group of one statement as one rule's {@code line} matched it. */
record Group(String value, Span span) {
}
