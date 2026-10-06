package etalii.adp.fbl.document;

/**
 * Where a rule's id comes from (FBL §5.3): a slot of the entry, or a key of the registration's
 * identities. The other is null.
 */
public record IdBinding(Slot from, String sidecarKey) {
}
