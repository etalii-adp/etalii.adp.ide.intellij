package etalii.adp.core.diagram.view;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import etalii.adp.core.diagram.Outline;

/**
 * One element as it is drawn, for the test kit (data-model.md). Bounds are unzoomed diagram
 * coordinates; colours are the ones painted, after the file's own and the contrast plate.
 *
 * @param texts the text of each slot as drawn (wrapped lines joined by line breaks), by slot id
 * @param fill the fill, or {@code null} when the outline is not drawn
 * @param placeholder the type is not declared: drawn as a neutral dashed box, never edited
 * @param plate the plate drawn behind the texts when their colour would not read, or {@code null}
 */
public record ElementView(Object key, String type, Rectangle bounds, Map<String, String> texts, Color fill, Color border, Color text, Font font,
        boolean placeholder, Outline outline, Color plate) {

    public ElementView {
        bounds = new Rectangle(bounds);
        texts = Collections.unmodifiableMap(new LinkedHashMap<>(texts));
    }

    @Override
    public Rectangle bounds() {
        return new Rectangle(bounds);
    }
}
