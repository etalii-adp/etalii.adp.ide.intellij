package etalii.adp.core.diagram.edit;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.SwingUtilities;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.diagram.view.CanvasLayer;
import etalii.adp.core.diagram.view.CanvasTool;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramFileEditor;
import etalii.adp.core.diagram.view.ElementPainter;
import etalii.adp.core.diagram.view.Scene.ElementRender;

/**
 * Selecting on the canvas (FR-017): a click selects an element or connection, Ctrl-click toggles
 * it, Shift-click adds it, and a drag from empty canvas selects what lies wholly inside the
 * rectangle, added to the selection with Ctrl or Shift. Elements whose type is not selectable are
 * passed through, as if the canvas were empty there. A press on a selected item keeps the
 * selection, so the move tool drags all of it; released without a drag, it selects only that item.
 */
public final class SelectionTool implements CanvasTool, CanvasLayer {

    private static final int THRESHOLD = 3;

    private final DiagramFileEditor fileEditor;
    private final DiagramCanvas canvas;
    private Point pressedAt;
    private Object pendingCollapse;
    private Point2D marqueeStart;
    private Point2D marqueeEnd;
    private List<Object> base = List.of();

    public SelectionTool(DiagramFileEditor fileEditor) {
        this.fileEditor = fileEditor;
        this.canvas = fileEditor.canvas();
    }

    /** The topmost selectable element at a diagram point, else the topmost connection there, or {@code null}. */
    public static Object itemAt(DiagramFileEditor fileEditor, Point2D point) {
        Object element = elementAt(fileEditor, point);
        return element != null ? element : fileEditor.canvas().connectionAt(point);
    }

    /** The topmost selectable element at a diagram point, or {@code null}; placeholders are selectable. */
    public static Object elementAt(DiagramFileEditor fileEditor, Point2D point) {
        List<ElementRender> renders = new ArrayList<>(fileEditor.canvas().scene().elements().values());
        for (int i = renders.size() - 1; i >= 0; i--) {
            ElementRender render = renders.get(i);
            Rectangle2D box = render.bounds();
            if ((render.type() == null || render.type().selectable()) && box.contains(point)
                    && (!render.view().outline().drawn() || render.outline().contains(point) || box.getWidth() < 8 || box.getHeight() < 8)) {
                return render.view().key();
            }
        }
        return null;
    }

    /** True for a left-button press without Ctrl, Shift, Alt or Meta: one that may start a drag gesture. */
    static boolean plainLeftPress(MouseEvent e) {
        return SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger()
                && (e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK)) == 0;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        reset();
        if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger() || fileEditor.diagram() == null) {
            return;
        }
        Point2D point = canvas.toDiagram(e.getPoint());
        Object item = itemAt(fileEditor, point);
        boolean toggle = (e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
        boolean extend = (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0;
        pressedAt = e.getPoint();
        List<Object> selection = new ArrayList<>(fileEditor.selection());
        if (item == null) {
            base = toggle || extend ? selection : List.of();
            if (!toggle && !extend && !selection.isEmpty()) {
                fileEditor.select(List.of());
            }
            marqueeStart = point;
            e.consume();
        } else if (toggle) {
            if (!selection.remove(item)) {
                selection.add(item);
            }
            fileEditor.select(selection);
            e.consume();
        } else if (extend) {
            if (!selection.contains(item)) {
                selection.add(item);
                fileEditor.select(selection);
            }
            e.consume();
        } else if (!selection.contains(item)) {
            fileEditor.select(List.of(item));
        } else if (selection.size() > 1) {
            pendingCollapse = item;
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (marqueeStart != null) {
            marqueeEnd = canvas.toDiagram(e.getPoint());
            canvas.repaint();
            e.consume();
        } else if (pendingCollapse != null && pressedAt.distance(e.getPoint()) >= JBUIScale.scale(THRESHOLD)) {
            pendingCollapse = null;
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (marqueeStart != null) {
            if (marqueeEnd != null) {
                Rectangle2D area = area(marqueeStart, marqueeEnd);
                Set<Object> selected = new LinkedHashSet<>(base);
                Map<Object, ElementRender> renders = canvas.scene().elements();
                for (Object key : canvas.elementsIn(area)) {
                    ElementRender render = renders.get(key);
                    if (render.type() == null || render.type().selectable()) {
                        selected.add(key);
                    }
                }
                selected.addAll(canvas.connectionsIn(area));
                fileEditor.select(List.copyOf(selected));
            }
            e.consume();
        } else if (pendingCollapse != null) {
            fileEditor.select(List.of(pendingCollapse));
        }
        reset();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && marqueeStart != null) {
            reset();
            e.consume();
        }
    }

    @Override
    public void paint(Graphics2D g, DiagramCanvas canvas, Rectangle2D visible) {
        if (marqueeStart == null || marqueeEnd == null) {
            return;
        }
        Rectangle2D area = area(marqueeStart, marqueeEnd);
        Color colour = ElementPainter.SELECTION;
        g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 40));
        g.fill(area);
        g.setColor(colour);
        g.setStroke(new BasicStroke((float) (JBUI.scale(1) / canvas.zoom())));
        g.draw(area);
    }

    private void reset() {
        boolean repaint = marqueeEnd != null;
        pressedAt = null;
        pendingCollapse = null;
        marqueeStart = null;
        marqueeEnd = null;
        base = List.of();
        if (repaint) {
            canvas.repaint();
        }
    }

    private static Rectangle2D area(Point2D a, Point2D b) {
        return new Rectangle2D.Double(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.abs(a.getX() - b.getX()),
                Math.abs(a.getY() - b.getY()));
    }
}
