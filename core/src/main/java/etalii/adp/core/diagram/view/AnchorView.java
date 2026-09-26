package etalii.adp.core.diagram.view;

import java.awt.geom.Point2D;

/**
 * One anchor of a drawn element, for the test kit and the connect gesture.
 *
 * @param position in unzoomed diagram coordinates; the element's centre for a perimeter anchor
 * @param visible whether a marker is drawn; invisible anchors still attach connections
 */
public record AnchorView(String id, Point2D position, boolean visible) {

    public AnchorView {
        position = new Point2D.Double(position.getX(), position.getY());
    }

    @Override
    public Point2D position() {
        return new Point2D.Double(position.getX(), position.getY());
    }
}
