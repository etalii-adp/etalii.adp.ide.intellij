package etalii.adp.fbl.rule;

import etalii.adp.fbl.text.Span;

/** A word of a lines or blocks group (FBL §4.6), with its span (quotes included) and its text (quotes excluded). */
public record Word(String text, Span span, boolean quoted) {
}
