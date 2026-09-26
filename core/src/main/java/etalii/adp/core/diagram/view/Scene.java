package etalii.adp.core.diagram.view;

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.LabelSlot;
import etalii.adp.core.diagram.view.ArrowHeads.Mark;
import etalii.adp.core.diagram.view.ElementMeasure.PlacedText;

/**
 * Everything the canvas paints for one diagram, laid out once per re-read: each shown element and
 * connection with its geometry, in painting order, in unzoomed diagram coordinates.
 *
 * @param extent the union of everything drawn
 * @param order the diagram's painting order, bottom first; empty paints connections under elements
 */
public record Scene(Map<Object, ElementRender> elements, Map<Object, ConnectionRender> connections, Rectangle2D extent, List<Object> order) {

    public static final Scene EMPTY = new Scene(Map.of(), Map.of(), new Rectangle2D.Double(), List.of());

    public Scene {
        elements = Collections.unmodifiableMap(new LinkedHashMap<>(elements));
        connections = Collections.unmodifiableMap(new LinkedHashMap<>(connections));
        order = List.copyOf(order);
    }

    /**
     * One element laid out.
     *
     * @param type its declaration, or {@code null} for a placeholder
     * @param outline the outline for its bounds
     * @param extent what painting it touches: the bounds, texts above and below, anchors and selection
     */
    public record ElementRender(ElementView view, ElementType type, Rectangle2D bounds, Shape outline, List<PlacedText> texts,
            List<AnchorView> anchors, Rectangle2D extent) {

        public ElementRender {
            texts = List.copyOf(texts);
            anchors = List.copyOf(anchors);
        }
    }

    /**
     * One connection laid out.
     *
     * @param route the points it runs through
     * @param path the line as drawn: a polyline, or Béziers through the route for a curved line
     * @param sourceMark the mark at the source end, or {@code null}
     * @param targetMark the mark at the target end, or {@code null}
     * @param extent what painting it touches, labels included
     */
    public record ConnectionRender(ConnectionView view, List<Point2D> route, Shape path, Mark sourceMark, Mark targetMark,
            Map<LabelSlot, PlacedLabel> labels, Rectangle2D extent) {

        public ConnectionRender {
            route = List.copyOf(route);
            labels = Collections.unmodifiableMap(labels.isEmpty() ? new EnumMap<>(LabelSlot.class) : new EnumMap<>(labels));
        }
    }

    /** One connection label laid out in its box. */
    public record PlacedLabel(TextBlock block, Rectangle2D box) {
    }
}
