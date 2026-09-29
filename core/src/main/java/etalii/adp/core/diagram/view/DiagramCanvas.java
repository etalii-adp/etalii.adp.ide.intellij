package etalii.adp.core.diagram.view;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import com.intellij.util.ui.JBUI;

import etalii.adp.core.diagram.Space;
import etalii.adp.core.diagram.view.Scene.ConnectionRender;
import etalii.adp.core.diagram.view.Scene.ElementRender;

/**
 * The drawing surface of a diagram (research R10, R16): one Swing component that paints
 * the diagram's {@link Scene} under the zoom, culled to the clip. It paints diagram-space layers
 * below the content, then connections, then elements in document order, then diagram-space layers
 * above the content, then view-space layers in viewport pixels. It offers mouse and key events to
 * its tools in order until one consumes them, converts coordinates and hit-tests. It changes
 * nothing itself; tools may put children (such as an in-place editor) on it, so it has no layout
 * manager. A diagram may extend it, through {@link DiagramFileEditor#createCanvas()}, for what only
 * it shows, such as tooltips.
 */
public class DiagramCanvas extends JComponent {

    private static final int MARGIN = 40;
    private static final int HIT_TOLERANCE = 4;

    /** An anchor under the pointer: the element and the anchor. */
    public record AnchorHit(Object key, AnchorView anchor) {
    }

    private final DiagramFileEditor fileEditor;
    private final List<CanvasLayer> layers = new CopyOnWriteArrayList<>();
    private final List<CanvasTool> tools = new CopyOnWriteArrayList<>();
    private final Map<Object, Shape> hitShapes = new HashMap<>();
    private Scene scene = Scene.EMPTY;

