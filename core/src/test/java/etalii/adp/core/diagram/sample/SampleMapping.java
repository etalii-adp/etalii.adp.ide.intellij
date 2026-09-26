package etalii.adp.core.diagram.sample;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import etalii.adp.core.FormatProblem;
import etalii.adp.core.TextChange;
import etalii.adp.core.TextChanges;
import etalii.adp.core.diagram.AddRequest;
import etalii.adp.core.diagram.BoundsChange;
import etalii.adp.core.diagram.DiagramMapping;
import etalii.adp.core.diagram.EndSide;
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
 * Reads and edits {@code .adpsample} files: {@code <box>} elements, {@code <link>} connections,
 * {@code <lane>} and {@code <legend>} sectors under a {@code <sample>} root. A box's title and a
 * link's label are the element content; every other property is an attribute of the same name.
 */
public final class SampleMapping implements DiagramMapping {

    private static final Set<String> BOX_FIXED = Set.of("id", "type", "x", "y", "w", "h", "lane");
    private static final Set<String> LINK_FIXED = Set.of("id", "type", "from", "fromAnchor", "to", "toAnchor", "points");

    @Override
    public Diagram read(CharSequence text) throws FormatProblem {
        XmlElement root = root(text);
        List<Element> elements = new ArrayList<>();
        List<Connection> connections = new ArrayList<>();
        List<Sector> sectors = new ArrayList<>();
        List<XmlElement> children = root.children();
        for (int i = 0; i < children.size(); i++) {
            XmlElement child = children.get(i);
            Object key = key(child, i);
            switch (child.name()) {
            case "lane", "legend" -> {
                boolean lane = child.name().equals("lane");
                sectors.add(new Sector(key, child.name(), child.attribute("label", ""), new Rectangle2D.Double(child.number("x", 0),
                        child.number("y", 0), child.number("width", lane ? 10000 : 0), child.number("height", lane ? 0 : 10000))));
            }
            case "box" -> elements.add(new Element(key, child.attribute("type"),
                    new Rectangle2D.Double(child.number("x", 0), child.number("y", 0), child.number("w", 0), child.number("h", 0)),
                    properties(child, "title", BOX_FIXED, key), child.attribute("lane"), null, style(child.attribute("color"))));
            case "link" -> connections.add(new Connection(key, child.attribute("type"), new End(child.attribute("from"), child.attribute("fromAnchor")),
                    new End(child.attribute("to"), child.attribute("toAnchor")), properties(child, "label", LINK_FIXED, key), null,
                    points(child.attribute("points"))));
            default -> {
                // anything else is kept as it is
            }
            }
        }
        return Diagram.of(elements, connections, sectors);
    }

    @Override
    public TextChanges add(CharSequence text, Diagram diagram, AddRequest request) {
        XmlElement root = XmlTree.reread(text).root();
        Rectangle2D bounds = request.bounds() == null ? new Rectangle2D.Double() : request.bounds();
        Map<String, String> attributes = XmlEdits.attributes("id", XmlEdits.uniqueId(root, request.type()), "type", request.type(), "x",
                XmlEdits.number(bounds.getX()), "y", XmlEdits.number(bounds.getY()), "w", bounds.getWidth() > 0 ? XmlEdits.number(bounds.getWidth()) : null,
                "h", bounds.getHeight() > 0 ? XmlEdits.number(bounds.getHeight()) : null, "lane", request.sector() == null ? null : request.sector().toString());
        request.properties().forEach(attributes::putIfAbsent);
        attributes.remove("title");
        String box = XmlEdits.element("box", attributes, request.properties().get("title"));
        return XmlEdits.changes(XmlEdits.insertChild(text, root, root.children().size(), box));
    }

    @Override
    public TextChanges remove(CharSequence text, Diagram diagram, Set<Object> keys) {
        Map<Object, XmlElement> byKey = byKey(XmlTree.reread(text).root());
        List<TextChange> changes = new ArrayList<>();
        for (Object key : keys) {
            XmlElement element = byKey.get(key);
            if (element != null) {
                changes.add(XmlEdits.remove(text, element));
            }
        }
        return XmlEdits.changes(changes);
    }

