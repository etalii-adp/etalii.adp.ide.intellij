package etalii.adp.core.diagram.navigation;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;

import javax.swing.JViewport;

import etalii.adp.core.diagram.view.CanvasTool;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramDesigner;

/**
 * Zooming with the wheel (FR-025, research R16): Ctrl+wheel (Cmd+wheel on macOS) steps through
 * {@code ViewState}'s zoom levels with the designer's own Zoom In and Zoom Out, and scrolls so the
 * diagram point under the pointer stays under it. With the designer's zoom off it swallows the
 * gesture, so it neither zooms nor scrolls. The plain wheel is left to the scroll pane.
 */
public final class WheelZoomTool implements CanvasTool {

    private final DiagramDesigner designer;
    private final DiagramCanvas canvas;

    public WheelZoomTool(DiagramDesigner designer) {
        this.designer = designer;
        this.canvas = designer.canvas();
    }

    // simplified: one zoom level per wheel event whatever its size, so a trackpad's many small events zoom fast;
    // if that binds, add up the precise rotation and step once a whole notch has gathered

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if ((e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) == 0) {
            return;
        }
        e.consume();
        double rotation = e.getPreciseWheelRotation();
        if (!designer.definition().view().zoom() || rotation == 0) {
            return;
        }
        Point pointer = e.getPoint();
        JViewport viewport = PanTool.viewportOf(canvas);
        Point before = viewport == null ? new Point() : viewport.getViewPosition();
        Point2D under = canvas.toDiagram(pointer);
        double zoom = designer.viewState().zoom();
        if (rotation < 0) {
            designer.zoomIn();
        } else {
            designer.zoomOut();
        }
        if (viewport == null || designer.viewState().zoom() == zoom) {
            return;
        }
        // the view takes its new size now rather than at the next layout, so the position can be set against it
        Dimension preferred = canvas.getPreferredSize();
        Dimension extent = viewport.getExtentSize();
        viewport.setViewSize(new Dimension(Math.max(preferred.width, extent.width), Math.max(preferred.height, extent.height)));
        Point at = canvas.toCanvas(under);
        PanTool.scrollTo(viewport, new Point(at.x - (pointer.x - before.x), at.y - (pointer.y - before.y)));
    }
}
