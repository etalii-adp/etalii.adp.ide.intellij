package etalii.adp.core.diagram.model;

import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One connection as read from the file (data-model.md).
 *
 * @param type a declared connection type id, or an unknown one, which makes it a placeholder
 * @param waypoints points the line is drawn through, in diagram coordinates; preserved, not edited
 */
public record Connection(Object key, String type, End source, End target, Map<String, String> properties, StyleOverride style,
        List<Point2D> waypoints) {

    public Connection {
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        waypoints = waypoints.stream().map(p -> (Point2D) new Point2D.Double(p.getX(), p.getY())).toList();
    }

    /** The property's value, empty when the file has none. */
    public String property(String id) {
        return properties.getOrDefault(id, "");
    }

    /** True when either end is at the element. */
    public boolean touches(Object elementKey) {
        return source.elementKey().equals(elementKey) || target.elementKey().equals(elementKey);
    }
}