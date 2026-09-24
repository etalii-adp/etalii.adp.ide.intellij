package etalii.adp.testing;

/**
 * Where {@link DesignerDriver#dragOnto} drops a node on its target. The driver drops at a tenth of
 * the target figure's height from its top ({@code BEFORE}), at its centre ({@code ONTO}), or a tenth
 * from its bottom ({@code AFTER}); a format's drop policy reads the position the same way.
 */
public enum DropPosition {
    BEFORE, AFTER, ONTO
}
