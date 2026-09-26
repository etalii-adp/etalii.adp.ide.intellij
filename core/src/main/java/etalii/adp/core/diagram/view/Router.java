package etalii.adp.core.diagram.view;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/**
 * Orthogonal routes around elements (FR-015, research R12). It tries the direct route, the two
 * one-bend routes and the two Z routes, and takes the first that crosses no element. Only when all
 * are blocked does it search a sparse grid made from the edges of the elements near the pair. When
 * that fails too, the simplest route is drawn anyway. Routes are cached per connection and dropped
 * only when an element near them moves or an end moves.
 */
public final class Router {

    private static final int CELL = 256;
    private static final int MAX_GROWTH = 8;

    private record Cached(Point2D from, Point2D to, List<Point2D> route, Rectangle2D area) {
    }

    private final double margin;
    private final Map<Object, Cached> cache = new HashMap<>();
    private final Map<Long, List<Rectangle2D>> index = new HashMap<>();
    private int computed;

    /** @param margin how far routes keep from elements they pass */
    public Router(double margin) {
        this.margin = margin;
    }

    /** Replace the elements routes go around. The cache is kept: tell it what moved with {@link #moved}. */
    public void obstacles(Map<?, Rectangle2D> bounds) {
        index.clear();
        for (Rectangle2D box : bounds.values()) {
            if (box.getWidth() <= 2 || box.getHeight() <= 2) {
                continue;
            }
            // one pixel inside, so a route along or from an element's edge does not cross it
            // simplified: a route may run along an element's edge; if that reads badly, check candidates against
            // bounds grown by the margin, leaving out the connection's own two elements
            Rectangle2D inner = new Rectangle2D.Double(box.getX() + 1, box.getY() + 1, box.getWidth() - 2, box.getHeight() - 2);
            for (long cell : cells(inner)) {
                index.computeIfAbsent(cell, c -> new ArrayList<>()).add(inner);
            }
        }
    }

    /** The route of connection {@code key}, from the cache when neither end moved since. */
    public List<Point2D> route(Object key, Point2D from, Point2D to) {
        Cached cached = cache.get(key);
        if (cached != null && cached.from().equals(from) && cached.to().equals(to)) {
            return cached.route();
        }
        List<Point2D> route = compute(from, to);
        Rectangle2D area = new Rectangle2D.Double(from.getX(), from.getY(), 0, 0);
        route.forEach(area::add);
        area.setRect(area.getX() - 2 * margin, area.getY() - 2 * margin, area.getWidth() + 4 * margin, area.getHeight() + 4 * margin);
        cache.put(key, new Cached(new Point2D.Double(from.getX(), from.getY()), new Point2D.Double(to.getX(), to.getY()), route, area));
        return route;
    }

    /** True when the connection's route is cached. */
    public boolean isCached(Object key) {
        return cache.containsKey(key);
    }

    /** An element moved, appeared ({@code before} {@code null}) or went ({@code after} {@code null}): routes near it are routed again. */
    public void moved(Rectangle2D before, Rectangle2D after) {
        cache.values().removeIf(c -> before != null && c.area().intersects(before) || after != null && c.area().intersects(after));
    }

    /** Forget every route but those of {@code keys}. */
    public void retain(Collection<?> keys) {
        Set<?> kept = keys instanceof Set<?> set ? set : new HashSet<>(keys);
        cache.keySet().removeIf(key -> !kept.contains(key));
    }

    public void clear() {
        cache.clear();
    }

    /** How many routes were computed rather than taken from the cache, for tests. */
    public int computed() {
        return computed;
    }

    /** The route from {@code from} to {@code to}, without the cache. */
    public List<Point2D> compute(Point2D from, Point2D to) {
        computed++;
        double px = from.getX();
        double py = from.getY();
        double qx = to.getX();
        double qy = to.getY();
        List<List<Point2D>> candidates = new ArrayList<>();
        if (px == qx || py == qy) {
            candidates.add(List.of(point(px, py), point(qx, qy)));
        } else {
            candidates.add(List.of(point(px, py), point(qx, py), point(qx, qy)));
            candidates.add(List.of(point(px, py), point(px, qy), point(qx, qy)));
            double mx = (px + qx) / 2;
            double my = (py + qy) / 2;
            candidates.add(List.of(point(px, py), point(mx, py), point(mx, qy), point(qx, qy)));
            candidates.add(List.of(point(px, py), point(px, my), point(qx, my), point(qx, qy)));
        }
        for (List<Point2D> candidate : candidates) {
            if (clear(candidate)) {
                return candidate;
            }
        }
        List<Point2D> searched = search(px, py, qx, qy);
        return searched != null ? searched : candidates.get(0);
    }

