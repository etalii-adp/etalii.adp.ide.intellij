package etalii.adp.core.diagram.view;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.intellij.ide.ui.UISettings;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.diagram.ArrowHead;
import etalii.adp.core.diagram.ConnectionType;
import etalii.adp.core.diagram.ConnectionType.LabelDecl;
import etalii.adp.core.diagram.Dash;
import etalii.adp.core.diagram.LabelSlot;
import etalii.adp.core.diagram.LineStyle;
import etalii.adp.core.diagram.SlotPosition;
import etalii.adp.core.diagram.Tone;
import etalii.adp.core.diagram.model.Connection;
import etalii.adp.core.diagram.model.StyleOverride;
import etalii.adp.core.diagram.view.ArrowHeads.Mark;
import etalii.adp.core.diagram.view.Scene.ConnectionRender;
import etalii.adp.core.diagram.view.Scene.PlacedLabel;

/**
 * How a connection looks (FR-012 to FR-014, research R11, R18): a straight or orthogonal polyline
 * or Béziers through its route, solid, dashed or dotted, in its thickness and tone, with a mark at
 * each end and up to three labels. The file may override line, dash, arrows, thickness and colour.
 * An undeclared type is drawn as a thin dashed grey line with nothing else.
 */
public final class ConnectionPainter {

    private static final int LABEL_MAX_WIDTH = 240;
    private static final int END_LABEL_DISTANCE = 16;

    private ConnectionPainter() {
    }

    /**
     * Lay out one connection along {@code route}.
     *
     * @param type its declaration, or {@code null} for a placeholder
     * @param sourceExit the unit direction the line leaves its source in, zero when free
     * @param targetExit the unit direction the line leaves its target in (outwards), zero when free
     */
    public static ConnectionRender render(ConnectionType type, Connection connection, List<Point2D> route, Point2D sourceExit, Point2D targetExit,
            FontRenderContext frc) {
        boolean placeholder = type == null;
        StyleOverride style = placeholder ? null : connection.style();
        LineStyle line = placeholder ? LineStyle.STRAIGHT : style != null && style.line() != null ? style.line() : type.line();
        Dash dash = placeholder ? Dash.DASHED : style != null && style.dash() != null ? style.dash() : type.dash();
        ArrowHead sourceArrow = placeholder ? ArrowHead.NONE : style != null && style.sourceArrow() != null ? style.sourceArrow() : type.sourceArrow();
        ArrowHead targetArrow = placeholder ? ArrowHead.NONE : style != null && style.targetArrow() != null ? style.targetArrow() : type.targetArrow();
        float thickness = placeholder ? 1f : style != null && style.thickness() != null ? style.thickness() : type.thickness();
        var colour = placeholder ? Tone.GREY.border() : style != null && style.border() != null ? style.border() : type.tone().border();

        List<Point2D> controls = line == LineStyle.CURVED && route.size() >= 2 ? controls(route, sourceExit, targetExit) : List.of();
        Shape path = path(route, controls);
        int n = route.size();
        double size = JBUIScale.scale(8f) + 2 * thickness;
        Mark sourceMark = n < 2 ? null : ArrowHeads.mark(sourceArrow, route.get(0), towards(route.get(0), controls.isEmpty() ? route.get(1) : controls.get(0), route.get(1)), size);
        Mark targetMark = n < 2 ? null
                : ArrowHeads.mark(targetArrow, route.get(n - 1), towards(route.get(n - 1), controls.isEmpty() ? route.get(n - 2) : controls.get(controls.size() - 1),
                        route.get(n - 2)), size);

        Map<LabelSlot, String> texts = new EnumMap<>(LabelSlot.class);
        Map<LabelSlot, PlacedLabel> labels = new EnumMap<>(LabelSlot.class);
        Rectangle2D extent = path.getBounds2D();
        if (!placeholder && n >= 2) {
            Font font = JBFont.small();
            for (Map.Entry<LabelSlot, LabelDecl> entry : type.labels().entrySet()) {
                String value = connection.property(entry.getValue().property());
                if (value.isEmpty()) {
                    continue;
                }
                if (connection.style() != null && connection.style().html()) {
                    value = "<html>" + value;
                }
                TextBlock block = TextBlock.layout(value, font, frc, JBUI.scale(LABEL_MAX_WIDTH), Double.MAX_VALUE, false);
                Rectangle2D box = labelBox(path, entry.getKey(), block.width(), block.height());
                texts.put(entry.getKey(), block.text());
                labels.put(entry.getKey(), new PlacedLabel(block, box));
                extent.add(box);
            }
        }
        for (Mark mark : new Mark[] { sourceMark, targetMark }) {
            if (mark != null) {
                extent.add(mark.shape().getBounds2D());
            }
        }
        double grow = thickness + JBUI.scale(4);
        extent.setRect(extent.getX() - grow, extent.getY() - grow, extent.getWidth() + 2 * grow, extent.getHeight() + 2 * grow);
        List<Point> points = route.stream().map(p -> new Point((int) Math.round(p.getX()), (int) Math.round(p.getY()))).toList();
        ConnectionView view = new ConnectionView(connection.key(), connection.type(), points, line, dash, sourceArrow, targetArrow, texts, placeholder,
                colour, thickness);
        return new ConnectionRender(view, route, path, sourceMark, targetMark, labels, extent);
    }

