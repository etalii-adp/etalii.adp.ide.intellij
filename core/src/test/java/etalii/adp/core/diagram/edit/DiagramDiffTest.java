package etalii.adp.core.diagram.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

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
import etalii.adp.core.diagram.model.End;

class DiagramDiffTest {

    private static Element box(String key, double x, double y, double w, double h, Map<String, String> properties, Object sector) {
        return new Element(key, "task", new Rectangle2D.Double(x, y, w, h), properties, sector, null, null);
    }

    private static Element box(String key, double x, double y) {
        return box(key, x, y, 100, 50, Map.of(), null);
    }

    private static Connection link(String key, String from, String to, Map<String, String> properties) {
        return new Connection(key, "flow", new End(from, "out"), new End(to, "in"), properties, null, List.of());
    }

    private static Diagram diagram(List<Element> elements, List<Connection> connections) {
        return Diagram.of(elements, connections, List.of());
    }

    @Test
    void unchangedDiagramsGiveNoChanges() {
        Diagram d = diagram(List.of(box("a", 0, 0), box("b", 200, 0)), List.of(link("f", "a", "b", Map.of("label", "x"))));
        Diagram same = diagram(List.of(box("a", 0, 0), box("b", 200, 0)), List.of(link("f", "a", "b", Map.of("label", "x"))));
        assertEquals(List.of(), DiagramDiff.diff(d, same));
    }

    @Test
    void addedAndRemovedElements() {
        Diagram before = diagram(List.of(box("a", 0, 0), box("b", 200, 0)), List.of());
        Diagram after = diagram(List.of(box("a", 0, 0), box("c", 400, 0)), List.of());
        assertEquals(List.of(new Removed("b"), new Added("c")), DiagramDiff.diff(before, after));
    }

    @Test
    void movedResizedAndSectorChanged() {
        Diagram before = diagram(List.of(box("a", 0, 0, 100, 50, Map.of(), "l1"), box("b", 200, 0)), List.of());
        Diagram after = diagram(List.of(box("a", 10, 20, 100, 50, Map.of(), "l2"), box("b", 200, 0, 150, 50, Map.of(), null)), List.of());
        assertEquals(List.of(new Moved("a", new Rectangle2D.Double(0, 0, 100, 50), new Rectangle2D.Double(10, 20, 100, 50)),
                new SectorChanged("a", "l1", "l2"),
                new Resized("b", new Rectangle2D.Double(200, 0, 100, 50), new Rectangle2D.Double(200, 0, 150, 50))),
                DiagramDiff.diff(before, after));
    }

    @Test
    void propertyChangesSortedByProperty() {
        Diagram before = diagram(List.of(box("a", 0, 0, 100, 50, Map.of("title", "x", "owner", "Ann"), null)), List.of());
        Diagram after = diagram(List.of(box("a", 0, 0, 100, 50, Map.of("title", "y", "done", "true"), null)), List.of());
        List<DiagramChange> changes = DiagramDiff.diff(before, after);
        assertEquals(3, changes.size());
        assertEquals(List.of(new PropertyChanged("a", "done", null, "true"), new PropertyChanged("a", "owner", "Ann", null),
                new PropertyChanged("a", "title", "x", "y")), changes);
    }

    @Test
    void connectedDisconnectedAndReconnected() {
        List<Element> elements = List.of(box("a", 0, 0), box("b", 200, 0), box("c", 400, 0));
        Diagram before = diagram(elements, List.of(link("f1", "a", "b", Map.of()), link("f2", "a", "b", Map.of())));
        Diagram after = diagram(elements, List.of(link("f1", "a", "c", Map.of()), link("f3", "b", "c", Map.of("label", "n"))));
        assertEquals(List.of(new Disconnected("f2"), new Disconnected("f1"), new Connected("f1", new End("a", "out"), new End("c", "in")),
                new Connected("f3", new End("b", "out"), new End("c", "in"))), DiagramDiff.diff(before, after));
    }

    @Test
    void connectionPropertyChanges() {
        List<Element> elements = List.of(box("a", 0, 0), box("b", 200, 0));
        Diagram before = diagram(elements, List.of(link("f", "a", "b", Map.of("label", "x"))));
        Diagram after = diagram(elements, List.of(link("f", "a", "b", Map.of("label", "y"))));
        assertEquals(List.of(new PropertyChanged("f", "label", "x", "y")), DiagramDiff.diff(before, after));
    }

    @Test
    void theOrderIsDeterministic() {
        Diagram before = diagram(List.of(box("a", 0, 0), box("b", 1, 1), box("c", 2, 2)), List.of());
        Diagram after = diagram(List.of(box("c", 5, 5), box("d", 0, 0), box("a", 9, 9)), List.of());
        List<DiagramChange> first = DiagramDiff.diff(before, after);
        for (int i = 0; i < 5; i++) {
            assertEquals(first, DiagramDiff.diff(before, after));
        }
        assertEquals(List.of(new Removed("b"), new Moved("c", new Rectangle2D.Double(2, 2, 100, 50), new Rectangle2D.Double(5, 5, 100, 50)),
                new Added("d"), new Moved("a", new Rectangle2D.Double(0, 0, 100, 50), new Rectangle2D.Double(9, 9, 100, 50))), first);
    }

    @Test
    void aNullPreviousDiagramAddsEverything() {
        Diagram after = diagram(List.of(box("a", 0, 0), box("b", 1, 1)), List.of(link("f", "a", "b", Map.of())));
        assertEquals(List.of(new Added("a"), new Added("b"), new Connected("f", new End("a", "out"), new End("b", "in"))),
                DiagramDiff.diff(null, after));
    }
}
