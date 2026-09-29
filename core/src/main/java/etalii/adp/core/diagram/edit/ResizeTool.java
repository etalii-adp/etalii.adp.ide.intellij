package etalii.adp.core.diagram.edit;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.Resize;
import etalii.adp.core.diagram.Sizing;
import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.model.Element;
import etalii.adp.core.diagram.view.CanvasLayer;
import etalii.adp.core.diagram.view.CanvasTool;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramFileEditor;
import etalii.adp.core.diagram.view.ElementPainter;
import etalii.adp.core.diagram.view.Handle;

/**
 * Dragging a resize handle of a selected element (FR-017): the dragged sides follow the pointer,
 * snapped to the grid, never closer than the type's minimum size; the other sides stay. Released,
 * it is one "Resize" of only the axes the type allows. Escape cancels.
 */
public final class ResizeTool implements CanvasTool, CanvasLayer {

    private static final int THRESHOLD = 3;

    /** The smallest an element is drawn, as the measure has it, for types that declare no minimum. */
    private static final int MIN_WIDTH = 24;
    private static final int MIN_HEIGHT = 16;

    private final DiagramFileEditor fileEditor;
    private final DiagramCanvas canvas;
    private final HandlesLayer handles;
    private final RefusalFeedback feedback;
    private HandlesLayer.Hit hit;
    private Rectangle2D start;
    private Point pressedAt;
    private Point2D pressedDiagram;
    private Rectangle2D next;

    public ResizeTool(DiagramFileEditor fileEditor, HandlesLayer handles, RefusalFeedback feedback) {
        this.fileEditor = fileEditor;
        this.canvas = fileEditor.canvas();
        this.handles = handles;
        this.feedback = feedback;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        reset();
        if (!SelectionTool.plainLeftPress(e) || !fileEditor.isEditable()) {
            return;
        }
        Point2D point = canvas.toDiagram(e.getPoint());
        HandlesLayer.Hit found = handles.hitAt(point);
        if (found == null) {
            return;
        }
        hit = found;
        start = (Rectangle2D) canvas.scene().elements().get(found.key()).bounds().clone();
        pressedAt = e.getPoint();
        pressedDiagram = point;
        e.consume();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        HandlesLayer.Hit over = fileEditor.isEditable() ? handles.hitAt(canvas.toDiagram(e.getPoint())) : null;
        if (over != null) {
            canvas.setCursor(cursor(over.handle()));
        } else if (canvas.isCursorSet() && canvas.getCursor().getType() != Cursor.DEFAULT_CURSOR && canvas.getCursor() != RefusalFeedback.NOT_ALLOWED) {
            canvas.setCursor(null);
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (hit == null) {
            return;
        }
        e.consume();
        if (next == null && pressedAt.distance(e.getPoint()) < JBUIScale.scale(THRESHOLD)) {
            return;
        }
        Point2D at = canvas.toDiagram(e.getPoint());
        next = resized(at.getX() - pressedDiagram.getX(), at.getY() - pressedDiagram.getY());
        canvas.repaint();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (hit == null) {
            return;
        }
        e.consume();
        try {
            if (next != null && !next.equals(start)) {
                Element element = fileEditor.diagram() == null ? null : fileEditor.diagram().element(hit.key());
                if (element != null && element.bounds() != null) {
                    Verdict verdict = fileEditor.commands().resize(hit.key(), stored(element, next));
                    if (!verdict.allowed()) {
                        feedback.balloon(e.getPoint(), verdict.reason());
                    }
                }
            }
        } finally {
            reset();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && hit != null) {
            reset();
            e.consume();
        }
    }

    @Override
    public void paint(Graphics2D g, DiagramCanvas canvas, Rectangle2D visible) {
        if (next == null) {
            return;
        }
        float width = (float) (JBUI.scale(1) / canvas.zoom());
        g.setColor(ElementPainter.SELECTION);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 4 * width, 3 * width }, 0f));
        g.draw(next);
    }

    /** The shown bounds with the handle's sides moved, snapped and kept apart by the minimum size. */
    private Rectangle2D resized(double dx, double dy) {
        Element element = fileEditor.diagram() == null ? null : fileEditor.diagram().element(hit.key());
        ElementType type = element == null ? null : fileEditor.definition().elementType(element.type());
        double minWidth = MIN_WIDTH;
        double minHeight = MIN_HEIGHT;
        if (type != null && type.sizing() instanceof Sizing.FromDiagram from) {
            minWidth = Math.max(minWidth, from.minWidth());
            minHeight = Math.max(minHeight, from.minHeight());
        }
        String name = hit.handle().name();
        double x0 = start.getMinX();
        double x1 = start.getMaxX();
        double y0 = start.getMinY();
        double y1 = start.getMaxY();
        if (name.endsWith("W")) {
            x0 = Math.min(MoveTool.snap(fileEditor, x0 + dx), x1 - minWidth);
        }
        if (name.endsWith("E")) {
            x1 = Math.max(MoveTool.snap(fileEditor, x1 + dx), x0 + minWidth);
        }
        if (name.startsWith("N")) {
            y0 = Math.min(MoveTool.snap(fileEditor, y0 + dy), y1 - minHeight);
        }
        if (name.startsWith("S")) {
            y1 = Math.max(MoveTool.snap(fileEditor, y1 + dy), y0 + minHeight);
        }
        return new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0);
    }

    /** The bounds to write: the new sides on the axes the type resizes, the file's own on the others. */
    private Rectangle2D stored(Element element, Rectangle2D shown) {
        ElementType type = fileEditor.definition().elementType(element.type());
        Resize resize = type == null ? Resize.NONE : type.resize();
        Rectangle2D old = element.bounds();
        return new Rectangle2D.Double(resize.horizontal() ? shown.getX() : old.getX(), resize.vertical() ? shown.getY() : old.getY(),
                resize.horizontal() ? shown.getWidth() : old.getWidth(), resize.vertical() ? shown.getHeight() : old.getHeight());
    }

    private static Cursor cursor(Handle handle) {
        return Cursor.getPredefinedCursor(switch (handle) {
        case N -> Cursor.N_RESIZE_CURSOR;
        case NE -> Cursor.NE_RESIZE_CURSOR;
        case E -> Cursor.E_RESIZE_CURSOR;
        case SE -> Cursor.SE_RESIZE_CURSOR;
        case S -> Cursor.S_RESIZE_CURSOR;
        case SW -> Cursor.SW_RESIZE_CURSOR;
        case W -> Cursor.W_RESIZE_CURSOR;
        case NW -> Cursor.NW_RESIZE_CURSOR;
        });
    }

    private void reset() {
        boolean repaint = next != null;
        hit = null;
        start = null;
        pressedAt = null;
        pressedDiagram = null;
        next = null;
        if (repaint) {
            canvas.repaint();
        }
    }
}
