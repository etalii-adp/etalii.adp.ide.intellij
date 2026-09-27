package etalii.adp.core.diagram.edit;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.diagram.Placement;
import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.ViewOptions;
import etalii.adp.core.diagram.view.CanvasLayer;
import etalii.adp.core.diagram.view.CanvasTool;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramDesigner;
import etalii.adp.core.diagram.view.ElementPainter;
import etalii.adp.core.diagram.view.Scene.ElementRender;
import etalii.adp.core.settings.AdpSettings;
import etalii.adp.core.settings.CanvasOption;

/**
 * Dragging the selected elements (FR-017). Past a 3 pixel threshold the selection follows the
 * pointer, the pressed element's top left snapped to the grid and the others kept at their
 * offsets; outlines show where they go. Released, it is one "Move". In a designer with a layout,
 * elements are dropped instead: before, onto or after the element under the pointer, by the
 * top quarter, the middle and the bottom quarter, as one {@link DiagramCommands#drop}. A refused
 * gesture shows the refusal while dragging and its reason in a balloon when released, and changes
 * nothing; Escape cancels.
 */
public final class MoveTool implements CanvasTool, CanvasLayer {

    private static final int THRESHOLD = 3;

    private final DiagramDesigner designer;
    private final DiagramCanvas canvas;
    private final RefusalFeedback feedback;
    private Point pressedAt;
    private Point2D pressedDiagram;
    private Object primary;
    private final Map<Object, Rectangle2D> start = new LinkedHashMap<>();
    private boolean dragging;
    private Verdict moveVerdict;
    private double dx;
    private double dy;
    private Drop drop;
    private Drop previewedDrop;

    /** Where a drop in a laid-out designer goes, and whether it may. */
    private record Drop(Object target, Placement placement, Rectangle2D box, Verdict verdict) {

        boolean sameSpot(Drop other) {
            return other != null && target.equals(other.target) && placement == other.placement;
        }
    }

    public MoveTool(DiagramDesigner designer, RefusalFeedback feedback) {
        this.designer = designer;
        this.canvas = designer.canvas();
        this.feedback = feedback;
    }

