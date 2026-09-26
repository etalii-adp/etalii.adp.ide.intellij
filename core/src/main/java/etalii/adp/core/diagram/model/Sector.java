package etalii.adp.core.diagram.model;

import java.awt.geom.Rectangle2D;

/**
 * One swimlane or other region, as read from the file.
 *
 * @param declId the id of its {@code SectorDecl}
 * @param bounds in diagram coordinates, or relative to the viewport for a view-space sector
 */
public record Sector(Object key, String declId, String label, Rectangle2D bounds) {

    public Sector {
        bounds = new Rectangle2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
    }

    @Override
    public Rectangle2D bounds() {
        return (Rectangle2D) bounds.clone();
    }
}