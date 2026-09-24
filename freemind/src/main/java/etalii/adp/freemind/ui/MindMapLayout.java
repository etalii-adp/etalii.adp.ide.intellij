package etalii.adp.freemind.ui;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import com.intellij.util.ui.JBUI;

import etalii.adp.core.NodeView;
import etalii.adp.freemind.model.ArrowLink;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.model.Side;

/**
 * Places nodes as FreeMind does (spec 001 FR-013): the root in the middle, first-level branches to
 * its left or right by {@link MindMap#sideOf}, each node's children stacked and centred on it. A
 * node's {@code HGAP} is its distance from its parent, its {@code VGAP} the space between its
 * children and its {@code VSHIFT} a vertical offset; zero means FreeMind's default. Nodes are never
 * positioned freely. One pass, linear in the number of drawn nodes.
 * <p>
 * The result is in unzoomed view coordinates, moved so that every box lies inside the canvas with
 * a margin. All distances go through {@link JBUI#scale(int)}.
 */
public final class MindMapLayout {

    public static final int DEFAULT_HGAP = 20;
    public static final int DEFAULT_VGAP = 3;

    /** Space around the map, before scaling. */
    static final int MARGIN = 24;

    /** A parent-child line, from the parent's side facing the child. */
    public record Connector(NodeKey parent, NodeKey child, boolean left) {
    }

    /** An arrow link between two drawn nodes, with an arrowhead at each end the file asks for one. */
    public record Arrow(ArrowLink link, NodeKey source, NodeKey target, boolean startArrow, boolean endArrow) {
    }

    /** One layout: the drawn nodes in document order, their lines and arrow links, and the canvas size. */
    public record Result(Map<NodeKey, NodeView> views, List<Connector> connectors, List<Arrow> arrows, Dimension size) {

        public static final Result EMPTY = new Result(Map.of(), List.of(), List.of(), new Dimension());
    }

    private final Predicate<MapNode> shownFolded;
    private final Function<MapNode, NodeView> measure;
    private final Map<MapNode, Integer> heights = new HashMap<>();
    private final Map<MapNode, NodeView> measured = new HashMap<>();
    private final Map<NodeKey, NodeView> views = new LinkedHashMap<>();
    private final List<Connector> connectors = new ArrayList<>();

    /**
     * @param shownFolded whether a node is drawn collapsed, the file's {@code FOLDED} or a display override
     * @param measure a node's box at the origin, with its size and everything it shows
     */
    private MindMapLayout(Predicate<MapNode> shownFolded, Function<MapNode, NodeView> measure) {
        this.shownFolded = shownFolded;
        this.measure = measure;
    }

    /** Lay out {@code map}; {@link Result#EMPTY} when there is none. */
    public static Result layout(MindMap map, Predicate<MapNode> shownFolded, Function<MapNode, NodeView> measure) {
        if (map == null) {
            return Result.EMPTY;
        }
        return new MindMapLayout(shownFolded, measure).run(map);
    }

    private Result run(MindMap map) {
        MapNode root = map.root();
        NodeView rootView = measured(root);
        Rectangle rootBounds = rootView.bounds();
        rootBounds.setLocation(-rootBounds.width / 2, -rootBounds.height / 2);
        place(rootView, rootBounds);
        if (!shownFolded.test(root)) {
            List<MapNode> left = new ArrayList<>();
            List<MapNode> right = new ArrayList<>();
            for (MapNode child : root.children()) {
                (map.sideOf(child) == Side.LEFT ? left : right).add(child);
            }
            placeChildren(root, rootBounds, left, true);
            placeChildren(root, rootBounds, right, false);
        }

        Rectangle extent = null;
        for (NodeView view : views.values()) {
            extent = extent == null ? view.bounds() : extent.union(view.bounds());
        }
        int margin = JBUI.scale(MARGIN);
        int dx = margin - extent.x;
        int dy = margin - extent.y;
        Map<NodeKey, NodeView> moved = new LinkedHashMap<>();
        for (NodeView view : views.values()) {
            Rectangle bounds = view.bounds();
            bounds.translate(dx, dy);
            moved.put((NodeKey) view.key(), withBounds(view, bounds));
        }
        Dimension size = new Dimension(extent.width + 2 * margin, extent.height + 2 * margin);
        return new Result(moved, List.copyOf(connectors), arrows(map, moved), size);
    }

    private void placeChildren(MapNode parent, Rectangle parentBounds, List<MapNode> children, boolean left) {
        int gap = vgap(parent);
        int total = 0;
        for (MapNode child : children) {
            total += height(child);
        }
        total += gap * Math.max(0, children.size() - 1);
        int y = (int) parentBounds.getCenterY() - total / 2;
        for (MapNode child : children) {
            int block = height(child);
            NodeView view = measured(child);
            Rectangle bounds = view.bounds();
            int hgap = JBUI.scale(child.hgap() == 0 ? DEFAULT_HGAP : child.hgap());
            int x = left ? parentBounds.x - hgap - bounds.width : parentBounds.x + parentBounds.width + hgap;
            bounds.setLocation(x, y + (block - bounds.height) / 2 + JBUI.scale(child.vshift()));
            place(view, bounds);
            connectors.add(new Connector(parent.key(), child.key(), left));
            if (!shownFolded.test(child)) {
                placeChildren(child, bounds, child.children(), left);
            }
            y += block + gap;
        }
    }

    private void place(NodeView view, Rectangle bounds) {
        views.put((NodeKey) view.key(), withBounds(view, bounds));
    }

    /** The height of the node's drawn subtree. */
    private int height(MapNode node) {
        Integer known = heights.get(node);
        if (known != null) {
            return known;
        }
        int own = measured(node).bounds().height;
        int height = own;
        if (!shownFolded.test(node) && !node.children().isEmpty()) {
            int sum = vgap(node) * (node.children().size() - 1);
            for (MapNode child : node.children()) {
                sum += height(child);
            }
            height = Math.max(own, sum);
        }
        heights.put(node, height);
        return height;
    }

    private NodeView measured(MapNode node) {
        return measured.computeIfAbsent(node, measure);
    }

    private static int vgap(MapNode node) {
        return JBUI.scale(node.vgap() == 0 ? DEFAULT_VGAP : node.vgap());
    }

    private static List<Arrow> arrows(MindMap map, Map<NodeKey, NodeView> views) {
        List<Arrow> arrows = new ArrayList<>();
        for (ArrowLink link : map.arrowLinks()) {
            MapNode destination = map.nodeById(link.destinationId());
            if (destination != null && views.containsKey(link.source()) && views.containsKey(destination.key())) {
                arrows.add(new Arrow(link, link.source(), destination.key(), arrowhead(link.startArrow(), false), arrowhead(link.endArrow(), true)));
            }
        }
        return List.copyOf(arrows);
    }

    /** FreeMind writes {@code None} for no arrowhead; any other value is one, and an absent one is the default. */
    private static boolean arrowhead(String value, boolean byDefault) {
        return value == null ? byDefault : !"None".equals(value);
    }

    static NodeView withBounds(NodeView view, Rectangle bounds) {
        return new NodeView(view.key(), bounds, view.text(), view.foreground(), view.background(), view.font(), view.icons(), view.hasLink(),
                view.hasNote(), view.folded());
    }
}