    protected DiagramCanvas(DiagramFileEditor fileEditor) {
        this.fileEditor = fileEditor;
        setLayout(null);
        setOpaque(true);
        setFocusable(true);
        setBackground(ElementPainter.CANVAS);
        Events events = new Events();
        addMouseListener(events);
        addMouseMotionListener(events);
        addMouseWheelListener(events);
        addKeyListener(events);
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

    public DiagramFileEditor tool() {
        return fileEditor;
    }

    /** What is drawn now. */
    public Scene scene() {
        return scene;
    }

    void setScene(Scene scene) {
        this.scene = scene;
        hitShapes.clear();
        revalidate();
        repaint();
    }

    /** Paint {@code layer} from now on, in its space and order. */
    public void addLayer(CanvasLayer layer) {
        layers.add(layer);
        List<CanvasLayer> sorted = new ArrayList<>(layers);
        sorted.sort(Comparator.comparingInt(CanvasLayer::order));
        layers.clear();
        layers.addAll(sorted);
        repaint();
    }

    public void removeLayer(CanvasLayer layer) {
        layers.remove(layer);
        repaint();
    }

    /** Offer events to {@code tool} after the tools added before it. */
    public void addTool(CanvasTool tool) {
        tools.add(tool);
    }

    public void removeTool(CanvasTool tool) {
        tools.remove(tool);
    }

    public double zoom() {
        return fileEditor.viewState().zoom();
    }

    // simplified: diagram (0, 0) is the canvas's top left, so content at negative coordinates is cut off;
    // if a format needs it, shift by the scene's extent here and in toCanvas

    /** A point of this component in unzoomed diagram coordinates. */
    public Point2D toDiagram(Point point) {
        double zoom = zoom();
        return new Point2D.Double(point.x / zoom, point.y / zoom);
    }

    /** A diagram point in this component's coordinates. */
    public Point toCanvas(Point2D point) {
        double zoom = zoom();
        return new Point((int) Math.round(point.getX() * zoom), (int) Math.round(point.getY() * zoom));
    }

    /** A diagram area in this component's coordinates. */
    public Rectangle toCanvas(Rectangle2D area) {
        double zoom = zoom();
        int x = (int) Math.floor(area.getX() * zoom);
        int y = (int) Math.floor(area.getY() * zoom);
        return new Rectangle(x, y, (int) Math.ceil(area.getMaxX() * zoom) - x, (int) Math.ceil(area.getMaxY() * zoom) - y);
    }

    /** The top left of the visible part, in this component's coordinates: where viewport pixel (0, 0) is. */
    public Point viewportPosition() {
        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
        return viewport == null ? new Point() : viewport.getViewPosition();
    }

    /** The visible part in unzoomed diagram coordinates. */
    public Rectangle2D visibleArea() {
        Rectangle visible = getVisibleRect();
        if (visible.isEmpty()) {
            visible = new Rectangle(0, 0, getWidth(), getHeight());
        }
        double zoom = zoom();
        return new Rectangle2D.Double(visible.x / zoom, visible.y / zoom, visible.width / zoom, visible.height / zoom);
    }

    /** The topmost element drawn at a diagram point, or {@code null}. */
    public Object elementAt(Point2D point) {
        List<ElementRender> renders = new ArrayList<>(scene.elements().values());
        for (int i = renders.size() - 1; i >= 0; i--) {
            if (hits(renders.get(i), point)) {
                return renders.get(i).view().key();
            }
        }
        return null;
    }

    /** The topmost connection drawn within a few pixels of a diagram point, or {@code null}. */
    public Object connectionAt(Point2D point) {
        List<ConnectionRender> renders = new ArrayList<>(scene.connections().values());
        for (int i = renders.size() - 1; i >= 0; i--) {
            if (hits(renders.get(i), point)) {
                return renders.get(i).view().key();
            }
        }
        return null;
    }

    /**
     * The item drawn on top at a diagram point, or {@code null}: in the diagram's painting order when
     * it has one, else the element, then the connection.
     */
    public Object itemAt(Point2D point) {
        if (scene.order().isEmpty()) {
            Object element = elementAt(point);
            return element != null ? element : connectionAt(point);
        }
        for (int i = scene.order().size() - 1; i >= 0; i--) {
            Object key = scene.order().get(i);
            ElementRender element = scene.elements().get(key);
            ConnectionRender connection = scene.connections().get(key);
            if (element != null && hits(element, point) || connection != null && hits(connection, point)) {
                return key;
            }
        }
        return null;
    }

    private static boolean hits(ElementRender render, Point2D point) {
        return render.bounds().contains(point) && (!render.view().outline().drawn() || render.outline().contains(point)
                || render.bounds().getWidth() < 8 || render.bounds().getHeight() < 8);
    }

    private boolean hits(ConnectionRender render, Point2D point) {
        if (!render.extent().contains(point)) {
            return false;
        }
        double tolerance = JBUI.scale(HIT_TOLERANCE) / zoom();
        Shape hit = hitShapes.computeIfAbsent(render.view().key(),
                key -> new BasicStroke((float) (2 * tolerance + render.view().thickness())).createStrokedShape(render.path()));
        return hit.contains(point) || render.labels().values().stream().anyMatch(label -> label.box().contains(point));
    }

    /** The elements whose bounds lie wholly inside a diagram area, in painting order. */
    public List<Object> elementsIn(Rectangle2D area) {
        return scene.elements().values().stream().filter(r -> area.contains(r.bounds())).map(r -> r.view().key()).toList();
    }

    /** The connections whose route lies wholly inside a diagram area, in painting order. */
    public List<Object> connectionsIn(Rectangle2D area) {
        return scene.connections().values().stream().filter(r -> area.contains(r.path().getBounds2D())).map(r -> r.view().key()).toList();
    }

    /**
     * The anchor nearest a diagram point within a few pixels, visible or not, or {@code null}. A
     * perimeter anchor is only near the outline, where the line from the centre toward the point
     * crosses it, so the middle of an element still starts a move.
     */
    public AnchorHit anchorAt(Point2D point) {
        double tolerance = JBUI.scale(HIT_TOLERANCE + 2) / zoom();
        AnchorHit best = null;
        double bestDistance = tolerance;
        for (ElementRender render : scene.elements().values()) {
            if (!render.extent().contains(point)) {
                continue;
            }
            for (AnchorView anchor : render.anchors()) {
                if (render.type() != null && render.type().anchor(anchor.id()) != null && render.type().anchor(anchor.id()).perimeter()) {
                    anchor = new AnchorView(anchor.id(), AnchorGeometry.perimeter(render.outline(), anchor.position(), point), anchor.visible());
                }
                double distance = anchor.position().distance(point);
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = new AnchorHit(render.view().key(), anchor);
                }
            }
        }
        return best;
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        Rectangle2D extent = scene.extent();
        double zoom = zoom();
        int margin = JBUI.scale(MARGIN);
        return new Dimension((int) Math.ceil(Math.max(0, extent.getMaxX()) * zoom) + margin, (int) Math.ceil(Math.max(0, extent.getMaxY()) * zoom) + margin);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (fileEditor != null) {
            // the theme or the scale changed: colours, fonts and sizes are taken again
            fileEditor.relayoutAll();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Rectangle clip = graphics.getClipBounds();
        if (clip == null) {
            clip = new Rectangle(0, 0, getWidth(), getHeight());
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(ElementPainter.CANVAS);
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            double zoom = zoom();
            g.scale(zoom, zoom);
            Rectangle2D visible = new Rectangle2D.Double(clip.x / zoom - 1, clip.y / zoom - 1, clip.width / zoom + 2, clip.height / zoom + 2);
            paintLayers(g, visible, layer -> layer.space() == Space.DIAGRAM && layer.order() < 0);
            Set<Object> selection = Set.copyOf(fileEditor.selection());
            List<Object> order = scene.order().isEmpty() ? new ArrayList<>(scene.connections().keySet()) : scene.order();
            if (scene.order().isEmpty()) {
                order.addAll(scene.elements().keySet());
            }
            for (Object key : order) {
                ConnectionRender connection = scene.connections().get(key);
                ElementRender element = scene.elements().get(key);
                if (connection != null && connection.extent().intersects(visible)) {
                    ConnectionPainter.paint(g, connection, selection.contains(key));
                } else if (element != null && element.extent().intersects(visible)) {
                    ElementPainter.paint(g, element, selection.contains(key));
                }
            }
            paintLayers(g, visible, layer -> layer.space() == Space.DIAGRAM && layer.order() >= 0);
        } finally {
            g.dispose();
        }
        if (layers.stream().anyMatch(layer -> layer.space() == Space.VIEW)) {
            Graphics2D v = (Graphics2D) graphics.create();
            try {
                Point origin = viewportPosition();
                v.translate(origin.x, origin.y);
                Rectangle2D visible = new Rectangle2D.Double(clip.x - origin.x, clip.y - origin.y, clip.width, clip.height);
                paintLayers(v, visible, layer -> layer.space() == Space.VIEW);
            } finally {
                v.dispose();
            }
        }
    }

    private void paintLayers(Graphics2D g, Rectangle2D visible, Predicate<CanvasLayer> which) {
        for (CanvasLayer layer : layers) {
            if (which.test(layer)) {
                Graphics2D copy = (Graphics2D) g.create();
                try {
                    layer.paint(copy, this, visible);
                } finally {
                    copy.dispose();
                }
            }
        }
    }

    private void offer(Consumer<CanvasTool> handler, InputEvent event) {
        for (CanvasTool tool : tools) {
            handler.accept(tool);
            if (event.isConsumed()) {
                return;
            }
        }
    }

    /** Mouse and keys, offered to the tools in order. */
    private final class Events implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {

        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            offer(tool -> tool.mousePressed(e), e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            offer(tool -> tool.mouseReleased(e), e);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            offer(tool -> tool.mouseClicked(e), e);
        }

        @Override
        public void mouseEntered(MouseEvent e) {
        }

        @Override
        public void mouseExited(MouseEvent e) {
            offer(tool -> tool.mouseExited(e), e);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            offer(tool -> tool.mouseDragged(e), e);
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            offer(tool -> tool.mouseMoved(e), e);
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            offer(tool -> tool.mouseWheelMoved(e), e);
            if (!e.isConsumed()) {
                // plain wheel scrolls, as it would without this listener
                JComponent scrollPane = (JComponent) SwingUtilities.getAncestorOfClass(JScrollPane.class, DiagramCanvas.this);
                if (scrollPane != null) {
                    scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(DiagramCanvas.this, e, scrollPane));
                }
            }
        }

        @Override
        public void keyPressed(KeyEvent e) {
            offer(tool -> tool.keyPressed(e), e);
        }

        @Override
        public void keyReleased(KeyEvent e) {
            offer(tool -> tool.keyReleased(e), e);
        }

        @Override
        public void keyTyped(KeyEvent e) {
            offer(tool -> tool.keyTyped(e), e);
        }
    }
}
