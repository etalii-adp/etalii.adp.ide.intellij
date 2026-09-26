package etalii.adp.core.diagram;

import java.awt.geom.Rectangle2D;

/**
 * One element's new bounds after a move or resize.
 *
 * @param sector the sector it is in now, or {@code null} for none; only meaningful when {@code sectorChanged}
 */
public record BoundsChange(Object key, Rectangle2D bounds, Object sector, boolean sectorChanged) {

    public BoundsChange {
        bounds = new Rectangle2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
    }
}