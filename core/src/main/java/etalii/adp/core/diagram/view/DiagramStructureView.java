package etalii.adp.core.diagram.view;

import java.util.ArrayList;
import java.util.List;

import etalii.adp.core.AdpStructureView;
import etalii.adp.core.diagram.ConnectionType;
import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.model.Connection;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Sector;

/**
 * A diagram in the Structure view: its sectors with the elements in them, then the elements in
 * none, each element with the connections that start at it. It follows the diagram's selection
 * and reveals what is chosen, through {@link AdpStructureView}.
 */
public final class DiagramStructureView extends AdpStructureView {

    /** The tree's root. */
    private enum Root {
        DIAGRAM
    }

    /** A sector in the tree, apart from element and connection keys that might equal its key. */
    private record SectorNode(Object key) {
    }

    private final DiagramFileEditor tool;

    public DiagramStructureView(DiagramFileEditor tool) {
        super(tool);
        this.tool = tool;
    }

    @Override
    protected Object rootKey() {
        return tool.diagram() == null ? null : Root.DIAGRAM;
    }

    @Override
    protected String text(Object key) {
        Diagram diagram = tool.diagram();
        if (diagram == null) {
            return "";
        }
        if (key == Root.DIAGRAM) {
            return tool.getFile().getName();
        }
        if (key instanceof SectorNode node) {
            Sector sector = diagram.sector(node.key());
            return sector == null ? "" : sector.label().isEmpty() ? String.valueOf(sector.key()) : sector.label();
        }
        var element = diagram.element(key);
        if (element != null) {
            ElementType type = tool.definition().elementType(element.type());
            return named(type == null ? element.type() : type.label(), firstText(key));
        }
        Connection connection = diagram.connection(key);
        if (connection != null) {
            ConnectionType type = tool.definition().connectionType(connection.type());
            String target = firstText(connection.target().elementKey());
            String label = type == null ? connection.type() : type.label();
            return named(label, target.isEmpty() ? "" : "→ " + target);
        }
        return "";
    }

    @Override
    protected List<?> children(Object key) {
        Diagram diagram = tool.diagram();
        if (diagram == null) {
            return List.of();
        }
        if (key == Root.DIAGRAM) {
            List<Object> children = new ArrayList<>();
            diagram.sectors().keySet().forEach(sector -> children.add(new SectorNode(sector)));
            diagram.elements().values().stream().filter(e -> e.sector() == null || diagram.sector(e.sector()) == null)
                    .forEach(e -> children.add(e.key()));
            return children;
        }
        if (key instanceof SectorNode node) {
            return diagram.elements().values().stream().filter(e -> node.key().equals(e.sector())).map(e -> e.key()).toList();
        }
        if (diagram.element(key) != null) {
            return diagram.connections().values().stream().filter(c -> c.source().elementKey().equals(key)).map(Connection::key).toList();
        }
        return List.of();
    }

    private String firstText(Object key) {
        ElementView view = tool.elementView(key);
        if (view == null) {
            return "";
        }
        return view.texts().values().stream().filter(text -> !text.isEmpty()).findFirst().orElse("").replace('\n', ' ');
    }

    private static String named(String kind, String text) {
        return text.isEmpty() ? kind : kind + " " + text;
    }
}
