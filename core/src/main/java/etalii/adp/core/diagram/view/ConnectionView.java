package etalii.adp.core.diagram.view;

import java.awt.Color;
import java.awt.Point;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import etalii.adp.core.diagram.ArrowHead;
import etalii.adp.core.diagram.Dash;
import etalii.adp.core.diagram.LabelSlot;
import etalii.adp.core.diagram.LineStyle;

/**
 * One connection as it is drawn, for the test kit (data-model.md), after the file's overrides.
 *
 * @param route the points the line runs through, from the source end to the target end, in unzoomed diagram coordinates
 * @param labels the labels drawn, by slot; empty labels are left out
 * @param placeholder the type is not declared: drawn as a thin dashed grey line, never edited
 */
public record ConnectionView(Object key, String type, List<Point> route, LineStyle line, Dash dash, ArrowHead source, ArrowHead target,
        Map<LabelSlot, String> labels, boolean placeholder, Color color, float thickness) {

    public ConnectionView {
        route = route.stream().map(Point::new).toList();
        labels = Collections.unmodifiableMap(labels.isEmpty() ? new EnumMap<>(LabelSlot.class) : new EnumMap<>(labels));
    }

    @Override
    public List<Point> route() {
        return route.stream().map(Point::new).toList();
    }
}
