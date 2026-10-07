package etalii.adp.fbl.rule;

import etalii.adp.fbl.document.AttributeBinding;
import etalii.adp.fbl.document.Rule;

/**
 * One attribute to write, after the engine's checks: its binding, how it is read now, and its new value.
 *
 * @param value the new value, or null
 */
public record SlotChange(String attribute, AttributeBinding binding, Rule rule, SlotRead read, Object value, boolean isEmpty) {
}
