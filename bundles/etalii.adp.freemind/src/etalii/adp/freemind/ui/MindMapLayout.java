package etalii.adp.freemind.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.draw2d.AbstractLayout;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.geometry.Rectangle;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.model.Side;

/**
 * Places node figures as FreeMind does (FR-013): the root centred on the origin, first-level
 * branches to its left or right by {@link MindMap#sideOf}, each node's children stacked and
 * centred on it. A node's {@code HGAP} is its distance from its parent, its {@code VGAP} the space
 * between its children and its {@code VSHIFT} a vertical offset; zero means FreeMind's default.
 * Nodes are never positioned freely. One pass, linear in the number of drawn nodes.
 */
public class MindMapLayout extends AbstractLayout {

    public static final int DEFAULT_HGAP = 20;
    public static final int DEFAULT_VGAP = 3;

    /** A parent-child line to draw, from the parent's side facing the child. */
    public record Edge(IFigure parent, IFigure child, boolean left) {
    }

    private final Supplier<ViewState> state;
    private final Function<NodeKey, IFigure> figures;
    private final List<Edge> edges = new ArrayList<>();
    private final Map<MapNode, Integer> heights = new HashMap<>();
    private Rectangle extent = new Rectangle();

    public MindMapLayout(Supplier<ViewState> state, Function<NodeKey, IFigure> figures) {
        this.state = state;
        this.figures = figures;
    }

    /** The parent-child lines of the last layout. */
    public List<Edge> edges() {
        return edges;
    }

    @Override
    public void layout(IFigure container) {
        edges.clear();
        heights.clear();
        ViewState view = state.get();
        MindMap map = view == null ? null : view.map();
        IFigure rootFigure = map == null ? null : figures.apply(map.root().key());
        if (rootFigure == null) {
            return;
        }
        MapNode root = map.root();
        Dimension size = rootFigure.getPreferredSize();
        Rectangle rootBounds = new Rectangle(-size.width / 2, -size.height / 2, size.width, size.height);
        rootFigure.setBounds(rootBounds);
        extent = rootBounds.getCopy();
        if (!view.isShownFolded(root)) {
            List<MapNode> left = new ArrayList<>();
            List<MapNode> right = new ArrayList<>();
            for (MapNode child : root.children()) {
                (map.sideOf(child) == Side.LEFT ? left : right).add(child);
            }
            placeChildren(view, root, rootFigure, rootBounds, left, true);
            placeChildren(view, root, rootFigure, rootBounds, right, false);
        }
        heights.clear();
        container.repaint();
    }

    private void placeChildren(ViewState view, MapNode parent, IFigure parentFigure, Rectangle parentBounds, List<MapNode> children,
            boolean left) {
        int gap = vgap(parent);
        int total = 0;
        for (MapNode child : children) {
            total += height(view, child);
        }
        total += gap * Math.max(0, children.size() - 1);
        int y = parentBounds.getCenter().y - total / 2;
        for (MapNode child : children) {
            int block = height(view, child);
            IFigure figure = figures.apply(child.key());
            if (figure != null) {
                Dimension size = figure.getPreferredSize();
                int hgap = child.hgap() == 0 ? DEFAULT_HGAP : child.hgap();
                int x = left ? parentBounds.x - hgap - size.width : parentBounds.right() + hgap;
                Rectangle bounds = new Rectangle(x, y + (block - size.height) / 2 + child.vshift(), size.width, size.height);
                figure.setBounds(bounds);
                extent.union(bounds);
                edges.add(new Edge(parentFigure, figure, left));
                if (!view.isShownFolded(child)) {
                    placeChildren(view, child, figure, bounds, child.children(), left);
                }
            }
            y += block + gap;
        }
    }

    /** The height of the node's drawn subtree. */
    private int height(ViewState view, MapNode node) {
        Integer known = heights.get(node);
        if (known != null) {
            return known;
        }
        IFigure figure = figures.apply(node.key());
        int own = figure == null ? 0 : figure.getPreferredSize().height;
        int height = own;
        if (!view.isShownFolded(node) && !node.children().isEmpty()) {
            int sum = vgap(node) * (node.children().size() - 1);
            for (MapNode child : node.children()) {
                sum += height(view, child);
            }
            height = Math.max(own, sum);
        }
        heights.put(node, height);
        return height;
    }

    private static int vgap(MapNode node) {
        return node.vgap() == 0 ? DEFAULT_VGAP : node.vgap();
    }

    @Override
    protected Dimension calculatePreferredSize(IFigure container, int wHint, int hHint) {
        return extent.getSize();
    }
}
