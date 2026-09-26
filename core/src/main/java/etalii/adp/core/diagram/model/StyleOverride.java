package etalii.adp.core.diagram.model;

import java.awt.Color;
import java.awt.Font;

import etalii.adp.core.diagram.ArrowHead;
import etalii.adp.core.diagram.Dash;
import etalii.adp.core.diagram.LineStyle;

/**
 * Looks the file stores for one item, overriding its type's declaration. Every field may be
 * {@code null}, meaning "as declared". The line fields apply to connections only.
 *
 * @param font the font an element's texts are drawn in, sized for the canvas as the element's
 *            bounds are; a text slot's style still applies to it
 * @param html the item's texts are HTML, drawn as such (bold, italic, line breaks, paragraphs, rules, entities)
 */
public record StyleOverride(Color fill, Color border, Color text, LineStyle line, Dash dash, ArrowHead sourceArrow, ArrowHead targetArrow,
        Float thickness, Font font, boolean html) {

    /** A colour written {@code #RRGGBB}, or {@code null} for anything else. */
    public static Color colour(String hex) {
        return hex != null && hex.matches("#[0-9A-Fa-f]{6}") ? Color.decode(hex) : null;
    }

    /** Colours only. */
    public static StyleOverride colours(Color fill, Color border, Color text) {
        return new StyleOverride(fill, border, text, null, null, null, null, null, null, false);
    }

    /** The same looks, with the item's texts drawn as HTML or not. */
    public StyleOverride withHtml(boolean asHtml) {
        return new StyleOverride(fill, border, text, line, dash, sourceArrow, targetArrow, thickness, font, asHtml);
    }
}