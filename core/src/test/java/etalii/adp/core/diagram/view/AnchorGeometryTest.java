package etalii.adp.core.diagram.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import etalii.adp.core.diagram.Anchor;
import etalii.adp.core.diagram.Outline;
import etalii.adp.core.diagram.sample.SampleDefinition;

/** FR-005, FR-009: fixed anchors sit on the bounds; floating ends meet every outline on its perimeter. */
class AnchorGeometryTest {

    private static final Rectangle2D BOUNDS = new Rectangle2D.Double(100, 50, 120, 80);
    private static final List<Outline> PREDEFINED = List.of(Outline.RECTANGLE, Outline.ROUNDED_RECTANGLE, Outline.ELLIPSE, Outline.DIAMOND,
            Outline.HEXAGON, Outline.PARALLELOGRAM, Outline.CYLINDER, Outline.DOCUMENT, Outline.NOTE, Outline.NONE);

    private static Anchor anchor(double fx, double fy) {
        return new Anchor("a", fx, fy, false, true, Map.of());
    }

    @Test
    void fixedAnchorsAreRelativePointsOnTheBounds() {
        assertPoint(100, 90, AnchorGeometry.fixed(anchor(0, 0.5), BOUNDS));
        assertPoint(220, 90, AnchorGeometry.fixed(anchor(1, 0.5), BOUNDS));
        assertPoint(160, 50, AnchorGeometry.fixed(anchor(0.5, 0), BOUNDS));
        assertPoint(160, 130, AnchorGeometry.fixed(anchor(0.5, 1), BOUNDS));
        assertPoint(130, 70, AnchorGeometry.fixed(anchor(0.25, 0.25), BOUNDS));
    }

    @Test
    void theSampleTypesAnchorsAreWhereTheyAreDeclared() {
        var task = SampleDefinition.DEFINITION.elementType("task");
        assertPoint(100, 90, AnchorGeometry.attach(task, BOUNDS, "in", new Point2D.Double(0, 0)));
        assertPoint(220, 90, AnchorGeometry.attach(task, BOUNDS, "out", new Point2D.Double(0, 0)));
    }

    @Test
    void theEdgeAnchorsLeaveOutwards() {
        assertPoint(-1, 0, AnchorGeometry.exit(anchor(0, 0.5)));
        assertPoint(1, 0, AnchorGeometry.exit(anchor(1, 0.5)));
        assertPoint(0, -1, AnchorGeometry.exit(anchor(0.5, 0)));
        assertPoint(0, 1, AnchorGeometry.exit(anchor(0.5, 1)));
        assertPoint(0, 0, AnchorGeometry.exit(anchor(0.5, 0.5)));
    }

    @Test
    void knownPerimeterPoints() {
        Point2D centre = new Point2D.Double(BOUNDS.getCenterX(), BOUNDS.getCenterY());
        assertPoint(220, 90, AnchorGeometry.perimeter(Outline.RECTANGLE.shape(BOUNDS), centre, new Point2D.Double(500, 90)));
        assertPoint(160, 50, AnchorGeometry.perimeter(Outline.ELLIPSE.shape(BOUNDS), centre, new Point2D.Double(160, -300)));
        Rectangle2D square = new Rectangle2D.Double(0, 0, 100, 100);
        assertPoint(75, 75, AnchorGeometry.perimeter(Outline.DIAMOND.shape(square), new Point2D.Double(50, 50), new Point2D.Double(200, 200)));
    }

    @Test
    void everyPredefinedOutlineIsMetOnItsPerimeter() {
        for (Outline outline : PREDEFINED) {
            assertOnPerimeter(outline.toString(), outline.shape(BOUNDS));
        }
    }

    @Test
    void aCustomOutlineIsMetOnItsPerimeter() {
        Outline triangle = Outline.custom(b -> {
            Path2D.Double path = new Path2D.Double();
            path.moveTo(b.getCenterX(), b.getY());
            path.lineTo(b.getMaxX(), b.getMaxY());
            path.lineTo(b.getX(), b.getMaxY());
            path.closePath();
            return path;
        });
        Shape shape = triangle.shape(BOUNDS);
        assertOnPerimeter("triangle", shape);
        Point2D centre = new Point2D.Double(BOUNDS.getCenterX(), BOUNDS.getCenterY());
        assertPoint(160, 50, AnchorGeometry.perimeter(shape, centre, new Point2D.Double(160, -100)));
    }

    @Test
    void aFloatingEndMeetsTheOutlineTowardsTheOtherEnd() {
        var decision = SampleDefinition.DEFINITION.elementType("decision");
        Rectangle2D box = new Rectangle2D.Double(0, 0, 100, 60);
        assertPoint(100, 30, AnchorGeometry.attach(decision, box, null, new Point2D.Double(400, 30)));
        assertPoint(50, 0, AnchorGeometry.attach(null, box, null, new Point2D.Double(50, -400)));
    }

    /** From the centre in twelve directions: just inside the point is inside the shape, just outside is not. */
    private static void assertOnPerimeter(String name, Shape shape) {
        Point2D centre = new Point2D.Double(BOUNDS.getCenterX(), BOUNDS.getCenterY());
        for (int degrees = 0; degrees < 360; degrees += 30) {
            double dx = Math.cos(Math.toRadians(degrees));
            double dy = Math.sin(Math.toRadians(degrees));
            Point2D p = AnchorGeometry.perimeter(shape, centre, new Point2D.Double(centre.getX() + dx * 1000, centre.getY() + dy * 1000));
            String where = name + " at " + degrees + "°: " + p;
            assertTrue(shape.contains(p.getX() - dx * 1.5, p.getY() - dy * 1.5), where + " is not on the inside edge");
            assertTrue(!shape.contains(p.getX() + dx * 1.5, p.getY() + dy * 1.5), where + " is not on the outside edge");
        }
    }

    private static void assertPoint(double x, double y, Point2D point) {
        assertEquals(x, point.getX(), 0.5, point.toString());
        assertEquals(y, point.getY(), 0.5, point.toString());
    }
}
