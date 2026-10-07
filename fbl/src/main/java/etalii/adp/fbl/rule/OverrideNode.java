package etalii.adp.fbl.rule;

import etalii.adp.fbl.document.Slot;

/**
 * A value read from an attribute's {@code override} slot, which writing removes (FBL §5.2).
 *
 * @param node the family's own node for the overriding slot, or null
 */
public record OverrideNode(Object node, Slot slot) {
}
