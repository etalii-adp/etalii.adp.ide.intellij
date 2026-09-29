package etalii.adp.core.diagram.edit;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;

import etalii.adp.core.TextChanges;
import etalii.adp.core.diagram.AddRequest;
import etalii.adp.core.diagram.Anchor;
import etalii.adp.core.diagram.BoundsChange;
import etalii.adp.core.diagram.ConnectionType;
import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.DiagramMapping;
import etalii.adp.core.diagram.DiagramRules;
import etalii.adp.core.diagram.Direction;
import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.EndSide;
import etalii.adp.core.diagram.Placement;
import etalii.adp.core.diagram.PropertyDecl;
import etalii.adp.core.diagram.Resize;
import etalii.adp.core.diagram.SectorDecl;
import etalii.adp.core.diagram.Sizing;
import etalii.adp.core.diagram.Space;
import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.model.Connection;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;
import etalii.adp.core.diagram.model.End;
import etalii.adp.core.diagram.model.Sector;

/**
 * Every gesture as one command (research R9, R17): the definition's permissions, then the
 * diagram's rules, then the mapping's text changes, run through {@link Host#execute} with the
 * contract's label. The first refusal wins, nothing is written, and it is recorded on the host
 * for the refusal feedback and the test kit.
 */
public final class DiagramCommands {

    /** What a gesture works on: the diagram, or a fake in tests. */
    public interface Host extends UserDataHolder {

        DiagramDefinition definition();

        DiagramMapping mapping();

        /** The diagram read from the text as it is now, or {@code null} while a problem is shown. */
        Diagram diagram();

        CharSequence text();

        /** One undoable command; {@code reselect} runs inside it, on the fresh diagram. */
        void execute(String label, TextChanges changes, Runnable reselect);

        void select(Collection<?> keys);

        /** A diagram point in viewport coordinates, for view-space sectors. */
        default Point2D toViewport(Point2D diagramPoint) {
            return diagramPoint;
        }
    }

    static final Key<Verdict> LAST_REFUSAL = Key.create("etalii.adp.diagram.lastRefusal");
    static final String UNKNOWN_TYPE = "unknown type is kept as it is";

    private final Host host;

    public DiagramCommands(Host host) {
        this.host = host;
    }

    /** The refusal of the last gesture on this host, or {@code null} when it was allowed. */
    public static Verdict lastRefusal(UserDataHolder host) {
        return host.getUserData(LAST_REFUSAL);
    }

    /** Add an element of {@code type} with its top left at {@code at}, or dropped onto {@code target}. */
    public Verdict add(String type, Point2D at, Object target) {
        Diagram d = host.diagram();
        DiagramDefinition definition = host.definition();
        ElementType elementType = definition.elementType(type);
        if (d == null) {
            return refuse("the file cannot be shown as a diagram");
        }
        if (elementType == null) {
            return refuse("'" + type + "' is not an element type of this diagram");
        }
        Rectangle2D bounds = null;
        Object sector = null;
        if (target != null) {
            Element onto = d.element(target);
            if (onto == null || isPlaceholder(onto)) {
                return refuse(onto == null ? "the drop target is gone" : UNKNOWN_TYPE);
            }
            if (!elementType.droppableOnto()) {
                return refuse("'" + elementType.label() + "' cannot be dropped onto another element");
            }
        } else if (at != null) {
            bounds = new Rectangle2D.Double(at.getX(), at.getY(), defaultWidth(elementType.sizing()), defaultHeight(elementType.sizing()));
            sector = sectorAt(d, bounds);
        }
        Verdict rule = definition.rules().canAdd(d, type, target != null ? target : sector);
        if (!rule.allowed()) {
            return refuse(rule.reason());
        }
        Map<String, String> defaults = new LinkedHashMap<>();
        elementType.properties().stream().filter(p -> p.defaultValue() != null).forEach(p -> defaults.put(p.id(), p.defaultValue()));
        TextChanges changes = host.mapping().add(host.text(), d, new AddRequest(type, bounds, target, sector, defaults));
        Set<Object> before = Set.copyOf(d.elements().keySet());
        return run("Add " + elementType.label(), changes, () -> selectNew(before, true));
    }

