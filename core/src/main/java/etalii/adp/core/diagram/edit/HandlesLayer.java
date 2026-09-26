package etalii.adp.core.diagram.edit;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import com.intellij.util.ui.JBUI;

import etalii.adp.core.diagram.view.CanvasLayer;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramDesigner;
import etalii.adp.core.diagram.view.ElementPainter;
import etalii.adp.core.diagram.view.Handle;
import etalii.adp.core.diagram.view.Scene.ElementRender;

/**
 * The resize handles of the selected elements: only those the type's {@code Resize} allows
 * ({@link DiagramDesigner#handlesOf}), drawn at a constant screen size whatever the zoom.
 */
public final class HandlesLayer implements CanvasLayer {

    private static final int SIZE = 7;

    /** A handle under the pointer. */
    public record Hit(Object key, Handle handle) {
    }

    private final DiagramDesigner designer;

    public HandlesLayer(DiagramDesigner designer) {
        this.designer = designer;
    }

    /** The handle of a selected element at a diagram point, the last selected first, or {@code null}. */
    public Hit hitAt(Point2D point) {
        DiagramCanvas canvas = designer.canvas();
        double reach = (JBUI.scale(SIZE) / 2.0 + JBUI.scale(2)) / canvas.zoom();
        List<Object> selection = designer.selection();
        for (int i = selection.size() - 1; i >= 0; i--) {
            Object key = selection.get(i);
            ElementRender render = canvas.scene().elements().get(key);
            if (render == null) {
                continue;
            }
            for (Handle handle : designer.handlesOf(key)) {
                Point2D at = handle.at(render.bounds());
                if (Math.abs(at.getX() - point.getX()) <= reach && Math.abs(at.getY() - point.getY()) <= reach) {
                    return new Hit(key, handle);
                }
            }
        }
        return null;
    }

    @Override
    public void paint(Graphics2D g, DiagramCanvas canvas, Rectangle2D visible) {
        double size = JBUI.scale(SIZE) / canvas.zoom();
        g.setStroke(new BasicStroke((float) (JBUI.scale(1) / canvas.zoom())));
        for (Object key : designer.selection()) {
            ElementRender render = canvas.scene().elements().get(key);
            if (render == null || !render.extent().intersects(visible)) {
                continue;
            }
            for (Handle handle : designer.handlesOf(key)) {
                Point2D at = handle.at(render.bounds());
                Rectangle2D square = new Rectangle2D.Double(at.getX() - size / 2, at.getY() - size / 2, size, size);
                g.setColor(ElementPainter.CANVAS);
                g.fill(square);
                g.setColor(ElementPainter.SELECTION);
                g.draw(square);
            }
        }
    }
}
