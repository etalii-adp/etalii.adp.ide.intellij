package etalii.adp.freemind.ui;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

import com.intellij.ui.ColorUtil;
import com.intellij.ui.scale.JBUIScale;

import etalii.adp.core.NodeView;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.model.Side;

/**
 * The designer's view of a map (research R6): one Swing component that paints the layout's boxes,
 * scaled by the zoom, and turns mouse and keys into selection, navigation, zoom and link clicks. It
 * changes nothing in the file: gestures that edit are added from outside, and may put children
 * (such as an in-place text field) on it, which is why it has no layout manager.
 * <p>
 * Boxes from {@link #mapLayout()} are unzoomed; {@link #boundsOf} and {@link #keyAt} are in this
 * component's (zoomed) coordinates.
 */
public final class MindMapCanvas extends JComponent {

    private final MindMapDesigner designer;
    private MindMapLayout.Result layout = MindMapLayout.Result.EMPTY;
    private NodeKey focusKey;
    private Point marqueeStart;
    private Rectangle marquee;
    private List<Object> selectionBeforeMarquee = List.of();

    MindMapCanvas(MindMapDesigner designer) {
        this.designer = designer;
        setLayout(null);
        setOpaque(true);
        setFocusable(true);
        setBackground(NodePainter.CANVAS_BACKGROUND);
        ToolTipManager.sharedInstance().registerComponent(this);
        Mouse mouse = new Mouse();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        addKeyListener(new Keys());
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    /** Lay out the designer's current model again. */
    void relayout() {
        MindMap map = designer.model();
        layout = MindMapLayout.layout(map, designer::isShownFolded, node -> NodePainter.measure(node, designer.isShownFolded(node), this));
        if (focusKey != null && !layout.views().containsKey(focusKey)) {
            focusKey = null;
        }
        revalidate();
        repaint();
    }

    /** The last layout, in unzoomed view coordinates. */
    public MindMapLayout.Result mapLayout() {
        return layout;
    }

    /** The laid-out box of a node, or {@code null} when it is not drawn. */
    public NodeView viewOf(Object key) {
        return layout.views().get(key);
    }

    /** The node's box in this component's coordinates, or {@code null} when it is not drawn. */
    public Rectangle boundsOf(NodeKey key) {
        NodeView view = viewOf(key);
        return view == null ? null : zoomed(view.bounds());
    }

    /** The node drawn at a point in this component's coordinates, or {@code null}. */
    public NodeKey keyAt(Point point) {
        double zoom = zoom();
        double x = point.x / zoom;
        double y = point.y / zoom;
        for (NodeView view : layout.views().values()) {
            if (view.bounds().contains(x, y)) {
                return (NodeKey) view.key();
            }
        }
        return null;
    }

    /** An indicator's box in this component's coordinates, or {@code null} when the node does not show it. */
    public Rectangle indicatorBounds(NodeKey key, NodePainter.Indicator which) {
        NodeView view = viewOf(key);
        if (view == null) {
            return null;
        }
        Rectangle box = NodePainter.indicator(NodePainter.parts(view, this), which);
        return box == null ? null : zoomed(box);
    }

    /** The node's {@code LINK}, or {@code null}. */
    public String linkOf(NodeKey key) {
        MapNode node = node(key);
        return node == null ? null : node.link();
    }

    /** The node keyboard navigation starts from: the last one clicked or moved to. */
    public NodeKey focusKey() {
        return focusKey;
    }

    /** The marquee being dragged, in this component's coordinates, or {@code null}. */
    public Rectangle marquee() {
        return marquee == null ? null : new Rectangle(marquee);
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

    private MapNode node(NodeKey key) {
        MindMap map = designer.model();
        return map == null || key == null ? null : map.node(key);
    }

    private double zoom() {
        return designer.viewState().zoom();
    }

    private Rectangle zoomed(Rectangle box) {
        double zoom = zoom();
        return new Rectangle((int) Math.round(box.x * zoom), (int) Math.round(box.y * zoom), (int) Math.round(box.width * zoom),
                (int) Math.round(box.height * zoom));
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        Dimension size = layout.size();
        double zoom = zoom();
        return new Dimension((int) Math.ceil(size.width * zoom), (int) Math.ceil(size.height * zoom));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (designer != null) {
            // the theme or the scale changed: colours and sizes are taken again
            relayout();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(NodePainter.CANVAS_BACKGROUND);
            Rectangle clip = g.getClipBounds();
            if (clip == null) {
                clip = new Rectangle(0, 0, getWidth(), getHeight());
            }
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            double zoom = zoom();
            g.scale(zoom, zoom);
            Rectangle visible = new Rectangle((int) Math.floor(clip.x / zoom) - 1, (int) Math.floor(clip.y / zoom) - 1,
                    (int) Math.ceil(clip.width / zoom) + 2, (int) Math.ceil(clip.height / zoom) + 2);
            paintMap(g, visible);
        } finally {
            g.dispose();
        }
        if (marquee != null) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setColor(ColorUtil.withAlpha(NodePainter.MARQUEE, 0.15));
                g2.fill(marquee);
                g2.setColor(NodePainter.MARQUEE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(marquee);
            } finally {
                g2.dispose();
            }
        }
    }

    private void paintMap(Graphics2D g, Rectangle visible) {
        Map<NodeKey, NodeView> views = layout.views();
        for (MindMapLayout.Connector connector : layout.connectors()) {
            Rectangle parent = views.get(connector.parent()).bounds();
            Rectangle child = views.get(connector.child()).bounds();
            if (parent.union(child).intersects(visible)) {
                NodePainter.paintConnector(g, parent, child, connector.left());
            }
        }
        Set<Object> selection = Set.copyOf(designer.selection());
        boolean focused = hasFocus();
        NodeKey root = designer.model() == null ? null : designer.model().root().key();
        for (NodeView view : views.values()) {
            Rectangle box = view.bounds();
            box.grow(JBUIScale.scale(4), JBUIScale.scale(4));
            if (box.intersects(visible)) {
                NodePainter.paint(g, view, view.key().equals(root), selection.contains(view.key()), focused && view.key().equals(focusKey), this);
            }
        }
        for (MindMapLayout.Arrow arrow : layout.arrows()) {
            NodePainter.paintArrow(g, views.get(arrow.source()).bounds(), views.get(arrow.target()).bounds(), arrow.startArrow(), arrow.endArrow());
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
        NodeView view = viewOf(key);
        List<Rectangle> icons = NodePainter.parts(view, this).icons();
        for (int i = 0; i < icons.size(); i++) {
            if (zoomed(icons.get(i)).contains(event.getPoint())) {
                return node.icons().get(i);
            }
        }
        return null;
    }

    private static boolean contains(Rectangle box, Point point) {
        return box != null && box.contains(point);
    }

    /** Selection by click, Ctrl or Shift click and marquee; link clicks; Ctrl+wheel zoom. */
    private final class Mouse extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger()) {
                return;
            }
            requestFocusInWindow();
            NodeKey key = keyAt(e.getPoint());
            boolean toggle = (e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
            boolean extend = (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0;
            if (key == null) {
                marqueeStart = e.getPoint();
                selectionBeforeMarquee = toggle || extend ? designer.selection() : List.of();
                if (!toggle && !extend) {
                    designer.select(List.of());
                }
                return;
            }
            focusKey = key;
            List<Object> selection = new ArrayList<>(designer.selection());
            if (toggle) {
                if (!selection.remove(key)) {
                    selection.add(key);
                }
            } else if (extend) {
                if (!selection.contains(key)) {
                    selection.add(key);
                }
            } else if (!selection.contains(key) || selection.size() == 1) {
                selection = List.of(key);
            }
            designer.select(selection);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (marqueeStart == null) {
                return;
            }
            Point at = e.getPoint();
            marquee = new Rectangle(Math.min(marqueeStart.x, at.x), Math.min(marqueeStart.y, at.y), Math.abs(at.x - marqueeStart.x),
                    Math.abs(at.y - marqueeStart.y));
            Set<Object> selection = new LinkedHashSet<>(selectionBeforeMarquee);
            for (NodeView view : layout.views().values()) {
                if (marquee.contains(zoomed(view.bounds()))) {
                    selection.add(view.key());
                }
            }
            designer.select(List.copyOf(selection));
            repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (marqueeStart != null) {
                marqueeStart = null;
                marquee = null;
                repaint();
                return;
            }
            if (SwingUtilities.isLeftMouseButton(e) && (e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK
                    | InputEvent.SHIFT_DOWN_MASK)) == 0) {
                // a plain click on one of several selected nodes selects it alone, once it is not a drag
                NodeKey key = keyAt(e.getPoint());
                if (key != null && designer.selection().size() > 1 && designer.selection().contains(key) && e.getClickCount() > 0) {
                    designer.select(List.of(key));
                }
            }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 1) {
                return;
            }
            NodeKey key = keyAt(e.getPoint());
            if (key != null && contains(indicatorBounds(key, NodePainter.Indicator.LINK), e.getPoint())) {
                LinkOpener.open(designer, linkOf(key));
            }
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            if ((e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0) {
                if (e.getWheelRotation() < 0) {
                    designer.zoomIn();
                } else if (e.getWheelRotation() > 0) {
                    designer.zoomOut();
                }
                e.consume();
                return;
            }
            // plain wheel scrolls, as it would without this listener
            JComponent scrollPane = (JComponent) SwingUtilities.getAncestorOfClass(JScrollPane.class, MindMapCanvas.this);
            if (scrollPane != null) {
                scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(MindMapCanvas.this, e, scrollPane));
            }
        }
    }

    /**
     * Arrow keys move the selection as in FreeMind: up and down between siblings on the same side,
     * left and right towards or away from the root. Keys with Ctrl, Alt or Meta are left to actions.
     */
    private final class Keys extends KeyAdapter {

        @Override
        public void keyPressed(KeyEvent e) {
            if ((e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0) {
                return;
            }
            MindMap map = designer.model();
            if (map == null) {
                return;
            }
            int code = e.getKeyCode();
            if (code != KeyEvent.VK_UP && code != KeyEvent.VK_DOWN && code != KeyEvent.VK_LEFT && code != KeyEvent.VK_RIGHT
                    && code != KeyEvent.VK_HOME) {
                return;
            }
            e.consume();
            MapNode current = node(currentKey());
            MapNode next;
            if (current == null || code == KeyEvent.VK_HOME) {
                next = map.root();
            } else {
                next = switch (code) {
                case KeyEvent.VK_UP -> sibling(map, current, -1);
                case KeyEvent.VK_DOWN -> sibling(map, current, 1);
                case KeyEvent.VK_LEFT -> sideways(map, current, Side.LEFT);
                default -> sideways(map, current, Side.RIGHT);
                };
            }
            if (next != null) {
                focusKey = next.key();
                boolean extend = (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0;
                List<Object> selection = new ArrayList<>(extend ? designer.selection() : List.of());
                selection.remove(next.key());
                selection.add(next.key());
                designer.select(selection);
                scrollTo(next.key());
            }
        }

        private NodeKey currentKey() {
            List<Object> selection = designer.selection();
            if (focusKey != null && selection.contains(focusKey)) {
                return focusKey;
            }
            return selection.isEmpty() ? null : (NodeKey) selection.get(selection.size() - 1);
        }

        /** The drawn sibling above or below, on the same side of the root. */
        private MapNode sibling(MindMap map, MapNode node, int direction) {
            MapNode parent = node.parent();
            if (parent == null) {
                return null;
            }
            Side side = map.sideOf(node);
            List<MapNode> siblings = parent.children().stream().filter(n -> map.sideOf(n) == side && viewOf(n.key()) != null).toList();
            int index = siblings.indexOf(node) + direction;
            return index >= 0 && index < siblings.size() ? siblings.get(index) : null;
        }

        /** Towards the root when the node is on {@code towards}'s other side, else out to its middle child. */
        private MapNode sideways(MindMap map, MapNode node, Side towards) {
            Side side = sideOfBranch(map, node);
            if (side != null && side != towards) {
                return node.parent();
            }
            if (designer.isShownFolded(node)) {
                return null;
            }
            List<MapNode> children = node.children().stream()
                    .filter(n -> (node.parent() != null || map.sideOf(n) == towards) && viewOf(n.key()) != null).toList();
            if (children.isEmpty()) {
                return null;
            }
            double centre = viewOf(node.key()).bounds().getCenterY();
            return children.stream().min(Comparator.comparingDouble(n -> Math.abs(viewOf(n.key()).bounds().getCenterY() - centre))).orElse(null);
        }

        /** The side of the root the node's branch is on, or {@code null} for the root. */
        private Side sideOfBranch(MindMap map, MapNode node) {
            MapNode n = node;
            while (n.parent() != null && n.parent().parent() != null) {
                n = n.parent();
            }
            return n.parent() == null ? null : map.sideOf(n);
        }
    }
}
