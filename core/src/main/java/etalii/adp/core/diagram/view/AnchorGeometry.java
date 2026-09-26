package etalii.adp.core.diagram.view;

import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import etalii.adp.core.diagram.Anchor;
import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.Outline;

/**
 * Where connections attach (FR-005, FR-009, research R11): a fixed anchor is a relative point on
 * the bounds; a floating end is where the line towards the other end leaves the outline.
 */
public final class AnchorGeometry {

    private static final double FLATNESS = 0.25;

    private AnchorGeometry() {
    }

    /** A fixed anchor's point on {@code bounds}. */
    public static Point2D fixed(Anchor anchor, Rectangle2D bounds) {
        return new Point2D.Double(bounds.getX() + anchor.fx() * bounds.getWidth(), bounds.getY() + anchor.fy() * bounds.getHeight());
    }

    /**
     * Where a connection end attaches to an element of {@code type} (or a placeholder, when
     * {@code null}) at {@code bounds}: the anchor's point, or for a perimeter anchor or no anchor,
     * where the line from the centre towards {@code toward} meets the outline.
     */
    public static Point2D attach(ElementType type, Rectangle2D bounds, String anchorId, Point2D toward) {
        Anchor anchor = type == null || anchorId == null ? null : type.anchor(anchorId);
        if (anchor != null && !anchor.perimeter()) {
            return fixed(anchor, bounds);
        }
        Outline outline = type == null ? Outline.RECTANGLE : type.outline();
        return perimeter(outline.shape(bounds), new Point2D.Double(bounds.getCenterX(), bounds.getCenterY()), toward);
    }

    /** The unit direction a line leaves a fixed anchor on an edge of the bounds; zero inside or for a perimeter anchor. */
    public static Point2D exit(Anchor anchor) {
        if (anchor == null || anchor.perimeter()) {
            return new Point2D.Double();
        }
        if (anchor.fx() == 0) {
            return new Point2D.Double(-1, 0);
        }
        if (anchor.fx() == 1) {
            return new Point2D.Double(1, 0);
        }
        if (anchor.fy() == 0) {
            return new Point2D.Double(0, -1);
        }
        if (anchor.fy() == 1) {
            return new Point2D.Double(0, 1);
        }
        return new Point2D.Double();
    }

    /**
     * Where the ray from {@code from} through {@code toward} leaves {@code outline}: the farthest
     * crossing of the flattened outline, or {@code from} when there is none.
     */
    public static Point2D perimeter(Shape outline, Point2D from, Point2D toward) {
        double dx = toward.getX() - from.getX();
        double dy = toward.getY() - from.getY();
        double length = Math.hypot(dx, dy);
        if (length < 1e-9) {
            return new Point2D.Double(from.getX(), from.getY());
        }
        dx /= length;
        dy /= length;
        double best = -1;
        double[] coords = new double[6];
        double startX = 0;
        double startY = 0;
        double lastX = 0;
        double lastY = 0;
        for (PathIterator it = outline.getPathIterator(null, FLATNESS); !it.isDone(); it.next()) {
            int kind = it.currentSegment(coords);
            if (kind == PathIterator.SEG_MOVETO) {
                startX = lastX = coords[0];
                startY = lastY = coords[1];
                continue;
            }
            double x = kind == PathIterator.SEG_CLOSE ? startX : coords[0];
            double y = kind == PathIterator.SEG_CLOSE ? startY : coords[1];
            best = Math.max(best, cross(from.getX(), from.getY(), dx, dy, lastX, lastY, x, y));
            lastX = x;
            lastY = y;
        }
        return best < 0 ? new Point2D.Double(from.getX(), from.getY()) : new Point2D.Double(from.getX() + dx * best, from.getY() + dy * best);
    }

    /** The distance along the ray to where it crosses the segment, or -1. */
    private static double cross(double ox, double oy, double dx, double dy, double ax, double ay, double bx, double by) {
        double ex = bx - ax;
        double ey = by - ay;
        double denominator = dx * ey - dy * ex;
        if (Math.abs(denominator) < 1e-12) {
            return -1;
        }
        double t = ((ax - ox) * ey - (ay - oy) * ex) / denominator;
        double u = ((ax - ox) * dy - (ay - oy) * dx) / denominator;
        return t >= 0 && u >= -1e-9 && u <= 1 + 1e-9 ? t : -1;
    }
}
