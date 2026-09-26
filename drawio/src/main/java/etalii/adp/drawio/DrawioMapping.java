package etalii.adp.drawio;

import static etalii.adp.drawio.DrawioDefinition.DEFINITION;
import static etalii.adp.core.xml.XmlEdits.attributes;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import etalii.adp.core.FormatProblem;
import etalii.adp.core.TextChange;
import etalii.adp.core.TextChanges;
import etalii.adp.core.diagram.AddRequest;
import etalii.adp.core.diagram.ArrowHead;
import etalii.adp.core.diagram.BoundsChange;
import etalii.adp.core.diagram.Dash;
import etalii.adp.core.diagram.DiagramMapping;
import etalii.adp.core.diagram.EndSide;
import etalii.adp.core.diagram.LineStyle;
import etalii.adp.core.diagram.model.Connection;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;
import etalii.adp.core.diagram.model.End;
import etalii.adp.core.diagram.model.Sector;
import etalii.adp.core.diagram.model.StyleOverride;
import etalii.adp.core.xml.XmlEdits;
import etalii.adp.core.xml.XmlElement;
import etalii.adp.core.xml.XmlTree;

/**
 * Reads and edits the first page of an uncompressed draw.io file (research R20, data-model.md
 * "draw.io mapping"). Cells are keyed by their {@code id}; geometry inside a swimlane is relative
 * to it and converted to and from diagram coordinates. Other pages and every cell, key and
 * attribute not listed there are kept as they are.
 */
public final class DrawioMapping implements DiagramMapping {

    static final String COMPRESSED = "The first page of this draw.io file is compressed, so it cannot be shown as a diagram."
            + " In draw.io, switch off \"Compressed\" under File > Properties and save the file again.";

    private static final Map<String, ArrowHead> ARROWS = Map.of("none", ArrowHead.NONE, "open", ArrowHead.OPEN, "openThin", ArrowHead.OPEN,
            "async", ArrowHead.OPEN, "oval", ArrowHead.CIRCLE, "diamond", ArrowHead.DIAMOND, "diamondThin", ArrowHead.DIAMOND, "dash", ArrowHead.BAR);

    // simplified: edges to swimlane sectors or with a loose end, cells wrapped in <UserObject> and edge labels not at an end are kept, not shown;
    // vertical text (horizontal=0) is drawn horizontally; a list's header has no divider; deleting a row leaves its gap, as draw.io restacks only on edit

    @Override
    public Diagram read(CharSequence text) throws FormatProblem {
        Page page = page(XmlTree.of(text).root());
        if (page.root() == null) {
            throw page.diagram() != null && page.diagram().child("mxGraphModel") == null ? new FormatProblem(COMPRESSED, page.diagram().content().offset())
                    : new FormatProblem("This is not a draw.io diagram: it has no <mxGraphModel> with a <root>", page.file().range().offset());
        }
        Map<String, Element> elements = new LinkedHashMap<>();
        Map<String, String> endLabels = new LinkedHashMap<>();
        List<Sector> sectors = new ArrayList<>();
        for (XmlElement c : page.cells().values()) {
            XmlElement parent = page.parent(c);
            Style s = style(c);
            if (!vertex(c) || parent == null) {
                continue;
            } else if ("1".equals(parent.attribute("edge"))) {
                endLabels.put(parent.attribute("id") + number(c.child("mxGeometry"), "x"), s.has("edgeLabel") ? c.attribute("value", "") : "");
            } else if (page.sector(c)) {
                sectors.add(new Sector(c.attribute("id"), DrawioDefinition.SWIMLANE, c.attribute("value", ""), page.bounds(c)));
            } else if (!"group".equals(s.name())) {
                String shape = s.get("shape") != null ? s.get("shape") : s.name() != null ? s.name() : "1".equals(s.get("rounded")) ? "rounded" : "rectangle";
                String fill = page.list(c) && s.get("swimlaneFillColor") != null ? s.get("swimlaneFillColor") : s.get("fillColor");
                elements.put(c.attribute("id"), new Element(c.attribute("id"), page.list(c) ? DrawioDefinition.LIST : page.list(parent) ? DrawioDefinition.ROW : shape,
                        page.bounds(c), properties(c, s, "fillColor", "strokeColor", "fontColor", "dashed", "rounded"), page.sectorOf(parent),
                        page.list(parent) ? parent.attribute("id") : null, StyleOverride.colours(StyleOverride.colour(fill),
                                StyleOverride.colour(s.get("strokeColor")), StyleOverride.colour(s.get("fontColor"))).withHtml("1".equals(s.get("html")))));
            }
        }
        List<Connection> connections = new ArrayList<>();
        for (XmlElement c : page.cells().values()) {
            Element source = elements.get(c.attribute("source", ""));
            Element target = elements.get(c.attribute("target", ""));
            if ("1".equals(c.attribute("edge")) && source != null && target != null) {
                connections.add(connection(page, c, style(c), source, target, endLabels));
            }
        }
        Set<Object> shown = Stream.concat(elements.keySet().stream(), connections.stream().map(Connection::key)).collect(Collectors.toSet());
        return Diagram.of(List.copyOf(elements.values()), connections, sectors).withOrder(page.cells().keySet().stream().filter(shown::contains)
                .map(Object.class::cast).toList());
    }