    /** A diagram coordinate on the designer's grid when snapping is effectively on (spec 004); unchanged otherwise. */
    public static double snap(DiagramDesigner designer, double value) {
        ViewOptions view = designer.definition().view();
        int grid = view.grid();
        return grid <= 0 || !AdpSettings.getInstance().effective(CanvasOption.SNAP_TO_GRID, view) ? value : Math.round(value / grid) * (double) grid;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        reset();
        if (!SelectionTool.plainLeftPress(e) || !designer.isEditable()) {
            return;
        }
        Point2D point = canvas.toDiagram(e.getPoint());
        Object key = SelectionTool.elementAt(designer, point);
        List<Object> selection = designer.selection();
        if (key == null || !selection.contains(key)) {
            return;
        }
        Map<Object, ElementRender> renders = canvas.scene().elements();
        for (Object selected : selection) {
            ElementRender render = renders.get(selected);
            if (render != null) {
                start.put(selected, (Rectangle2D) render.bounds().clone());
            }
        }
        primary = key;
        pressedAt = e.getPoint();
        pressedDiagram = point;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (primary == null) {
            return;
        }
        e.consume();
        if (!dragging && pressedAt.distance(e.getPoint()) < JBUIScale.scale(THRESHOLD)) {
            return;
        }
        if (!dragging) {
            dragging = true;
            if (!laidOut()) {
                // whether the elements may move does not depend on where they go: asked once
                moveVerdict = RefusalFeedback.preview(designer).move(start);
            }
        }
        follow(e.getPoint());
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (primary == null) {
            return;
        }
        try {
            if (dragging) {
                e.consume();
                follow(e.getPoint());
                commit(e.getPoint());
            }
        } finally {
            reset();
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && primary != null) {
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
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(ElementPainter.SELECTION);
        if (laidOut()) {
            if (drop != null && drop.verdict().allowed()) {
                paintDrop(g, drop, zoom);
            }
            return;
        }
        float width = (float) (JBUI.scale(1) / zoom);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 4 * width, 3 * width }, 0f));
        AffineTransform shift = AffineTransform.getTranslateInstance(dx, dy);
        Map<Object, ElementRender> renders = canvas.scene().elements();
        for (Object key : start.keySet()) {
            ElementRender render = renders.get(key);
            if (render != null) {
                g.draw(shift.createTransformedShape(render.outline()));
            }
        }
    }

    private boolean laidOut() {
        return designer.definition().layout() != null;
    }

    private void follow(Point point) {
        Point2D at = canvas.toDiagram(point);
        Rectangle2D box = start.get(primary);
        dx = snap(designer, box.getX() + at.getX() - pressedDiagram.getX()) - box.getX();
        dy = snap(designer, box.getY() + at.getY() - pressedDiagram.getY()) - box.getY();
        if (laidOut()) {
            drop = dropAt(at);
            if (drop != null && !drop.verdict().allowed()) {
                feedback.showRefused(drop.box(), drop.verdict().reason());
            } else {
                feedback.clear();
            }
        } else if (!moveVerdict.allowed()) {
            feedback.showRefused(moved(), moveVerdict.reason());
        } else {
            feedback.clear();
        }
        canvas.repaint();
    }

    private void commit(Point point) {
        Verdict verdict;
        if (laidOut()) {
            if (drop == null) {
                return;
            }
            verdict = designer.commands().drop(new ArrayList<>(start.keySet()), drop.target(), drop.placement());
        } else {
            if (dx == 0 && dy == 0) {
                return;
            }
            Map<Object, Rectangle2D> bounds = new LinkedHashMap<>();
            start.forEach((key, box) -> bounds.put(key, new Rectangle2D.Double(box.getX() + dx, box.getY() + dy, box.getWidth(), box.getHeight())));
            verdict = designer.commands().move(bounds);
        }
        if (!verdict.allowed()) {
            feedback.balloon(point, verdict.reason());
        }
    }

    /** The drop at a diagram point in a laid-out designer, or {@code null} over nothing to drop onto. */
    private Drop dropAt(Point2D at) {
        Object target = SelectionTool.elementAt(designer, at);
        if (target == null || start.containsKey(target)) {
            return null;
        }
        Rectangle2D box = canvas.scene().elements().get(target).bounds();
        double band = Math.max(1, box.getHeight() / 4);
        Placement placement = at.getY() < box.getY() + band ? Placement.BEFORE
                : at.getY() >= box.getMaxY() - band ? Placement.AFTER : Placement.INTO;
        Drop spot = new Drop(target, placement, box, null);
        if (spot.sameSpot(previewedDrop)) {
            return previewedDrop;
        }
        previewedDrop = new Drop(target, placement, box, RefusalFeedback.preview(designer).drop(new ArrayList<>(start.keySet()), target, placement));
        return previewedDrop;
    }

    private Rectangle2D moved() {
        Rectangle2D area = null;
        for (Rectangle2D box : start.values()) {
            Rectangle2D shifted = new Rectangle2D.Double(box.getX() + dx, box.getY() + dy, box.getWidth(), box.getHeight());
            if (area == null) {
                area = shifted;
            } else {
                area.add(shifted);
            }
        }
        return area;
    }

    private static void paintDrop(Graphics2D g, Drop drop, double zoom) {
        double margin = JBUI.scale(3) / zoom;
        Rectangle2D box = drop.box();
        g.setStroke(new BasicStroke((float) (JBUI.scale(2) / zoom)));
        switch (drop.placement()) {
        case BEFORE -> g.draw(new Line2D.Double(box.getX() - margin, box.getY() - margin, box.getMaxX() + margin, box.getY() - margin));
        case AFTER -> g.draw(new Line2D.Double(box.getX() - margin, box.getMaxY() + margin, box.getMaxX() + margin, box.getMaxY() + margin));
        case INTO -> g.draw(new RoundRectangle2D.Double(box.getX() - margin, box.getY() - margin, box.getWidth() + 2 * margin,
                box.getHeight() + 2 * margin, 2 * margin, 2 * margin));
        }
    }

    private void reset() {
        boolean repaint = dragging;
        pressedAt = null;
        pressedDiagram = null;
        primary = null;
        start.clear();
        dragging = false;
        moveVerdict = null;
        dx = 0;
        dy = 0;
        drop = null;
        previewedDrop = null;
        if (repaint) {
            feedback.clear();
            canvas.repaint();
        }
    }
}