    /** Paint one connection. */
    public static void paint(Graphics2D g, ConnectionRender render, boolean selected) {
        ConnectionView view = render.view();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        float width = JBUIScale.scale(view.thickness());
        if (selected) {
            g.setColor(ColorUtil.withAlpha(ElementPainter.SELECTION, 0.4));
            g.setStroke(new BasicStroke(width + JBUIScale.scale(4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(render.path());
        }
        g.setColor(view.color());
        g.setStroke(stroke(width, view.dash()));
        g.draw(render.path());
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Mark mark : new Mark[] { render.sourceMark(), render.targetMark() }) {
            if (mark == null) {
                continue;
            }
            if (mark.fill() != ArrowHeads.Fill.NONE) {
                g.setColor(mark.fill() == ArrowHeads.Fill.LINE ? view.color() : ElementPainter.CANVAS);
                g.fill(mark.shape());
            }
            g.setColor(view.color());
            g.draw(mark.shape());
        }
        if (!render.labels().isEmpty()) {
            UISettings.setupAntialiasing(g);
            double pad = JBUI.scale(2);
            for (PlacedLabel label : render.labels().values()) {
                Rectangle2D box = label.box();
                g.setColor(ElementPainter.CANVAS);
                g.fill(new Rectangle2D.Double(box.getX() - pad, box.getY(), box.getWidth() + 2 * pad, box.getHeight()));
                // simplified: labels use the theme's text colour, not the file's; pass the style's text colour when a format stores one
                g.setColor(ElementPainter.TEXT);
                label.block().paint(g, box, SlotPosition.CENTER);
            }
        }
    }

    private static Stroke stroke(float width, Dash dash) {
        return switch (dash) {
        case SOLID -> new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
        case DASHED -> new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 4f,
                new float[] { JBUIScale.scale(6f) + 2 * width, JBUIScale.scale(4f) + width }, 0f);
        case DOTTED -> new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 4f, new float[] { 0.1f, JBUIScale.scale(3f) + 2 * width },
                0f);
        };
    }

    /** The point to aim an end's mark from: its neighbouring control point, or the next route point when they coincide. */
    private static Point2D towards(Point2D tip, Point2D control, Point2D next) {
        return control.distance(tip) > 1e-6 ? control : next;
    }

    private static Shape path(List<Point2D> route, List<Point2D> controls) {
        Path2D.Double path = new Path2D.Double();
        if (route.isEmpty()) {
            return path;
        }
        path.moveTo(route.get(0).getX(), route.get(0).getY());
        for (int i = 1; i < route.size(); i++) {
            Point2D p = route.get(i);
            if (controls.isEmpty()) {
                path.lineTo(p.getX(), p.getY());
            } else {
                Point2D c1 = controls.get(2 * (i - 1));
                Point2D c2 = controls.get(2 * (i - 1) + 1);
                path.curveTo(c1.getX(), c1.getY(), c2.getX(), c2.getY(), p.getX(), p.getY());
            }
        }
        return path;
    }