    @Override
    public TextChanges setBounds(CharSequence text, Diagram diagram, List<BoundsChange> changes) {
        Map<Object, XmlElement> byKey = byKey(XmlTree.reread(text).root());
        List<TextChange> edits = new ArrayList<>();
        for (BoundsChange change : changes) {
            XmlElement element = byKey.get(change.key());
            Rectangle2D bounds = change.bounds();
            edits.add(XmlEdits.updateNumber(element, "x", bounds.getX()));
            edits.add(XmlEdits.updateNumber(element, "y", bounds.getY()));
            edits.add(bounds.getWidth() > 0 ? XmlEdits.updateNumber(element, "w", bounds.getWidth()) : null);
            edits.add(bounds.getHeight() > 0 ? XmlEdits.updateNumber(element, "h", bounds.getHeight()) : null);
            if (change.sectorChanged()) {
                edits.add(XmlEdits.update(element, "lane", change.sector() == null ? null : change.sector().toString()));
            }
        }
        return XmlEdits.changes(edits);
    }

    @Override
    public TextChanges connect(CharSequence text, Diagram diagram, String connectionType, End source, End target) {
        XmlElement root = XmlTree.reread(text).root();
        Map<String, String> attributes = XmlEdits.attributes("id", XmlEdits.uniqueId(root, connectionType), "type", connectionType, "from",
                source.elementKey().toString(), "fromAnchor", source.anchorId(), "to", target.elementKey().toString(), "toAnchor", target.anchorId());
        return XmlEdits.changes(XmlEdits.insertChild(text, root, root.children().size(), XmlEdits.element("link", attributes, null)));
    }

    @Override
    public TextChanges reconnect(CharSequence text, Diagram diagram, Object connection, EndSide side, End end) {
        XmlElement element = byKey(XmlTree.reread(text).root()).get(connection);
        String elementAttribute = side == EndSide.SOURCE ? "from" : "to";
        String anchorAttribute = elementAttribute + "Anchor";
        return XmlEdits.changes(XmlEdits.update(element, elementAttribute, end.elementKey().toString()),
                XmlEdits.update(element, anchorAttribute, end.anchorId()));
    }

    @Override
    public TextChanges setProperty(CharSequence text, Diagram diagram, Set<Object> keys, String property, String value) {
        Map<Object, XmlElement> byKey = byKey(XmlTree.reread(text).root());
        List<TextChange> changes = new ArrayList<>();
        for (Object key : keys) {
            XmlElement element = byKey.get(key);
            if (element == null || property.equals("id")) {
                continue;
            }
            String content = element.name().equals("box") ? "title" : "label";
            if (property.equals(content)) {
                changes.add(element.text().equals(value) ? null : XmlEdits.setContent(element, value));
            } else {
                changes.add(XmlEdits.update(element, property, value));
            }
        }
        return XmlEdits.changes(changes);
    }

    private static XmlElement root(CharSequence text) throws FormatProblem {
        XmlElement root = XmlTree.of(text).root();
        if (!root.name().equals("sample")) {
            throw new FormatProblem("This is not a sample diagram: the root element is <" + root.name() + ">", root.range().offset());
        }
        return root;
    }


    private static Map<Object, XmlElement> byKey(XmlElement root) {
        Map<Object, XmlElement> byKey = new LinkedHashMap<>();
        for (int i = 0; i < root.children().size(); i++) {
            byKey.put(key(root.children().get(i), i), root.children().get(i));
        }
        return byKey;
    }

    private static Object key(XmlElement element, int index) {
        String id = element.attribute("id");
        return id != null ? id : "@" + index;
    }

    private static Map<String, String> properties(XmlElement element, String contentProperty, Set<String> fixed, Object key) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put(contentProperty, element.text());
        element.attributes().forEach((name, attribute) -> {
            if (!fixed.contains(name)) {
                properties.put(name, attribute.value());
            }
        });
        properties.put("id", key.toString());
        return properties;
    }

    private static StyleOverride style(String colour) {
        return StyleOverride.colour(colour) == null ? null : StyleOverride.colours(StyleOverride.colour(colour), null, null);
    }

    private static List<Point2D> points(String points) {
        List<Point2D> list = new ArrayList<>();
        if (points != null) {
            for (String pair : points.trim().split("\\s+")) {
                String[] xy = pair.split(",");
                if (xy.length == 2) {
                    try {
                        list.add(new Point2D.Double(Double.parseDouble(xy[0]), Double.parseDouble(xy[1])));
                    } catch (NumberFormatException e) {
                        // a malformed point is not drawn
                    }
                }
            }
        }
        return list;
    }
}