    private boolean clear(List<Point2D> route) {
        for (int i = 1; i < route.size(); i++) {
            Point2D a = route.get(i - 1);
            Point2D b = route.get(i);
            if (blocked(a.getX(), a.getY(), b.getX(), b.getY())) {
                return false;
            }
        }
        return true;
    }

    /** A* over the grid of the pair's and nearby elements' edges, with a cost for every bend; {@code null} when there is no way. */
    private List<Point2D> search(double px, double py, double qx, double qy) {
        Rectangle2D region = new Rectangle2D.Double(Math.min(px, qx), Math.min(py, qy), Math.abs(qx - px), Math.abs(qy - py));
        grow(region, 2 * margin);
        List<Rectangle2D> near = new ArrayList<>();
        for (int i = 0; i < MAX_GROWTH; i++) {
            near = query(region);
            Rectangle2D grown = (Rectangle2D) region.clone();
            for (Rectangle2D box : near) {
                grown.add(new Rectangle2D.Double(box.getX() - 2 * margin, box.getY() - 2 * margin, box.getWidth() + 4 * margin, box.getHeight() + 4 * margin));
            }
            if (grown.equals(region)) {
                break;
            }
            region = grown;
        }
        TreeSet<Double> xSet = new TreeSet<>(List.of(px, qx, region.getMinX(), region.getMaxX()));
        TreeSet<Double> ySet = new TreeSet<>(List.of(py, qy, region.getMinY(), region.getMaxY()));
        for (Rectangle2D box : near) {
            xSet.add(box.getMinX() - 1 - margin);
            xSet.add(box.getMaxX() + 1 + margin);
            ySet.add(box.getMinY() - 1 - margin);
            ySet.add(box.getMaxY() + 1 + margin);
        }
        double[] xs = xSet.stream().mapToDouble(Double::doubleValue).toArray();
        double[] ys = ySet.stream().mapToDouble(Double::doubleValue).toArray();
        int nx = xs.length;
        int ny = ys.length;
        int start = Arrays.binarySearch(xs, px) * ny + Arrays.binarySearch(ys, py);
        int goal = Arrays.binarySearch(xs, qx) * ny + Arrays.binarySearch(ys, qy);
        double bend = 4 * margin + 1;
        int states = nx * ny * 4;
        double[] cost = new double[states];
        Arrays.fill(cost, Double.MAX_VALUE);
        int[] previous = new int[states];
        byte[] blockedNode = new byte[nx * ny];
        PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        for (int direction = 0; direction < 4; direction++) {
            cost[start * 4 + direction] = 0;
            previous[start * 4 + direction] = -1;
            open.add(new double[] { Math.abs(qx - px) + Math.abs(qy - py), start * 4 + direction });
        }
        int[] dxs = { 1, -1, 0, 0 };
        int[] dys = { 0, 0, 1, -1 };
        int reached = -1;
        while (!open.isEmpty()) {
            double[] top = open.poll();
            int state = (int) top[1];
            int node = state / 4;
            int direction = state % 4;
            double g = cost[state];
            if (top[0] - heuristic(xs, ys, node, ny, qx, qy) > g + 1e-6) {
                continue;
            }
            if (node == goal) {
                reached = state;
                break;
            }
            int i = node / ny;
            int j = node % ny;
            for (int next = 0; next < 4; next++) {
                if ((next ^ 1) == direction) {
                    continue;
                }
                int ni = i + dxs[next];
                int nj = j + dys[next];
                if (ni < 0 || nj < 0 || ni >= nx || nj >= ny) {
                    continue;
                }
                int neighbour = ni * ny + nj;
                if (neighbour != goal && isBlockedNode(blockedNode, neighbour, xs[ni], ys[nj])) {
                    continue;
                }
                if (blocked(xs[i], ys[j], xs[ni], ys[nj])) {
                    continue;
                }
                double step = Math.abs(xs[ni] - xs[i]) + Math.abs(ys[nj] - ys[j]) + (node != start && next != direction ? bend : 0);
                int nextState = neighbour * 4 + next;
                if (g + step < cost[nextState] - 1e-9) {
                    cost[nextState] = g + step;
                    previous[nextState] = state;
                    open.add(new double[] { g + step + heuristic(xs, ys, neighbour, ny, qx, qy), nextState });
                }
            }
        }
        if (reached < 0) {
            return null;
        }
        List<Point2D> reversed = new ArrayList<>();
        for (int state = reached; state >= 0; state = previous[state]) {
            int node = state / 4;
            reversed.add(point(xs[node / ny], ys[node % ny]));
        }
        List<Point2D> route = new ArrayList<>();
        for (int k = reversed.size() - 1; k >= 0; k--) {
            Point2D p = reversed.get(k);
            if (!route.isEmpty() && route.get(route.size() - 1).equals(p)) {
                continue;
            }
            int n = route.size();
            if (n >= 2 && collinear(route.get(n - 2), route.get(n - 1), p)) {
                route.set(n - 1, p);
            } else {
                route.add(p);
            }
        }
        return List.copyOf(route);
    }

