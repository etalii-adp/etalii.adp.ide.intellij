package etalii.adp.core.diagram;

import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the framework asks the mapping to add. The mapping chooses the new key; the framework finds
 * the new element by diffing and selects it.
 *
 * @param bounds where it goes, sized from the type's sizing (a width or height of 0: auto-sized, not stored);
 *            {@code null} when dropped onto {@code target}
 * @param target the element it is dropped onto, or {@code null}
 * @param sector the sector at the drop point, or {@code null}
 * @param properties the type's default property values
 */
public record AddRequest(String type, Rectangle2D bounds, Object target, Object sector, Map<String, String> properties) {

    public AddRequest {
        bounds = bounds == null ? null : new Rectangle2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}