    /** Delete elements and connections; an element's connections go with it. */
    public Verdict remove(Collection<?> keys) {
        Diagram d = host.diagram();
        if (d == null) {
            return refuse("the file cannot be shown as a diagram");
        }
        DiagramRules rules = host.definition().rules();
        Set<Object> all = new LinkedHashSet<>();
        List<Object> connections = new ArrayList<>();
        for (Object key : keys) {
            Element element = d.element(key);
            Connection connection = d.connection(key);
            if (element != null) {
                if (isPlaceholder(element)) {
                    return refuse(UNKNOWN_TYPE);
                }
                all.add(key);
                d.connectionsOf(key).forEach(c -> all.add(c.key()));
            } else if (connection != null) {
                ConnectionType type = host.definition().connectionType(connection.type());
                if (type == null) {
                    return refuse(UNKNOWN_TYPE);
                }
                if (!type.userConnectable()) {
                    return refuse(notRemovable(d, type, key));
                }
                all.add(key);
                connections.add(key);
            }
        }
        if (all.isEmpty()) {
            return allow();
        }
        for (Object connection : connections) {
            Verdict rule = rules.canDisconnect(d, connection);
            if (!rule.allowed()) {
                return refuse(rule.reason());
            }
        }
        Verdict rule = rules.canRemove(d, all);
        if (!rule.allowed()) {
            return refuse(rule.reason());
        }
        return run("Delete", host.mapping().remove(host.text(), d, all), () -> host.select(List.of()));
    }

    /** Move elements to these bounds; only the positions are written, and the sector each centre lands in. */
    public Verdict move(Map<?, Rectangle2D> bounds) {
        Diagram d = host.diagram();
        if (d == null) {
            return refuse("the file cannot be shown as a diagram");
        }
        List<BoundsChange> changes = new ArrayList<>();
        Set<Object> sectorsEntered = new HashSet<>();
        boolean sectorChanged = false;
        for (Map.Entry<?, Rectangle2D> entry : bounds.entrySet()) {
            Element element = d.element(entry.getKey());
            if (element == null) {
                continue;
            }
            if (isPlaceholder(element)) {
                return refuse(UNKNOWN_TYPE);
            }
            ElementType type = host.definition().elementType(element.type());
            if (!type.movable() || element.bounds() == null) {
                return refuse("'" + type.label() + "' cannot be moved");
            }
            Rectangle2D shown = entry.getValue();
            Rectangle2D stored = element.bounds();
            Object sector = sectorAt(d, shown);
            boolean changed = !Objects.equals(sector, element.sector());
            sectorChanged |= changed;
            if (changed) {
                sectorsEntered.add(sector);
            }
            changes.add(new BoundsChange(element.key(), new Rectangle2D.Double(shown.getX(), shown.getY(), stored.getWidth(), stored.getHeight()),
                    sector, changed));
        }
        String label = "Move";
        if (sectorChanged && sectorsEntered.size() == 1 && sectorsEntered.iterator().next() != null) {
            label = "Move to " + d.sector(sectorsEntered.iterator().next()).label();
        }
        List<Object> keys = List.copyOf(bounds.keySet());
        return run(label, host.mapping().setBounds(host.text(), d, changes), () -> host.select(keys));
    }

    /** Resize one element within the axes its type allows. */
    public Verdict resize(Object key, Rectangle2D next) {
        Diagram d = host.diagram();
        Element element = d == null ? null : d.element(key);
        if (element == null) {
            return refuse("the element is gone");
        }
        if (isPlaceholder(element)) {
            return refuse(UNKNOWN_TYPE);
        }
        ElementType type = host.definition().elementType(element.type());
        Resize resize = type.resize();
        Rectangle2D old = element.bounds();
        if (resize == Resize.NONE || old == null) {
            return refuse("'" + type.label() + "' cannot be resized");
        }
        if (!resize.vertical() && (next.getY() != old.getY() || old.getHeight() > 0 && next.getHeight() != old.getHeight())) {
            return refuse("'" + type.label() + "' can only be resized horizontally");
        }
        if (!resize.horizontal() && (next.getX() != old.getX() || old.getWidth() > 0 && next.getWidth() != old.getWidth())) {
            return refuse("'" + type.label() + "' can only be resized vertically");
        }
        Rectangle2D resized = new Rectangle2D.Double(next.getX(), next.getY(), resize.horizontal() ? next.getWidth() : old.getWidth(),
                resize.vertical() ? next.getHeight() : old.getHeight());
        Object sector = sectorAt(d, next);
        BoundsChange change = new BoundsChange(key, resized, sector, !Objects.equals(sector, element.sector()));
        return run("Resize", host.mapping().setBounds(host.text(), d, List.of(change)), () -> host.select(List.of(key)));
    }

