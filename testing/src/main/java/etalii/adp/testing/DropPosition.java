package etalii.adp.testing;

/**
 * Where {@link ToolDriver#dragOnto} drops an item on its target. The driver drops at a tenth of
 * the target's height from its top ({@code BEFORE}), at its centre ({@code ONTO}), or a tenth from
 * its bottom ({@code AFTER}); a format's drop handling reads the position the same way.
 */
public enum DropPosition {
    BEFORE, AFTER, ONTO
}
