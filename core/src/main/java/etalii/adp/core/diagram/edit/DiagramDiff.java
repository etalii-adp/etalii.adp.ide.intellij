package etalii.adp.core.diagram.edit;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import etalii.adp.core.diagram.DiagramChange;
import etalii.adp.core.diagram.DiagramChange.Added;
import etalii.adp.core.diagram.DiagramChange.Connected;
import etalii.adp.core.diagram.DiagramChange.Disconnected;
import etalii.adp.core.diagram.DiagramChange.Moved;
import etalii.adp.core.diagram.DiagramChange.PropertyChanged;
import etalii.adp.core.diagram.DiagramChange.Removed;
import etalii.adp.core.diagram.DiagramChange.Resized;
import etalii.adp.core.diagram.DiagramChange.SectorChanged;
import etalii.adp.core.diagram.model.Connection;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;

/**
 * What changed between two successive diagrams, by key (research R8). The order is fixed: removed
 * elements, then removed connections, in the old order; then each element and each connection in
 * the new order, with its moves, sector and property changes (properties by id).
 */
public final class DiagramDiff {

    private DiagramDiff() {
    }

    /** @param before the previous diagram, or {@code null} when there was none */
    public static List<DiagramChange> diff(Diagram before, Diagram after) {
        Diagram old = before == null ? Diagram.EMPTY : before;
        List<DiagramChange> changes = new ArrayList<>();
        for (Object key : old.elements().keySet()) {
            Element next = after.element(key);
            if (next == null || !next.type().equals(old.element(key).type())) {
                changes.add(new Removed(key));
            }
        }
        for (Object key : old.connections().keySet()) {
            if (!after.connections().containsKey(key)) {
                changes.add(new Disconnected(key));
            }
        }
        for (Element element : after.elements().values()) {
            Element previous = old.element(element.key());
            if (previous == null || !previous.type().equals(element.type())) {
                changes.add(new Added(element.key()));
                continue;
            }
            Rectangle2D from = previous.bounds();
            Rectangle2D to = element.bounds();
            if (!Objects.equals(from, to) && from != null && to != null) {
                boolean resized = from.getWidth() != to.getWidth() || from.getHeight() != to.getHeight();
                changes.add(resized ? new Resized(element.key(), from, to) : new Moved(element.key(), from, to));
            } else if (!Objects.equals(previous.parent(), element.parent()) || !Objects.equals(from, to)) {
                changes.add(new Moved(element.key(), from, to));
            }
            if (!Objects.equals(previous.sector(), element.sector())) {
                changes.add(new SectorChanged(element.key(), previous.sector(), element.sector()));
            }
            properties(changes, element.key(), previous.properties(), element.properties());
        }
        for (Connection connection : after.connections().values()) {
            Connection previous = old.connection(connection.key());
            if (previous != null && (!previous.source().equals(connection.source()) || !previous.target().equals(connection.target()))) {
                changes.add(new Disconnected(connection.key()));
                previous = null;
            }
            if (previous == null) {
                changes.add(new Connected(connection.key(), connection.source(), connection.target()));
            } else {
                properties(changes, connection.key(), previous.properties(), connection.properties());
            }
        }
        return changes;
    }

    private static void properties(List<DiagramChange> changes, Object key, Map<String, String> from, Map<String, String> to) {
        if (from.equals(to)) {
            return;
        }
        TreeSet<String> ids = new TreeSet<>(from.keySet());
        ids.addAll(to.keySet());
        for (String id : ids) {
            if (!Objects.equals(from.get(id), to.get(id))) {
                changes.add(new PropertyChanged(key, id, from.get(id), to.get(id)));
            }
        }
    }
}