    private static Connection connection(Page page, XmlElement c, Style s, Element source, Element target, Map<String, String> endLabels) {
        Map<String, String> properties = properties(c, s, "startArrow", "endArrow", "dashed", "strokeWidth");
        String line = "1".equals(s.get("curved")) ? "curved" : s.get("edgeStyle") == null || s.get("edgeStyle").equals("none") ? "straight" : "orthogonal";
        properties.put("edgeStyle", line);
        properties.put("sourceLabel", endLabels.getOrDefault(c.attribute("id") + "-1.0", ""));
        properties.put("targetLabel", endLabels.getOrDefault(c.attribute("id") + "1.0", ""));
        Point2D o = page.origin(c.attribute("parent", ""));
        XmlElement geometry = c.child("mxGeometry");
        XmlElement points = geometry == null ? null : geometry.child("Array");
        Double width = number(s.get("strokeWidth"));
        return new Connection(c.attribute("id"), DrawioDefinition.EDGE, end(source, s, "exit"), end(target, s, "entry"), properties,
                new StyleOverride(null, null, null, LineStyle.valueOf(line.toUpperCase()), "1".equals(s.get("dashed")) ? Dash.DASHED : null,
                        arrow(s.get("startArrow")), arrow(s.get("endArrow")), width == null ? null : width.floatValue(), null, "1".equals(s.get("html"))),
                points == null ? List.of() : points.children().stream().map(p -> new Point2D.Double(o.getX() + p.number("x", 0), o.getY() + p.number("y", 0)))
                        .map(Point2D.class::cast).toList());
    }

    @Override
    public TextChanges add(CharSequence text, Diagram diagram, AddRequest request) {
        Page page = page(XmlTree.reread(text).root());
        var shape = DrawioDefinition.SHAPES.get(request.type());
        String parent = request.sector() == null ? page.defaultLayer() : request.sector().toString();
        Point2D o = page.origin(parent);
        Rectangle2D at = request.bounds() == null ? new Rectangle2D.Double() : request.bounds();
        return insert(text, page, attributes("id", XmlEdits.uniqueId(page.file(), "adp-"), "value", request.properties().getOrDefault("label", ""), "style",
                shape.style() + ";whiteSpace=wrap;html=1;", "parent", parent, "vertex", "1"), attributes("x", format(at.getX() - o.getX()), "y",
                format(at.getY() - o.getY()), "width", format(shape.width()), "height", format(shape.height()), "as", "geometry"));
    }

    /** The cells, their children and the edges at any of them, such as a list's rows and the edges at those. */
    @Override
    public TextChanges remove(CharSequence text, Diagram diagram, Set<Object> keys) {
        Page page = page(XmlTree.reread(text).root());
        Set<String> gone = new LinkedHashSet<>(keys.stream().map(Object::toString).toList());
        for (int size = -1; size < gone.size(); ) {
            size = gone.size();
            page.cells().values().stream().filter(c -> Stream.of("parent", "source", "target").anyMatch(a -> gone.contains(c.attribute(a, ""))))
                    .forEach(c -> gone.add(c.attribute("id")));
        }
        return XmlEdits.changes(gone.stream().filter(page.cells()::containsKey).map(id -> XmlEdits.remove(text, page.cells().get(id))).toList());
    }

    @Override
    public TextChanges setBounds(CharSequence text, Diagram diagram, List<BoundsChange> changes) {
        Page page = page(XmlTree.reread(text).root());
        return XmlEdits.changes(changes.stream().flatMap(change -> {
            XmlElement c = page.cells().get(change.key().toString());
            String parent = !change.sectorChanged() ? c.attribute("parent") : change.sector() == null ? page.defaultLayer() : change.sector().toString();
            Point2D o = page.origin(parent);
            XmlElement g = c.child("mxGeometry");
            Rectangle2D b = change.bounds();
            return Stream.of(XmlEdits.update(c, "parent", parent), XmlEdits.updateNumber(g, "x", b.getX() - o.getX()), XmlEdits.updateNumber(g, "y", b.getY() - o.getY()),
                    b.getWidth() > 0 ? XmlEdits.updateNumber(g, "width", b.getWidth()) : null, b.getHeight() > 0 ? XmlEdits.updateNumber(g, "height", b.getHeight()) : null);
        }).toList());
    }

