package etalii.adp.core.diagram.edit;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Objects;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.diagram.Anchor;
import etalii.adp.core.diagram.Direction;
import etalii.adp.core.diagram.EndSide;
import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.model.Connection;
import etalii.adp.core.diagram.model.End;
import etalii.adp.core.diagram.view.AnchorView;
import etalii.adp.core.diagram.view.CanvasLayer;
import etalii.adp.core.diagram.view.CanvasTool;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramCanvas.AnchorHit;
import etalii.adp.core.diagram.view.DiagramFileEditor;
import etalii.adp.core.diagram.view.ElementPainter;
import etalii.adp.core.diagram.view.Scene.ConnectionRender;
import etalii.adp.core.diagram.view.Scene.ElementRender;

/**
 * Drawing connections between anchors (FR-017, FR-018). A drag from an anchor draws a connection
 * of the type armed in the toolbox, or else of the first type the anchor accepts; while dragging,
 * the anchors that may take its other end are ringed, and a refused target gets the "not allowed"
 * feedback with the command's reason. Released on an anchor, or on an element's outline, it is one
 * "Connect"; released elsewhere it changes nothing. A drag from an end of a selected connection
 * moves that end, as one "Reconnect". Escape cancels.
 */
public final class ConnectTool implements CanvasTool, CanvasLayer {

    private static final int THRESHOLD = 3;
    private static final int END_REACH = 6;

    private final DiagramFileEditor fileEditor;
    private final DiagramCanvas canvas;
    private final RefusalFeedback feedback;
    private String type;
    private End source;
    private Object reconnecting;
    private EndSide side;
    private Point2D from;
    private Point pressedAt;
    private boolean dragging;
    private Point2D pointer;
    private End target;
    private Verdict verdict;

    public ConnectTool(DiagramFileEditor fileEditor, RefusalFeedback feedback) {
        this.fileEditor = fileEditor;
        this.canvas = fileEditor.canvas();
        this.feedback = feedback;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        reset();
        if (!SelectionTool.plainLeftPress(e) || !fileEditor.isEditable() || fileEditor.diagram() == null) {
            return;
        }
        Point2D point = canvas.toDiagram(e.getPoint());
        if (!startReconnect(point) && !startConnect(point)) {
            return;
        }
        pressedAt = e.getPoint();
        e.consume();
    }

    /** A press on an end of a selected connection. */
    private boolean startReconnect(Point2D point) {
        double reach = JBUIScale.scale(END_REACH) / canvas.zoom();
        for (Object key : fileEditor.selection()) {
            ConnectionRender render = canvas.scene().connections().get(key);
            Connection connection = fileEditor.diagram().connection(key);
            if (render == null || connection == null || render.route().isEmpty()) {
                continue;
            }
            List<Point2D> route = render.route();
            Point2D first = route.get(0);
            Point2D last = route.get(route.size() - 1);
            EndSide end = last.distance(point) <= reach ? EndSide.TARGET : first.distance(point) <= reach ? EndSide.SOURCE : null;
            if (end != null) {
                reconnecting = key;
                side = end;
                type = connection.type();
                from = end == EndSide.TARGET ? first : last;
                return true;
            }
        }
        return false;
    }

    /** A press on an anchor that accepts a connection type, or any anchor while one is armed. */
    private boolean startConnect(Point2D point) {
        AnchorHit hit = canvas.anchorAt(point);
        if (hit == null) {
            return false;
        }
        String connectionType = fileEditor.armedConnectionType();
        if (connectionType == null) {
            ElementRender render = canvas.scene().elements().get(hit.key());
            Anchor anchor = render == null || render.type() == null ? null : render.type().anchor(hit.anchor().id());
            connectionType = anchor == null || anchor.accepts().isEmpty() ? null : anchor.accepts().keySet().iterator().next();
        }
        if (connectionType == null) {
            return false;
        }
        type = connectionType;
        source = new End(hit.key(), hit.anchor().id());
        from = hit.anchor().position();
        return true;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (type == null) {
            return;
        }
        e.consume();
        if (!dragging && pressedAt.distance(e.getPoint()) < JBUIScale.scale(THRESHOLD)) {
            return;
        }
        dragging = true;
        follow(canvas.toDiagram(e.getPoint()));
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (type == null) {
            return;
        }
        e.consume();
        try {
            if (!dragging) {
                return;
            }
            follow(canvas.toDiagram(e.getPoint()));
            if (target == null) {
                return;
            }
            Verdict result = reconnecting != null ? fileEditor.commands().reconnect(reconnecting, side, target)
                    : fileEditor.commands().connect(type, source, target);
            if (!result.allowed()) {
                feedback.balloon(e.getPoint(), result.reason());
            }
        } finally {
            reset();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && type != null) {
            reset();
            e.consume();
        }
    }