    private boolean isBlockedNode(byte[] cache, int node, double x, double y) {
        if (cache[node] == 0) {
            cache[node] = (byte) (inside(x, y) ? 2 : 1);
        }
        return cache[node] == 2;
    }

    private static double heuristic(double[] xs, double[] ys, int node, int ny, double qx, double qy) {
        return Math.abs(xs[node / ny] - qx) + Math.abs(ys[node % ny] - qy);
    }

    private static boolean collinear(Point2D a, Point2D b, Point2D c) {
        return a.getX() == b.getX() && b.getX() == c.getX() || a.getY() == b.getY() && b.getY() == c.getY();
    }

    private boolean inside(double x, double y) {
        List<Rectangle2D> boxes = index.get(key((int) Math.floor(x / CELL), (int) Math.floor(y / CELL)));
        if (boxes != null) {
            for (Rectangle2D box : boxes) {
                if (box.contains(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True when the segment crosses an element. */
    private boolean blocked(double ax, double ay, double bx, double by) {
        int x0 = (int) Math.floor(Math.min(ax, bx) / CELL);
        int x1 = (int) Math.floor(Math.max(ax, bx) / CELL);
        int y0 = (int) Math.floor(Math.min(ay, by) / CELL);
        int y1 = (int) Math.floor(Math.max(ay, by) / CELL);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cy = y0; cy <= y1; cy++) {
                List<Rectangle2D> boxes = index.get(key(cx, cy));
                if (boxes != null) {
                    for (Rectangle2D box : boxes) {
                        if (box.intersectsLine(ax, ay, bx, by)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private List<Rectangle2D> query(Rectangle2D region) {
        Set<Rectangle2D> found = new HashSet<>();
        for (long cell : cells(region)) {
            List<Rectangle2D> boxes = index.get(cell);
            if (boxes != null) {
                for (Rectangle2D box : boxes) {
                    if (box.intersects(region)) {
                        found.add(box);
                    }
                }
            }
        }
        return new ArrayList<>(found);
    }

    private static List<Long> cells(Rectangle2D box) {
        List<Long> cells = new ArrayList<>();
        int x0 = (int) Math.floor(box.getMinX() / CELL);
        int x1 = (int) Math.floor(box.getMaxX() / CELL);
        int y0 = (int) Math.floor(box.getMinY() / CELL);
        int y1 = (int) Math.floor(box.getMaxY() / CELL);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cy = y0; cy <= y1; cy++) {
                cells.add(key(cx, cy));
            }
        }
        return cells;
    }

    private static long key(int cx, int cy) {
        return ((long) cx << 32) ^ (cy & 0xFFFFFFFFL);
    }

    private static void grow(Rectangle2D box, double by) {
        box.setRect(box.getX() - by, box.getY() - by, box.getWidth() + 2 * by, box.getHeight() + 2 * by);
    }

    private static Point2D point(double x, double y) {
        return new Point2D.Double(x, y);
    }
}