    @Override
    public TextChanges connect(CharSequence text, Diagram diagram, String connectionType, End source, End target) {
        Page page = page(XmlTree.reread(text).root());
        Style style = anchor(anchor(new Style("edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;"), diagram, source, "exit"), diagram, target, "entry");
        return insert(text, page, attributes("id", XmlEdits.uniqueId(page.file(), "adp-"), "value", "", "style", style.text(), "parent", page.defaultLayer(),
                "source", source.elementKey().toString(), "target", target.elementKey().toString(), "edge", "1"), attributes("relative", "1", "as", "geometry"));
    }

    @Override
    public TextChanges reconnect(CharSequence text, Diagram diagram, Object connection, EndSide side, End end) {
        XmlElement c = page(XmlTree.reread(text).root()).cells().get(connection.toString());
        boolean source = side == EndSide.SOURCE;
        return XmlEdits.changes(XmlEdits.update(c, source ? "source" : "target", end.elementKey().toString()),
                XmlEdits.update(c, "style", anchor(style(c), diagram, end, source ? "exit" : "entry").text()));
    }

    @Override
    public TextChanges setProperty(CharSequence text, Diagram diagram, Set<Object> keys, String property, String value) {
        Page page = page(XmlTree.reread(text).root());
        return XmlEdits.changes(keys.stream().filter(key -> page.cells().containsKey(key.toString()) && !property.equals("id")).map(key -> {
            XmlElement c = page.cells().get(key.toString());
            Style s = style(c);
            String current = diagram.element(key) != null ? diagram.element(key).property(property) : diagram.connection(key).property(property);
            return current.equals(value) ? null : switch (property) {
            case "label" -> XmlEdits.update(c, "value", value);
            case "edgeStyle" -> XmlEdits.update(c, "style",
                    s.with("curved", value.equals("curved") ? "1" : null).with("edgeStyle", value.equals("orthogonal") ? "orthogonalEdgeStyle" : null).text());
            case "dashed", "rounded" -> XmlEdits.update(c, "style", s.with(property, value.equals("true") ? "1" : null).text());
            default -> XmlEdits.update(c, "style", s.with(property, value).text());
            };
        }).toList());
    }

    /** The first page: its file, its {@code <diagram>} if any, its {@code <root>} and the cells there by id, in document order. */
    private record Page(XmlElement file, XmlElement diagram, XmlElement root, Map<String, XmlElement> cells, Set<String> holdSwimlanes) {

        XmlElement parent(XmlElement c) {
            return cells.get(c.attribute("parent", ""));
        }

        /** The first layer: a child of the root cell. */
        String defaultLayer() {
            return cells.values().stream().filter(c -> parent(c) != null && parent(c).attribute("parent") == null).map(c -> c.attribute("id")).findFirst().orElse("1");
        }

        /** A draw.io list, such as a UML class: a stacked swimlane of rows. It is an element; its rows are its parts. */
        boolean list(XmlElement c) {
            return swimlane(c) && "stackLayout".equals(style(c).get("childLayout")) && !holdSwimlanes.contains(c.attribute("id"));
        }

        /** Any other swimlane, a pool of lanes included, is a sector. */
        boolean sector(XmlElement c) {
            return swimlane(c) && !list(c);
        }

        /** The innermost sector a cell is in, through groups and lists, or {@code null}. */
        Object sectorOf(XmlElement c) {
            return c == null ? null : sector(c) ? c.attribute("id") : sectorOf(parent(c));
        }

        /** Where a cell's children are measured from, in diagram coordinates. */
        Point2D origin(String id) {
            XmlElement c = cells.get(id);
            Point2D p = c == null || !vertex(c) ? null : origin(c.attribute("parent", ""));
            return p == null ? new Point2D.Double() : new Point2D.Double(p.getX() + number(c.child("mxGeometry"), "x"), p.getY() + number(c.child("mxGeometry"), "y"));
        }

        Rectangle2D bounds(XmlElement c) {
            Point2D at = origin(c.attribute("id"));
            return new Rectangle2D.Double(at.getX(), at.getY(), number(c.child("mxGeometry"), "width"), number(c.child("mxGeometry"), "height"));
        }
    }

