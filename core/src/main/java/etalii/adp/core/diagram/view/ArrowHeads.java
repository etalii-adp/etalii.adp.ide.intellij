package etalii.adp.core.diagram.view;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

import etalii.adp.core.diagram.ArrowHead;

/** The mark at one end of a connection (FR-013): a shape at the tip, pointing away from {@code from}. */
public final class ArrowHeads {

    /** How a mark is filled: in the line's colour, in the canvas colour (so the line does not show through), or not at all. */
    public enum Fill {
        LINE, BACKGROUND, NONE
    }

    /** A mark's outline and fill; it is always stroked in the line's colour. */
    public record Mark(Shape shape, Fill fill) {
    }

    private static final double SPREAD = Math.toRadians(25);

    private ArrowHeads() {
    }

    /** The mark for {@code head} at {@code tip}, or {@code null} for {@link ArrowHead#NONE}. {@code size} is its length. */
    public static Mark mark(ArrowHead head, Point2D tip, Point2D from, double size) {
        double angle = Math.atan2(tip.getY() - from.getY(), tip.getX() - from.getX());
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = tip.getX();
        double y = tip.getY();
        return switch (head) {
        case NONE -> null;
        case OPEN -> {
            Path2D.Double path = new Path2D.Double();
            path.moveTo(x - size * Math.cos(angle - SPREAD), y - size * Math.sin(angle - SPREAD));
            path.lineTo(x, y);
            path.lineTo(x - size * Math.cos(angle + SPREAD), y - size * Math.sin(angle + SPREAD));
            yield new Mark(path, Fill.NONE);
        }
        case FILLED -> {
            Path2D.Double path = new Path2D.Double();
            path.moveTo(x, y);
            path.lineTo(x - size * Math.cos(angle - SPREAD), y - size * Math.sin(angle - SPREAD));
            path.lineTo(x - size * Math.cos(angle + SPREAD), y - size * Math.sin(angle + SPREAD));
            path.closePath();
            yield new Mark(path, Fill.LINE);
        }
        case DIAMOND, OPEN_DIAMOND -> {
            double half = size / 3;
            Path2D.Double path = new Path2D.Double();
            path.moveTo(x, y);
            path.lineTo(x - size / 2 * cos + half * sin, y - size / 2 * sin - half * cos);
            path.lineTo(x - size * cos, y - size * sin);
            path.lineTo(x - size / 2 * cos - half * sin, y - size / 2 * sin + half * cos);
            path.closePath();
            yield new Mark(path, head == ArrowHead.DIAMOND ? Fill.LINE : Fill.BACKGROUND);
        }
        case CIRCLE -> {
            double r = size / 3;
            yield new Mark(new Ellipse2D.Double(x - r * cos - r, y - r * sin - r, 2 * r, 2 * r), Fill.BACKGROUND);
        }
        case BAR -> {
            double half = size / 2;
            Path2D.Double path = new Path2D.Double();
            path.moveTo(x + half * sin, y - half * cos);
            path.lineTo(x - half * sin, y + half * cos);
            yield new Mark(path, Fill.NONE);
        }
        };
    }
}
