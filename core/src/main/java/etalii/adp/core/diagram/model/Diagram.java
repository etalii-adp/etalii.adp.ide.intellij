package etalii.adp.core.diagram.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The elements, connections and sectors read from one text, by key, in document order.
 * Immutable: every change to the text gives a new diagram (research R5).
 *
 * @param order the painting order of element and connection keys, bottom first, for formats whose
 *            file order is their stacking order (draw.io); empty paints connections under elements
 */
public record Diagram(Map<Object, Element> elements, Map<Object, Connection> connections, Map<Object, Sector> sectors, List<Object> order) {

    public static final Diagram EMPTY = new Diagram(Map.of(), Map.of(), Map.of());

    public Diagram {
        elements = Collections.unmodifiableMap(new LinkedHashMap<>(elements));
        connections = Collections.unmodifiableMap(new LinkedHashMap<>(connections));
        sectors = Collections.unmodifiableMap(new LinkedHashMap<>(sectors));
        order = List.copyOf(order);
    }

    /** Connections painted under elements. */
    public Diagram(Map<Object, Element> elements, Map<Object, Connection> connections, Map<Object, Sector> sectors) {
        this(elements, connections, sectors, List.of());
    }

    /** The same diagram painted in this order of element and connection keys, bottom first. */
    public Diagram withOrder(List<Object> paintOrder) {
        return new Diagram(elements, connections, sectors, paintOrder);
    }

    public static Diagram of(List<Element> elements, List<Connection> connections, List<Sector> sectors) {
        Map<Object, Element> e = new LinkedHashMap<>();
        elements.forEach(element -> e.put(element.key(), element));
        Map<Object, Connection> c = new LinkedHashMap<>();
        connections.forEach(connection -> c.put(connection.key(), connection));
        Map<Object, Sector> s = new LinkedHashMap<>();
        sectors.forEach(sector -> s.put(sector.key(), sector));
        return new Diagram(e, c, s);
    }

    public Element element(Object key) {
        return elements.get(key);
    }

    public Connection connection(Object key) {
        return connections.get(key);
    }

    public Sector sector(Object key) {
        return sectors.get(key);
    }

    /** The connections with an end at the element, in document order. */
    public List<Connection> connectionsOf(Object elementKey) {
        return connections.values().stream().filter(connection -> connection.touches(elementKey)).toList();
    }
}