package etalii.adp.core.diagram.view;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import etalii.adp.core.diagram.Resize;

/** A resize handle on a selected element's bounds, by compass point. */
public enum Handle {
    N(0.5, 0), NE(1, 0), E(1, 0.5), SE(1, 1), S(0.5, 1), SW(0, 1), W(0, 0.5), NW(0, 0);

    private final double fx;
    private final double fy;

    Handle(double fx, double fy) {
        this.fx = fx;
        this.fy = fy;
    }

    /** The handles an element type's {@link Resize} allows, clockwise from the top. */
    public static List<Handle> allowed(Resize resize) {
        return switch (resize) {
        case NONE -> List.of();
        case HORIZONTAL -> List.of(E, W);
        case VERTICAL -> List.of(N, S);
        case BOTH -> List.of(values());
        };
    }

    /** Where the handle sits on {@code bounds}. */
    public Point2D at(Rectangle2D bounds) {
        return new Point2D.Double(bounds.getX() + fx * bounds.getWidth(), bounds.getY() + fy * bounds.getHeight());
    }

    /** {@code bounds} with this handle dragged by {@code dx}, {@code dy}; the opposite side stays. */
    public Rectangle2D drag(Rectangle2D bounds, double dx, double dy) {
        double x0 = bounds.getMinX() + (fx == 0 ? dx : 0);
        double x1 = bounds.getMaxX() + (fx == 1 ? dx : 0);
        double y0 = bounds.getMinY() + (fy == 0 ? dy : 0);
        double y1 = bounds.getMaxY() + (fy == 1 ? dy : 0);
        return new Rectangle2D.Double(Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0));
    }
}
