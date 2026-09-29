package etalii.adp.core;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.List;

/**
 * One laid-out item as the tool draws it, in unzoomed view coordinates. The test kit reads it
 * to check what is shown without a screen.
 *
 * @param key the format's key for the item
 * @param bounds the box the item is drawn in
 * @param foreground the text colour, the file's own or the theme's
 * @param background the fill, or {@code null} when the item has none
 * @param icons the icon glyphs, in file order
 * @param hasLink whether a link indicator is drawn
 * @param hasNote whether a note indicator is drawn
 * @param folded whether the item is drawn collapsed
 */
public record NodeView(Object key, Rectangle bounds, String text, Color foreground, Color background, Font font, List<String> icons,
        boolean hasLink, boolean hasNote, boolean folded) {

    public NodeView {
        bounds = new Rectangle(bounds);
        icons = List.copyOf(icons);
    }

    @Override
    public Rectangle bounds() {
        return new Rectangle(bounds);
    }
}