    /** The first page of a parsed file; its root is {@code null} when there is none. */
    private static Page page(XmlElement file) {
        XmlElement diagram = file.name().equals("mxfile") ? file.child("diagram") : null;
        XmlElement model = diagram == null ? file : diagram.child("mxGraphModel");
        XmlElement root = model != null && model.name().equals("mxGraphModel") ? model.child("root") : null;
        Map<String, XmlElement> cells = new LinkedHashMap<>();
        (root == null ? List.<XmlElement>of() : root.children()).stream().filter(c -> c.name().equals("mxCell") && c.attribute("id") != null)
                .forEach(c -> cells.put(c.attribute("id"), c));
        Set<String> holdSwimlanes = new HashSet<>();
        cells.values().stream().filter(DrawioMapping::swimlane).forEach(c -> holdSwimlanes.add(c.attribute("parent", "")));
        return new Page(file, diagram, root, cells, holdSwimlanes);
    }

    /** A new cell with its geometry, at the end of the page, indented as its siblings are. */
    private static TextChanges insert(CharSequence text, Page page, Map<String, String> cell, Map<String, String> geometry) {
        List<XmlElement> siblings = page.root().children();
        String line = XmlEdits.lineSeparator(text) + (siblings.isEmpty() ? page.root().indent() + "  " : siblings.get(siblings.size() - 1).indent());
        String xml = XmlEdits.element("mxCell", cell, "").replaceFirst("</mxCell>$", line + "  " + XmlEdits.element("mxGeometry", geometry, null) + line + "</mxCell>");
        return XmlEdits.changes(XmlEdits.insertChild(text, page.root(), siblings.size(), xml));
    }

    /** The style with the side's anchor position for the end's fixed anchor, or without one for the outline. */
    private static Style anchor(Style style, Diagram diagram, End end, String side) {
        var element = diagram.element(end.elementKey());
        var type = element == null || end.anchorId() == null ? null : DEFINITION.elementType(element.type());
        var anchor = type == null || type.anchor(end.anchorId()).perimeter() ? null : type.anchor(end.anchorId());
        return style.with(side + "X", anchor == null ? null : XmlEdits.number(anchor.fx())).with(side + "Y", anchor == null ? null : XmlEdits.number(anchor.fy()));
    }

    private static End end(Element element, Style s, String side) {
        var type = DEFINITION.elementType(element.type());
        Double x = number(s.get(side + "X"));
        Double y = number(s.get(side + "Y"));
        String anchor = x == null || y == null ? null : DrawioDefinition.anchorId(x, y);
        return new End(element.key(), type != null && anchor != null && type.anchor(anchor) != null ? anchor : null);
    }

    /** The label, style keys as they are with draw.io's 1 and 0 as true and false, the id, and editable=0 for the rules. */
    private static Map<String, String> properties(XmlElement c, Style s, String... keys) {
        Map<String, String> properties = attributes("label", c.attribute("value", ""), "id", c.attribute("id"), "editable", "0".equals(s.get("editable")) ? "0" : "");
        for (String key : keys) {
            String value = s.get(key) == null ? "" : s.get(key);
            properties.put(key, !key.equals("dashed") && !key.equals("rounded") ? value : value.equals("1") ? "true" : value.equals("0") ? "false" : "");
        }
        return properties;
    }

    private static ArrowHead arrow(String value) {
        return value == null ? null : ARROWS.getOrDefault(value, ArrowHead.FILLED);
    }

    /** A geometry value that has not moved is kept as written, unrounded; {@code updateNumber} compares after rounding. */
    private static TextChange coordinate(XmlElement g, String name, double value) {
        return Math.abs(g.number(name, 0) - value) < 1e-6 ? null : XmlEdits.updateNumber(g, name, value);
    }

    private static String format(double value) {
        return XmlEdits.number(Math.round(value * 100) / 100.0);
    }

    private static boolean vertex(XmlElement c) {
        return "1".equals(c.attribute("vertex"));
    }

    private static boolean swimlane(XmlElement c) {
        return c != null && vertex(c) && style(c).has("swimlane");
    }

    private static Style style(XmlElement c) {
        return new Style(c.attribute("style"));
    }

    private static double number(XmlElement e, String name) {
        return e == null ? 0 : e.number(name, 0);
    }

    private static Double number(String value) {
        return value != null && value.matches("\\s*-?\\d+(\\.\\d+)?\\s*") ? Double.valueOf(value.trim()) : null;
    }
}
