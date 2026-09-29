package etalii.adp.core.diagram;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Function;

/**
 * The outline of an element type (FR-005, research R10): a predefined shape or a custom one. The
 * outline is also the perimeter that floating anchors and connection ends attach to.
 */
public final class Outline {

    public static final Outline RECTANGLE = new Outline("rectangle", true, b -> new Rectangle2D.Double(b.getX(), b.getY(), b.getWidth(), b.getHeight()));
    public static final Outline ROUNDED_RECTANGLE = new Outline("rounded rectangle", true, b -> {
        double arc = Math.min(16, Math.min(b.getWidth(), b.getHeight()));
        return new RoundRectangle2D.Double(b.getX(), b.getY(), b.getWidth(), b.getHeight(), arc, arc);
    });
    public static final Outline ELLIPSE = new Outline("ellipse", true, b -> new Ellipse2D.Double(b.getX(), b.getY(), b.getWidth(), b.getHeight()));
    public static final Outline DIAMOND = new Outline("diamond", true,
            b -> polygon(b.getCenterX(), b.getY(), b.getMaxX(), b.getCenterY(), b.getCenterX(), b.getMaxY(), b.getX(), b.getCenterY()));
    public static final Outline HEXAGON = new Outline("hexagon", true, b -> {
        double inset = Math.min(b.getWidth() / 4, b.getHeight() / 2);
        return polygon(b.getX() + inset, b.getY(), b.getMaxX() - inset, b.getY(), b.getMaxX(), b.getCenterY(), b.getMaxX() - inset, b.getMaxY(),
                b.getX() + inset, b.getMaxY(), b.getX(), b.getCenterY());
    });
    public static final Outline PARALLELOGRAM = new Outline("parallelogram", true, b -> {
        double skew = Math.min(b.getWidth() / 5, b.getHeight() / 2);
        return polygon(b.getX() + skew, b.getY(), b.getMaxX(), b.getY(), b.getMaxX() - skew, b.getMaxY(), b.getX(), b.getMaxY());
    });
    public static final Outline CYLINDER = new Outline("cylinder", true, b -> {
        double ry = Math.min(b.getHeight() / 6, 10);
        Area area = new Area(new Rectangle2D.Double(b.getX(), b.getY() + ry, b.getWidth(), Math.max(0, b.getHeight() - 2 * ry)));
        area.add(new Area(new Ellipse2D.Double(b.getX(), b.getY(), b.getWidth(), 2 * ry)));
        area.add(new Area(new Ellipse2D.Double(b.getX(), b.getMaxY() - 2 * ry, b.getWidth(), 2 * ry)));
        return area;
    });
    public static final Outline DOCUMENT = new Outline("document", true, b -> {
        double wave = Math.min(b.getHeight() / 8, 8);
        Path2D.Double path = new Path2D.Double();
        path.moveTo(b.getX(), b.getY());
        path.lineTo(b.getMaxX(), b.getY());
        path.lineTo(b.getMaxX(), b.getMaxY() - wave);
        path.curveTo(b.getX() + b.getWidth() * 0.75, b.getMaxY() - 3 * wave, b.getX() + b.getWidth() * 0.25, b.getMaxY() + wave, b.getX(),
                b.getMaxY() - wave);
        path.closePath();
        return path;
    });
    public static final Outline NOTE = new Outline("note", true, b -> {
        double fold = Math.min(12, Math.min(b.getWidth(), b.getHeight()) / 4);
        return polygon(b.getX(), b.getY(), b.getMaxX() - fold, b.getY(), b.getMaxX(), b.getY() + fold, b.getMaxX(), b.getMaxY(), b.getX(),
                b.getMaxY());
    });
    /** Nothing is drawn; the bounds still act as the perimeter. */
    public static final Outline NONE = new Outline("none", false, RECTANGLE.shape);

    private final String name;
    private final boolean drawn;
    private final Function<Rectangle2D, Shape> shape;

    private Outline(String name, boolean drawn, Function<Rectangle2D, Shape> shape) {
        this.name = name;
        this.drawn = drawn;
        this.shape = shape;
    }

    /** An outline of the diagram's own, as a shape for the given bounds. */
    public static Outline custom(Function<Rectangle2D, Shape> shape) {
        return new Outline("custom", true, shape);
    }

    /** The outline for {@code bounds}. */
    public Shape shape(Rectangle2D bounds) {
        return shape.apply(bounds);
    }

    /** False for {@link #NONE}. */
    public boolean drawn() {
        return drawn;
    }

    @Override
    public String toString() {
        return name;
    }

    private static Shape polygon(double... points) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) {
            path.lineTo(points[i], points[i + 1]);
        }
        path.closePath();
        return path;
    }
}