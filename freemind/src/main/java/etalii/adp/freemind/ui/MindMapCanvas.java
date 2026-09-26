package etalii.adp.freemind.ui;

import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ToolTipManager;

import com.intellij.ui.scale.JBUIScale;

import etalii.adp.core.NodeView;
import etalii.adp.core.diagram.TextSlot;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.ElementMeasure;
import etalii.adp.core.diagram.view.ElementView;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;

/**
 * The shared canvas with what only a mind map shows: a tooltip for a node's note, link, fold
 * marker and icons, and where its indicators are, in this component's (zoomed) coordinates.
 */
public final class MindMapCanvas extends DiagramCanvas {

    private final MindMapDesigner designer;

    MindMapCanvas(MindMapDesigner designer) {
        super(designer);
        this.designer = designer;
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    /** The drawn nodes in document order, as the designer shows them. */
    public MindMapLayout.Result mapLayout() {
        Map<NodeKey, NodeView> views = new LinkedHashMap<>();
        for (Object key : designer.elementKeys()) {
            NodeView view = designer.viewOf(key);
            if (view != null) {
                views.put((NodeKey) key, view);
            }
        }
        return new MindMapLayout.Result(views);
    }

    /** The node's box in this component's coordinates, or {@code null} when it is not drawn. */
    public Rectangle boundsOf(NodeKey key) {
        ElementView view = designer.elementView(key);
        return view == null ? null : zoomed(view.bounds());
    }

    /** The node drawn at a point in this component's coordinates, or {@code null}. */
    public NodeKey keyAt(Point point) {
        Point2D at = toDiagram(point);
        for (Object key : designer.elementKeys()) {
            if (designer.elementView(key).bounds().contains(at)) {
                return (NodeKey) key;
            }
        }
        return null;
    }

    /** An indicator's box in this component's coordinates, or {@code null} when the node does not show it. */
    public Rectangle indicatorBounds(NodeKey key, NodePainter.Indicator which) {
        ElementView view = designer.elementView(key);
        if (view == null) {
            return null;
        }
        if (which == NodePainter.Indicator.FOLDED) {
            Rectangle2D box = designer.textBounds(key, FreeMindMapping.FOLD_MARKER);
            return box == null || view.texts().getOrDefault(FreeMindMapping.FOLD_MARKER, "").isEmpty() ? null : zoomed(box);
        }
        String glyph = which == NodePainter.Indicator.LINK ? NodePainter.LINK_GLYPH : NodePainter.NOTE_GLYPH;
        String shown = view.texts().getOrDefault(FreeMindMapping.INDICATORS, "");
        Rectangle2D box = designer.textBounds(key, FreeMindMapping.INDICATORS);
        if (box == null || !shown.contains(glyph)) {
            return null;
        }
        // the indicators are one line in the slot's box: the link first, the note last
        double width = width(key, glyph);
        double x = shown.startsWith(glyph) ? box.getX() : box.getMaxX() - width;
        return zoomed(new Rectangle2D.Double(x, box.getY(), width, box.getHeight()));
    }

    /** The node's {@code LINK}, or {@code null}. */
    public String linkOf(NodeKey key) {
        MapNode node = node(key);
        return node == null ? null : node.link();
    }

    /** The node keyboard navigation starts from: the last one selected. */
    public NodeKey focusKey() {
        List<Object> selection = designer.selection();
        for (int i = selection.size() - 1; i >= 0; i--) {
            if (selection.get(i) instanceof NodeKey key) {
                return key;
            }
        }
        return null;
    }

    /**
     * The marquee being dragged, or {@code null}. The framework's selection tool paints it as a
     * layer while the mouse is down and keeps none afterwards.
     */
    public Rectangle marquee() {
        return null;
    }

    /** Scroll so the node is in view. */
    public void scrollTo(NodeKey key) {
        Rectangle box = boundsOf(key);
        if (box != null) {
            int margin = JBUIScale.scale(24);
            box.grow(margin, margin);
            scrollRectToVisible(box);
        }
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        NodeKey key = keyAt(event.getPoint());
        MapNode node = node(key);
        if (node == null) {
            return null;
        }
        if (contains(indicatorBounds(key, NodePainter.Indicator.NOTE), event.getPoint())) {
            return node.note();
        }
        if (contains(indicatorBounds(key, NodePainter.Indicator.LINK), event.getPoint())) {
            return node.link();
        }
        if (contains(indicatorBounds(key, NodePainter.Indicator.FOLDED), event.getPoint())) {
            return "Folded";
        }
        Rectangle2D icons = designer.textBounds(key, FreeMindMapping.ICONS);
        if (icons == null || node.icons().isEmpty()) {
            return null;
        }
        // the icons are one line in the slot's box, each followed by a space
        double x = icons.getX();
        double at = toDiagram(event.getPoint()).getX();
        for (String icon : node.icons()) {
            double next = x + width(key, FreeMindIcons.display(icon) + " ");
            if (at >= x && at < next && icons.contains(toDiagram(event.getPoint()))) {
                return icon;
            }
            x = next;
        }
        return null;
    }

    private double width(NodeKey key, String text) {
        Diagram diagram = designer.diagram();
        Font font = ElementMeasure.font(TextSlot.Style.PLAIN, diagram == null ? null : diagram.element(key));
        FontRenderContext frc = getFontMetrics(font).getFontRenderContext();
        return font.getStringBounds(text, frc).getWidth();
    }

    private MapNode node(NodeKey key) {
        MindMap map = designer.model();
        return map == null || key == null ? null : map.node(key);
    }

    private Rectangle zoomed(Rectangle2D box) {
        double zoom = zoom();
        return new Rectangle((int) Math.round(box.getX() * zoom), (int) Math.round(box.getY() * zoom), (int) Math.round(box.getWidth() * zoom),
                (int) Math.round(box.getHeight() * zoom));
    }

    private static boolean contains(Rectangle box, Point point) {
        return box != null && box.contains(point);
    }
}