    /** Drop elements onto a target, for diagrams with a layout. */
    public Verdict drop(Collection<?> keys, Object target, Placement placement) {
        Diagram d = host.diagram();
        Element onto = d == null ? null : d.element(target);
        if (onto == null) {
            return refuse("the drop target is gone");
        }
        if (isPlaceholder(onto)) {
            return refuse(UNKNOWN_TYPE);
        }
        Set<Object> dropped = new LinkedHashSet<>();
        for (Object key : keys) {
            Element element = d.element(key);
            if (element == null) {
                continue;
            }
            if (isPlaceholder(element)) {
                return refuse(UNKNOWN_TYPE);
            }
            ElementType type = host.definition().elementType(element.type());
            if (!type.droppableOnto()) {
                return refuse("'" + type.label() + "' cannot be dropped onto another element");
            }
            if (key.equals(target)) {
                return refuse("an element cannot be dropped onto itself");
            }
            dropped.add(key);
        }
        Verdict rule = host.definition().rules().canDrop(d, dropped, target, placement);
        if (!rule.allowed()) {
            return refuse(rule.reason());
        }
        List<Object> reselect = List.copyOf(dropped);
        return run("Move", host.mapping().drop(host.text(), d, dropped, target, placement), () -> host.select(reselect));
    }

    /** Connect two anchors with a connection of {@code type}. */
    public Verdict connect(String type, End source, End target) {
        Diagram d = host.diagram();
        if (d == null) {
            return refuse("the file cannot be shown as a diagram");
        }
        ConnectionType connectionType = host.definition().connectionType(type);
        if (connectionType == null) {
            return refuse("'" + type + "' is not a connection type of this diagram");
        }
        if (!connectionType.userConnectable()) {
            return refuse("'" + connectionType.label() + "' connections cannot be drawn");
        }
        Verdict end = checkEnd(d, type, source, Direction.OUT);
        if (end == null) {
            end = checkEnd(d, type, target, Direction.IN);
        }
        if (end != null) {
            return refuse(end.reason());
        }
        Verdict rule = host.definition().rules().canConnect(d, type, source, target);
        if (!rule.allowed()) {
            return refuse(rule.reason());
        }
        Set<Object> before = Set.copyOf(d.connections().keySet());
        return run("Connect " + connectionType.label(), host.mapping().connect(host.text(), d, type, source, target),
                () -> selectNew(before, false));
    }

    /** Move one end of a connection to another anchor. */
    public Verdict reconnect(Object key, EndSide side, End end) {
        Diagram d = host.diagram();
        Connection connection = d == null ? null : d.connection(key);
        if (connection == null) {
            return refuse("the connection is gone");
        }
        ConnectionType type = host.definition().connectionType(connection.type());
        if (type == null) {
            return refuse(UNKNOWN_TYPE);
        }
        if (!type.userConnectable()) {
            return refuse(notRemovable(d, type, key));
        }
        Verdict check = checkEnd(d, type.id(), end, side == EndSide.SOURCE ? Direction.OUT : Direction.IN);
        if (check != null) {
            return refuse(check.reason());
        }
        DiagramRules rules = host.definition().rules();
        Verdict rule = rules.canDisconnect(d, key);
        if (rule.allowed()) {
            rule = rules.canConnect(d, type.id(), side == EndSide.SOURCE ? end : connection.source(),
                    side == EndSide.TARGET ? end : connection.target());
        }
        if (!rule.allowed()) {
            return refuse(rule.reason());
        }
        return run("Reconnect " + type.label(), host.mapping().reconnect(host.text(), d, key, side, end), () -> host.select(List.of(key)));
    }

    /** Set one property on every item, as one command. */
    public Verdict setProperty(Collection<?> keys, String property, String value) {
        Diagram d = host.diagram();
        if (d == null) {
            return refuse("the file cannot be shown as a diagram");
        }
        PropertyDecl first = null;
        for (Object key : keys) {
            PropertyDecl decl;
            Element element = d.element(key);
            Connection connection = d.connection(key);
            if (element != null) {
                ElementType type = host.definition().elementType(element.type());
                if (type == null) {
                    return refuse(UNKNOWN_TYPE);
                }
                decl = type.property(property);
            } else if (connection != null) {
                ConnectionType type = host.definition().connectionType(connection.type());
                if (type == null) {
                    return refuse(UNKNOWN_TYPE);
                }
                decl = type.property(property);
            } else {
                return refuse("the item is gone");
            }
            if (decl == null) {
                return refuse("there is no property '" + property + "' here");
            }
            if (decl.readOnly()) {
                return refuse("'" + decl.label() + "' is read-only");
            }
            String problem = decl.editor().validate(value);
            if (problem != null) {
                return refuse(problem);
            }
            Verdict rule = host.definition().rules().canSetProperty(d, key, property);
            if (!rule.allowed()) {
                return refuse(rule.reason());
            }
            first = first == null ? decl : first;
        }
        if (first == null) {
            return allow();
        }
        List<Object> reselect = List.copyOf(keys);
        return run("Change " + first.label(), host.mapping().setProperty(host.text(), d, new LinkedHashSet<>(keys), property, value),
                () -> host.select(reselect));
    }

