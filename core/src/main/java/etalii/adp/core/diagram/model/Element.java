package etalii.adp.core.diagram.model;

import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One element as read from the file (data-model.md).
 *
 * @param key stable across re-reads, compared by value
 * @param type a declared element type id, or an unknown one, which makes the element a placeholder
 * @param bounds in diagram coordinates; {@code null} for laid-out designers. A width or height of 0
 *            means "not stored": the type's sizing decides it
 * @param properties values of the declared properties; a missing one shows empty
 * @param sector the key of the sector the file puts it in, or {@code null}
 * @param parent the key of its parent, for drop targets and layouts, or {@code null}
 * @param style looks the file stores, or {@code null}
 */
public record Element(Object key, String type, Rectangle2D bounds, Map<String, String> properties, Object sector, Object parent,
        StyleOverride style) {

    public Element {
        bounds = bounds == null ? null : new Rectangle2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    @Override
    public Rectangle2D bounds() {
        return bounds == null ? null : (Rectangle2D) bounds.clone();
    }

    /** The property's value, empty when the file has none. */
    public String property(String id) {
        return properties.getOrDefault(id, "");
    }
}