    /**
     * Two control points per segment of a smooth curve through the route: leaving and entering the
     * ends in their exit directions, and through inner points along the line joining their neighbours.
     * An end reaches no further along its exit than half the way to the next point, when that point
     * lies ahead of it.
     */
    private static List<Point2D> controls(List<Point2D> route, Point2D sourceExit, Point2D targetExit) {
        int n = route.size();
        Point2D[] tangents = new Point2D[n];
        for (int i = 0; i < n; i++) {
            Point2D p = route.get(i);
            if (i == 0 || i == n - 1) {
                Point2D other = route.get(i == 0 ? 1 : n - 2);
                Point2D exit = i == 0 ? sourceExit : targetExit;
                double reach = Math.max(p.distance(other) * 0.4, JBUIScale.scale(20f));
                double length = Math.hypot(exit.getX(), exit.getY());
                double ahead = length == 0 ? 0 : ((other.getX() - p.getX()) * exit.getX() + (other.getY() - p.getY()) * exit.getY()) / length;
                if (ahead > 0) {
                    // at most half the way toward the other point along the exit, so the curve never bends back on itself
                    reach = Math.min(reach, ahead / 2);
                }
                double sign = i == 0 ? 1 : -1;
                tangents[i] = exit.getX() == 0 && exit.getY() == 0
                        ? new Point2D.Double(sign * (other.getX() - p.getX()) / 3, sign * (other.getY() - p.getY()) / 3)
                        : new Point2D.Double(sign * exit.getX() * reach, sign * exit.getY() * reach);
            } else {
                Point2D before = route.get(i - 1);
                Point2D after = route.get(i + 1);
                tangents[i] = new Point2D.Double((after.getX() - before.getX()) / 6, (after.getY() - before.getY()) / 6);
            }
        }
        List<Point2D> controls = new ArrayList<>();
        for (int i = 0; i + 1 < n; i++) {
            Point2D p = route.get(i);
            Point2D q = route.get(i + 1);
            controls.add(new Point2D.Double(p.getX() + tangents[i].getX(), p.getY() + tangents[i].getY()));
            controls.add(new Point2D.Double(q.getX() - tangents[i + 1].getX(), q.getY() - tangents[i + 1].getY()));
        }
        return controls;
    }

    /** A label's box: centred on the middle of the line, or beside it near an end. */
    private static Rectangle2D labelBox(Shape path, LabelSlot slot, double width, double height) {
        List<Point2D> points = flatten(path);
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            total += points.get(i).distance(points.get(i - 1));
        }
        double near = Math.min(JBUI.scale(END_LABEL_DISTANCE), total / 4);
        double at = switch (slot) {
        case MIDDLE -> total / 2;
        case SOURCE -> near;
        case TARGET -> total - near;
        };
        double walked = 0;
        Point2D point = points.get(0);
        double dx = 1;
        double dy = 0;
        for (int i = 1; i < points.size(); i++) {
            Point2D a = points.get(i - 1);
            Point2D b = points.get(i);
            double length = a.distance(b);
            if (length > 0 && walked + length >= at) {
                double t = (at - walked) / length;
                point = new Point2D.Double(a.getX() + t * (b.getX() - a.getX()), a.getY() + t * (b.getY() - a.getY()));
                dx = (b.getX() - a.getX()) / length;
                dy = (b.getY() - a.getY()) / length;
                break;
            }
            walked += length;
            point = b;
        }
        double cx = point.getX();
        double cy = point.getY();
        if (slot != LabelSlot.MIDDLE) {
            double nx = dy;
            double ny = -dx;
            if (ny > 0 || ny == 0 && nx < 0) {
                nx = -nx;
                ny = -ny;
            }
            double offset = Math.abs(nx) * width / 2 + Math.abs(ny) * height / 2 + JBUI.scale(3);
            cx += nx * offset;
            cy += ny * offset;
        }
        return new Rectangle2D.Double(cx - width / 2, cy - height / 2, width, height);
    }

    private static List<Point2D> flatten(Shape path) {
        List<Point2D> points = new ArrayList<>();
        double[] coords = new double[6];
        for (PathIterator it = path.getPathIterator(null, 1.0); !it.isDone(); it.next()) {
            if (it.currentSegment(coords) != PathIterator.SEG_CLOSE) {
                points.add(new Point2D.Double(coords[0], coords[1]));
            }
        }
        return points;
    }
}
