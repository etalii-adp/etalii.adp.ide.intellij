package etalii.adp.fbl;

import etalii.adp.fbl.text.Span;

/** A view a block of the body defines (FBL §4.7): its name, the block rule that matched it, and where it is. */
public record FblView(String name, String block, Span span, int line) {
}
