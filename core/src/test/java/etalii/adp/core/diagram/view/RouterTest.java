package etalii.adp.core.diagram.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** FR-015, R12: the simplest clear orthogonal route, a grid search only when needed, and routes cached until something near moves. */
class RouterTest {

    private static final double MARGIN = 10;

    private static Point2D p(double x, double y) {
        return new Point2D.Double(x, y);
    }

    private static Router router(Rectangle2D... obstacles) {
        Map<Object, Rectangle2D> bounds = new LinkedHashMap<>();
        for (int i = 0; i < obstacles.length; i++) {
            bounds.put("o" + i, obstacles[i]);
        }
        Router router = new Router(MARGIN);
        router.obstacles(bounds);
        return router;
    }

    private static Rectangle2D box(double x, double y, double w, double h) {
        return new Rectangle2D.Double(x, y, w, h);
    }

    @Test
    void aClearStraightLineIsTheRoute() {
        Router router = router(box(100, 200, 40, 40));

        assertEquals(List.of(p(0, 50), p(200, 50)), router.compute(p(0, 50), p(200, 50)));
    }

    @Test
    void aOneBendRouteGoesAroundAnObstacle() {
        Router router = router(box(150, -20, 40, 40));

        assertEquals(List.of(p(0, 0), p(0, 100), p(200, 100)), router.compute(p(0, 0), p(200, 100)));
    }

    @Test
    void aZRouteGoesAroundObstaclesAtBothCorners() {
        Rectangle2D[] obstacles = { box(180, -20, 40, 40), box(-20, 80, 40, 40) };
        Router router = router(obstacles);

        List<Point2D> route = router.compute(p(0, 0), p(200, 100));

        assertEquals(List.of(p(0, 0), p(100, 0), p(100, 100), p(200, 100)), route);
        assertClear(route, obstacles);
    }

    @Test
    void aGridSearchGoesAroundSeveralObstacles() {
        Rectangle2D[] obstacles = { box(100, 0, 40, 100), box(200, 20, 40, 60) };
        Router router = router(obstacles);

        List<Point2D> route = router.compute(p(0, 50), p(300, 50));

        assertEquals(p(0, 50), route.get(0));
        assertEquals(p(300, 50), route.get(route.size() - 1));
        assertTrue(route.size() > 2, route.toString());
        assertOrthogonal(route);
        assertClear(route, obstacles);
    }

    @Test
    void whenEveryRouteIsBlockedTheSimplestIsDrawnAnyway() {
        Router router = router(box(280, 30, 40, 40));

        assertEquals(List.of(p(0, 50), p(300, 50)), router.compute(p(0, 50), p(300, 50)));
        assertEquals(List.of(p(0, 0), p(300, 0), p(300, 50)), router.compute(p(0, 0), p(300, 50)));
    }

    @Test
    void routesAreCachedAndOnlyThoseNearAMovedElementAreRecomputed() {
        Router router = router(box(100, 0, 40, 100), box(1000, 0, 40, 100));

        router.route("left", p(0, 50), p(300, 50));
        router.route("right", p(900, 50), p(1200, 50));
        assertEquals(2, router.computed());

        router.route("left", p(0, 50), p(300, 50));
        router.route("right", p(900, 50), p(1200, 50));
        assertEquals(2, router.computed(), "unchanged routes come from the cache");
        assertTrue(router.isCached("left"));

        router.moved(box(1000, 0, 40, 100), box(1000, 150, 40, 100));
        assertTrue(router.isCached("left"), "far from the move");
        assertFalse(router.isCached("right"), "near the move");

        router.route("right", p(900, 50), p(1200, 50));
        assertEquals(3, router.computed());

        router.route("left", p(0, 60), p(300, 60));
        assertEquals(4, router.computed(), "a moved end is routed again");
    }

    private static void assertOrthogonal(List<Point2D> route) {
        for (int i = 1; i < route.size(); i++) {
            Point2D a = route.get(i - 1);
            Point2D b = route.get(i);
            assertTrue(a.getX() == b.getX() || a.getY() == b.getY(), "segment " + a + " to " + b + " is not orthogonal");
        }
    }

    private static void assertClear(List<Point2D> route, Rectangle2D... obstacles) {
        for (int i = 1; i < route.size(); i++) {
            Line2D segment = new Line2D.Double(route.get(i - 1), route.get(i));
            for (Rectangle2D obstacle : obstacles) {
                Rectangle2D inner = new Rectangle2D.Double(obstacle.getX() + 1, obstacle.getY() + 1, obstacle.getWidth() - 2, obstacle.getHeight() - 2);
                assertFalse(segment.intersects(inner), "segment " + segment.getP1() + " to " + segment.getP2() + " crosses " + obstacle);
            }
        }
    }
}
