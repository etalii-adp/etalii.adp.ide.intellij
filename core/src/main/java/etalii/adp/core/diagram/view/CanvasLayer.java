package etalii.adp.core.diagram.view;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import etalii.adp.core.diagram.Space;

/**
 * Something a feature paints on the {@link DiagramCanvas}: sectors, handles, drag feedback.
 * Diagram-space layers paint under the zoom, in {@link #order()}: below the connections and
 * elements when negative, above them otherwise. View-space layers paint after them, in viewport
 * pixels, so they stay put while the diagram scrolls and zooms.
 */
public interface CanvasLayer {

    /** Under the connections and elements, such as sectors. */
    int BELOW_CONTENT = -100;

    /** Over the connections and elements, such as handles and feedback. */
    int ABOVE_CONTENT = 100;

    default Space space() {
        return Space.DIAGRAM;
    }

    default int order() {
        return ABOVE_CONTENT;
    }

    /**
     * @param g in unzoomed diagram coordinates for a diagram-space layer, in viewport pixels for a view-space one
     * @param visible the part to paint, in the same coordinates
     */
    void paint(Graphics2D g, DiagramCanvas canvas, Rectangle2D visible);
}