    /** True when the item's type is not declared: it is shown and kept, never edited. */
    public boolean isPlaceholder(Element element) {
        return host.definition().elementType(element.type()) == null;
    }

    /**
     * The sector the bounds' centre is in: view-space sectors first, in viewport coordinates, then
     * diagram-space ones, where the last in document order wins, so a lane inside a pool wins over the pool.
     */
    public Object sectorAt(Diagram d, Rectangle2D bounds) {
        Point2D centre = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
        Object found = null;
        for (Sector sector : d.sectors().values()) {
            SectorDecl decl = host.definition().sectors().get(sector.declId());
            boolean view = decl != null && decl.space() == Space.VIEW;
            if (view && sector.bounds().contains(host.toViewport(centre))) {
                return sector.key();
            }
            if (!view && sector.bounds().contains(centre)) {
                found = sector.key();
            }
        }
        return found;
    }

    /** {@code null} when the end may take this role for the connection type, else the refusal. */
    private Verdict checkEnd(Diagram d, String type, End end, Direction role) {
        Element element = d.element(end.elementKey());
        if (element == null) {
            return Verdict.refuse("the element is gone");
        }
        ElementType elementType = host.definition().elementType(element.type());
        if (elementType == null) {
            return Verdict.refuse(UNKNOWN_TYPE);
        }
        String verb = role == Direction.OUT ? "start" : "end";
        if (end.anchorId() == null) {
            boolean perimeter = elementType.anchors().stream().anyMatch(a -> a.perimeter() && a.accepts(type, role));
            return perimeter ? null : Verdict.refuse("'" + type + "' may not " + verb + " at the outline of '" + elementType.id() + "'");
        }
        Anchor anchor = elementType.anchor(end.anchorId());
        if (anchor == null) {
            return Verdict.refuse("'" + elementType.id() + "' has no anchor '" + end.anchorId() + "'");
        }
        if (!anchor.accepts().containsKey(type)) {
            return Verdict.refuse("anchor '" + anchor.id() + "' of '" + elementType.id() + "' does not accept '" + type + "'");
        }
        if (!anchor.accepts(type, role)) {
            return Verdict.refuse("'" + type + "' may not " + verb + " at anchor '" + anchor.id() + "' of '" + elementType.id() + "'");
        }
        return null;
    }

    /** A connection the user may not remove: the rules' reason when they give one. */
    private String notRemovable(Diagram d, ConnectionType type, Object key) {
        Verdict rule = host.definition().rules().canDisconnect(d, key);
        return rule.allowed() ? "'" + type.label() + "' connections cannot be removed" : rule.reason();
    }

    private void selectNew(Set<Object> before, boolean elements) {
        Diagram now = host.diagram();
        if (now == null) {
            return;
        }
        List<Object> fresh = new ArrayList<>(elements ? now.elements().keySet() : now.connections().keySet());
        fresh.removeAll(before);
        host.select(fresh);
    }

    private static double defaultWidth(Sizing sizing) {
        return switch (sizing) {
        case Sizing.Fixed fixed -> fixed.width();
        case Sizing.FromDiagram from -> from.minWidth();
        case Sizing.Auto auto -> 0;
        };
    }

    private static double defaultHeight(Sizing sizing) {
        return switch (sizing) {
        case Sizing.Fixed fixed -> fixed.height();
        case Sizing.FromDiagram from -> from.minHeight();
        case Sizing.Auto auto -> 0;
        };
    }

    private Verdict run(String label, TextChanges changes, Runnable reselect) {
        Verdict allowed = allow();
        if (!changes.isEmpty()) {
            host.execute(label, changes, reselect);
        }
        return allowed;
    }

    private Verdict allow() {
        host.putUserData(LAST_REFUSAL, null);
        return Verdict.allow();
    }

    private Verdict refuse(String reason) {
        Verdict verdict = Verdict.refuse(reason);
        host.putUserData(LAST_REFUSAL, verdict);
        return verdict;
    }
}
