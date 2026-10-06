package etalii.adp.fbl.registration;

import java.util.List;

import etalii.adp.fbl.text.Span;

/** A block: its name line, its whole span, and its entries in order. */
public record RegistrationBlock(String name, Span nameLine, Span span, List<RegistrationEntry> entries) {

    public RegistrationBlock {
        entries = List.copyOf(entries);
    }
}
