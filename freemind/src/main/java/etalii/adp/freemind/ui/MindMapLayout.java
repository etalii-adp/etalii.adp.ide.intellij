package etalii.adp.freemind.ui;

import java.awt.Rectangle;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.intellij.util.ui.JBUI;

import etalii.adp.core.NodeView;
import etalii.adp.core.ViewState;
import etalii.adp.core.diagram.DiagramLayout;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;
import etalii.adp.freemind.model.ArrowLink;
import etalii.adp.freemind.model.NodeKey;

/**
 * Places nodes as FreeMind does (spec 001 FR-013, research R7): the root in the middle, first-level
 * branches to its left or right by their side, each node's children stacked and centred on it. A
 * node's {@code HGAP} is its distance from its parent, its {@code VGAP} the space between its
 * children and its {@code VSHIFT} a vertical offset; zero means FreeMind's default. A branch shown
 * folded, by the file or for display only ({@link ViewState#foldOverride}), hides its children.
 * One pass, linear in the number of drawn nodes.
 * <p>
 * The boxes are in whole pixels, moved so that every box lies inside the canvas with a margin. All
 * distances go through {@link JBUI#scale(int)}.
 */
public final class MindMapLayout implements DiagramLayout {

    public static final int DEFAULT_HGAP = 20;
    public static final int DEFAULT_VGAP = 3;

    /** Space around the map, before scaling. */
    static final int MARGIN = 24;

    /** An arrow link between two drawn nodes, with an arrowhead at each end the file asks for one. */
    public record Arrow(ArrowLink link, NodeKey source, NodeKey target, boolean startArrow, boolean endArrow) {
    }

    /** The drawn nodes in document order. */
    public record Result(Map<NodeKey, NodeView> views) {
    }

    @Override
    public Map<Object, Rectangle2D> layout(Diagram diagram, ViewState view, Function<Element, Dimension2D> measure) {
        return diagram.elements().isEmpty() ? Map.of() : new Run(diagram, view, measure).run();
    }

    /** Whether an element with children is drawn collapsed: the file's {@code FOLDED}, unless folded or unfolded for display only. */
    static boolean shownFolded(Element element, ViewState view) {
        Boolean override = view.foldOverride(element.key());
        return override != null ? override : "true".equals(element.property(FreeMindMapping.FOLDED));
    }

    private static final class Run {

        private final ViewState view;
        private final Function<Element, Dimension2D> measure;
        private final Element root;
        private final Map<Object, List<Element>> children = new HashMap<>();
        private final Map<Object, Integer> heights = new HashMap<>();
        private final Map<Object, Rectangle> sizes = new HashMap<>();
        private final Map<Object, Rectangle> placed = new LinkedHashMap<>();

        Run(Diagram diagram, ViewState view, Function<Element, Dimension2D> measure) {
            this.view = view;
            this.measure = measure;
            Element top = null;
            for (Element element : diagram.elements().values()) {
                if (element.parent() == null) {
                    top = top == null ? element : top;
                } else {
                    children.computeIfAbsent(element.parent(), key -> new ArrayList<>()).add(element);
                }
            }
            this.root = top;
        }

        Map<Object, Rectangle2D> run() {
            Rectangle rootBounds = size(root);
            rootBounds.setLocation(-rootBounds.width / 2, -rootBounds.height / 2);
            placed.put(root.key(), rootBounds);
            if (!folded(root)) {
                List<Element> left = new ArrayList<>();
                List<Element> right = new ArrayList<>();
                for (Element child : children(root)) {
                    ("left".equals(child.property(FreeMindMapping.SIDE)) ? left : right).add(child);
                }
                placeChildren(root, rootBounds, left, true);
                placeChildren(root, rootBounds, right, false);
            }

            Rectangle extent = null;
            for (Rectangle box : placed.values()) {
                extent = extent == null ? new Rectangle(box) : extent.union(box);
            }
            int dx = JBUI.scale(MARGIN) - extent.x;
            int dy = JBUI.scale(MARGIN) - extent.y;
            Map<Object, Rectangle2D> moved = new LinkedHashMap<>();
            placed.forEach((key, box) -> moved.put(key, new Rectangle(box.x + dx, box.y + dy, box.width, box.height)));
            return moved;
        }

        private void placeChildren(Element parent, Rectangle parentBounds, List<Element> nodes, boolean left) {
            int gap = vgap(parent);
            int total = gap * Math.max(0, nodes.size() - 1);
            for (Element child : nodes) {
                total += height(child);
            }
            int y = (int) parentBounds.getCenterY() - total / 2;
            for (Element child : nodes) {
                int block = height(child);
                Rectangle bounds = size(child);
                int hgap = JBUI.scale(number(child, FreeMindMapping.HGAP, DEFAULT_HGAP));
                int x = left ? parentBounds.x - hgap - bounds.width : parentBounds.x + parentBounds.width + hgap;
                bounds.setLocation(x, y + (block - bounds.height) / 2 + JBUI.scale(number(child, FreeMindMapping.VSHIFT, 0)));
                placed.put(child.key(), bounds);
                if (!folded(child)) {
                    placeChildren(child, bounds, children(child), left);
                }
                y += block + gap;
            }
        }

        /** The height of the element's drawn subtree. */
        private int height(Element element) {
            Integer known = heights.get(element.key());
            if (known != null) {
                return known;
            }
            int own = size(element).height;
            int height = own;
            List<Element> below = children(element);
            if (!folded(element) && !below.isEmpty()) {
                int sum = vgap(element) * (below.size() - 1);
                for (Element child : below) {
                    sum += height(child);
                }
                height = Math.max(own, sum);
            }
            heights.put(element.key(), height);
            return height;
        }

        /** The element's measured size at the origin, in whole pixels; a fresh box each time. */
        private Rectangle size(Element element) {
            Rectangle size = sizes.computeIfAbsent(element.key(), key -> {
                Dimension2D measured = measure.apply(element);
                return new Rectangle(0, 0, (int) Math.ceil(measured.getWidth()), (int) Math.ceil(measured.getHeight()));
            });
            return new Rectangle(size);
        }

        private List<Element> children(Element element) {
            return children.getOrDefault(element.key(), List.of());
        }

        private boolean folded(Element element) {
            return !children(element).isEmpty() && shownFolded(element, view);
        }

        private static int vgap(Element element) {
            return JBUI.scale(number(element, FreeMindMapping.VGAP, DEFAULT_VGAP));
        }

        /** A whole-number layout property; zero or absent means {@code byDefault}. */
        private static int number(Element element, String property, int byDefault) {
            String value = element.property(property);
            int number = value.isEmpty() ? 0 : Integer.parseInt(value);
            return number == 0 ? byDefault : number;
        }
    }
}