    @Override
    public void paint(Graphics2D g, DiagramCanvas canvas, Rectangle2D visible) {
        if (!dragging) {
            return;
        }
        double zoom = canvas.zoom();
        double radius = JBUI.scale(5) / zoom;
        float width = (float) (JBUIScale.scale(1.5f) / zoom);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(ElementPainter.SELECTION);
        g.setStroke(new BasicStroke(width));
        Direction role = reconnecting != null && side == EndSide.SOURCE ? Direction.OUT : Direction.IN;
        for (ElementRender render : canvas.scene().elements().values()) {
            if (render.type() == null || !render.extent().intersects(visible)) {
                continue;
            }
            for (AnchorView anchor : render.anchors()) {
                Anchor decl = render.type().anchor(anchor.id());
                if (decl != null && decl.accepts(type, role)) {
                    Point2D at = anchor.position();
                    Ellipse2D ring = new Ellipse2D.Double(at.getX() - radius, at.getY() - radius, 2 * radius, 2 * radius);
                    boolean current = target != null && target.elementKey().equals(render.view().key()) && anchor.id().equals(target.anchorId());
                    if (current && verdict != null && verdict.allowed()) {
                        g.fill(ring);
                    } else {
                        g.draw(ring);
                    }
                }
            }
        }
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[] { 4 * width, 3 * width }, 0f));
        g.draw(new Line2D.Double(from, pointer));
    }

    /** Track the pointer: the end it is over and whether that end is allowed, asked again only when the end changes. */
    private void follow(Point2D point) {
        pointer = point;
        End over = endAt(point);
        if (!Objects.equals(over, target)) {
            target = over;
            verdict = over == null ? null
                    : reconnecting != null ? RefusalFeedback.preview(fileEditor).reconnect(reconnecting, side, over)
                            : RefusalFeedback.preview(fileEditor).connect(type, source, over);
        }
        if (verdict != null && !verdict.allowed()) {
            feedback.showRefused(area(target), verdict.reason());
        } else {
            feedback.clear();
        }
        canvas.repaint();
    }

    /** The anchor under the pointer, else the outline of the element under it, or {@code null}. */
    private End endAt(Point2D point) {
        AnchorHit anchor = canvas.anchorAt(point);
        if (anchor != null) {
            return new End(anchor.key(), anchor.anchor().id());
        }
        Object element = canvas.elementAt(point);
        return element == null ? null : new End(element, null);
    }

    /** What a refused end outlines: the anchor, or the element for its outline. */
    private Rectangle2D area(End end) {
        ElementRender render = canvas.scene().elements().get(end.elementKey());
        if (render == null) {
            return null;
        }
        if (end.anchorId() != null) {
            for (AnchorView anchor : render.anchors()) {
                if (anchor.id().equals(end.anchorId())) {
                    double r = JBUI.scale(4) / canvas.zoom();
                    return new Rectangle2D.Double(anchor.position().getX() - r, anchor.position().getY() - r, 2 * r, 2 * r);
                }
            }
        }
        return render.bounds();
    }

    private void reset() {
        boolean repaint = dragging;
        type = null;
        source = null;
        reconnecting = null;
        side = null;
        from = null;
        pressedAt = null;
        dragging = false;
        pointer = null;
        target = null;
        verdict = null;
        if (repaint) {
            feedback.clear();
            canvas.repaint();
        }
    }
}
