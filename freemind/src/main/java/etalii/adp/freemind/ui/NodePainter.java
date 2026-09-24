package etalii.adp.freemind.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JComponent;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.NodeView;
import etalii.adp.core.Rgb;
import etalii.adp.freemind.model.FontSpec;
import etalii.adp.freemind.model.MapNode;

/**
 * How one node looks and where its parts are (spec 001 FR-014, FR-018; FR-016): icons as glyphs or
 * badges, the text in the node's font and colours, a link and a note indicator, and a marker when
 * the branch is folded; also the lines between nodes and the arrow links. Everything is in unzoomed
 * view coordinates; the canvas scales the graphics for zoom.
 * <p>
 * Colours are the theme's ({@link JBColor} pairs) unless the file sets them. A file colour is used
 * as written; when it has less than 3:1 contrast with what it is drawn on, the node gets a plate
 * (research R8), so the author's colour is kept and stays legible in either theme.
 */
public final class NodePainter {

    public static final JBColor CANVAS_BACKGROUND = new JBColor(0xFFFFFF, 0x1E1F22);
    public static final JBColor TEXT = new JBColor(0x1E1F22, 0xDFE1E5);
    public static final JBColor CONNECTOR = new JBColor(0x7D818A, 0x868A91);
    public static final JBColor ARROW_LINK = new JBColor(0x2154B8, 0x6C9BF5);
    public static final JBColor SELECTION = new JBColor(0x3574F0, 0x548AF7);
    public static final JBColor ROOT_BORDER = new JBColor(0x6F737A, 0x9DA0A8);
    public static final JBColor MARQUEE = new JBColor(new Color(0x3574F0), new Color(0x548AF7));

    /** The neutral plates a low-contrast file colour is drawn on. */
    static final Color LIGHT_PLATE = new Color(0xF7F8FA);
    static final Color DARK_PLATE = new Color(0x2B2D30);
    static final Color DARK_TEXT = new Color(0x1E1F22);
    static final Color LIGHT_TEXT = new Color(0xF7F8FA);

    /** WCAG 2.1 non-text contrast. */
    static final double MIN_CONTRAST = 3.0;

    private static final int PAD_H = 6;
    private static final int PAD_V = 3;
    private static final int GAP = 4;
    private static final int ARC = 10;
    private static final int FOLD_MARK = 10;

    /** A part of a node that answers a click or shows a tooltip. */
    public enum Indicator {
        LINK, NOTE, FOLDED
    }

    /** Where each part of a node is, in unzoomed view coordinates. */
    record Parts(List<Rectangle> icons, Rectangle text, Rectangle link, Rectangle note, Rectangle folded) {
    }

    private NodePainter() {
    }

    /** The default colours, each of which has at least 3:1 contrast with {@link #CANVAS_BACKGROUND} in either theme. */
    public static List<Color> defaultColours() {
        return List.of(TEXT, CONNECTOR, ARROW_LINK, SELECTION, ROOT_BORDER);
    }

    /** The WCAG 2.1 contrast ratio of two colours, 1 to 21. */
    public static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color colour) {
        return 0.2126 * channel(colour.getRed()) + 0.7152 * channel(colour.getGreen()) + 0.0722 * channel(colour.getBlue());
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** A node's box at the origin, with everything it shows; {@code metrics} measures text. */
    static NodeView measure(MapNode node, boolean shownFolded, JComponent metrics) {
        Font font = font(node.font());
        Color background = node.backgroundColor() == null ? null : colour(node.backgroundColor());
        Color foreground;
        if (node.color() != null) {
            foreground = colour(node.color());
            if (background == null && contrast(foreground, CANVAS_BACKGROUND) < MIN_CONTRAST) {
                background = contrast(foreground, LIGHT_PLATE) >= contrast(foreground, DARK_PLATE) ? LIGHT_PLATE : DARK_PLATE;
            }
        } else if (background != null && contrast(TEXT, background) < MIN_CONTRAST) {
            foreground = contrast(DARK_TEXT, background) >= contrast(LIGHT_TEXT, background) ? DARK_TEXT : LIGHT_TEXT;
        } else {
            foreground = TEXT;
        }
        List<String> icons = node.icons().stream().map(FreeMindIcons::display).toList();
        String text = node.text() == null ? "" : node.text();
        boolean folded = shownFolded && !node.children().isEmpty();
        NodeView view = new NodeView(node.key(), new Rectangle(), text, foreground, background, font, icons, node.link() != null,
                node.note() != null, folded);
        Parts parts = parts(view, metrics);
        Rectangle all = parts.text();
        for (Rectangle icon : parts.icons()) {
            all = all.union(icon);
        }
        for (Rectangle part : new Rectangle[] { parts.link(), parts.note(), parts.folded() }) {
            if (part != null) {
                all = all.union(part);
            }
        }
        Rectangle box = new Rectangle(0, 0, all.x + all.width + JBUI.scale(PAD_H), all.y + all.height + JBUI.scale(PAD_V));
        return MindMapLayout.withBounds(view, box);
    }

    /** The node's font: its own {@code font} element, sizes in points scaled for the IDE, or the IDE's label font. */
    static Font font(FontSpec spec) {
        Font base = JBFont.label().asPlain();
        if (spec == null) {
            return base;
        }
        int style = (spec.bold() ? Font.BOLD : Font.PLAIN) | (spec.italic() ? Font.ITALIC : Font.PLAIN);
        String name = spec.name() != null ? spec.name() : base.getFamily();
        float size = spec.size() != null ? JBUIScale.scale((float) spec.size()) : base.getSize2D();
        return new Font(name, style, 12).deriveFont(size);
    }

    private static Color colour(Rgb rgb) {
        return new Color(rgb.red(), rgb.green(), rgb.blue());
    }

    /** Where the parts of a laid-out node are: padding, icons, text lines, then the indicators, left to right. */
    static Parts parts(NodeView view, JComponent metrics) {
        Rectangle box = view.bounds();
        FontMetrics fm = metrics.getFontMetrics(view.font());
        int padH = JBUI.scale(PAD_H);
        int padV = JBUI.scale(PAD_V);
        int gap = JBUI.scale(GAP);
        String[] lines = view.text().split("\n", -1);
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, fm.stringWidth(line));
        }
        int textHeight = fm.getHeight() * lines.length;
        Icon link = view.hasLink() ? AllIcons.Ide.Link : null;
        Icon note = view.hasNote() ? AllIcons.General.Note : null;
        int foldMark = JBUI.scale(FOLD_MARK);
        int contentHeight = textHeight;
        for (Icon icon : new Icon[] { link, note }) {
            if (icon != null) {
                contentHeight = Math.max(contentHeight, icon.getIconHeight());
            }
        }
        int x = box.x + padH;
        int top = box.y + padV;
        List<Rectangle> icons = new ArrayList<>();
        for (String icon : view.icons()) {
            int width = fm.stringWidth(icon) + (isBadge(icon) ? 2 * JBUI.scale(3) : 0);
            icons.add(new Rectangle(x, top + (contentHeight - fm.getHeight()) / 2, width, fm.getHeight()));
            x += width + gap;
        }
        Rectangle text = new Rectangle(x, top + (contentHeight - textHeight) / 2, Math.max(textWidth, JBUI.scale(8)), textHeight);
        x += text.width;
        Rectangle linkBox = null;
        if (link != null) {
            x += gap;
            linkBox = new Rectangle(x, top + (contentHeight - link.getIconHeight()) / 2, link.getIconWidth(), link.getIconHeight());
            x += linkBox.width;
        }
        Rectangle noteBox = null;
        if (note != null) {
            x += gap;
            noteBox = new Rectangle(x, top + (contentHeight - note.getIconHeight()) / 2, note.getIconWidth(), note.getIconHeight());
            x += noteBox.width;
        }
        Rectangle foldedBox = null;
        if (view.folded()) {
            x += gap;
            foldedBox = new Rectangle(x, top + (contentHeight - foldMark) / 2, foldMark, foldMark);
        }
        return new Parts(icons, text, linkBox, noteBox, foldedBox);
    }

    /**
     * An icon FreeMind has no glyph for is shown by its name, drawn as a badge. Glyphs all use
     * symbol code points, and names are plain text, so the display string tells them apart.
     */
    private static boolean isBadge(String display) {
        return FreeMindIcons.glyph(display) == null && display.codePoints().allMatch(c -> c < 0x2000);
    }

    /** An indicator's box, or {@code null} when the node does not show it. */
    static Rectangle indicator(Parts parts, Indicator which) {
        return switch (which) {
        case LINK -> parts.link();
        case NOTE -> parts.note();
        case FOLDED -> parts.folded();
        };
    }

    /** Paint one node. */
    static void paint(Graphics2D g, NodeView view, boolean root, boolean selected, boolean focused, JComponent component) {
        Rectangle box = view.bounds();
        Parts parts = parts(view, component);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float arc = JBUI.scale(ARC);
        RoundRectangle2D shape = new RoundRectangle2D.Float(box.x + 0.5f, box.y + 0.5f, box.width - 1f, box.height - 1f, arc, arc);
        if (view.background() != null) {
            g.setColor(view.background());
            g.fill(shape);
        }
        if (root) {
            g.setColor(ROOT_BORDER);
            g.setStroke(new BasicStroke(JBUIScale.scale(1.5f)));
            g.draw(shape);
        }

        UISettings.setupAntialiasing(g);
        g.setFont(view.font());
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < view.icons().size(); i++) {
            String icon = view.icons().get(i);
            Rectangle at = parts.icons().get(i);
            if (isBadge(icon)) {
                g.setColor(CONNECTOR);
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(at.x, at.y, at.width - 1, at.height - 1, JBUI.scale(4), JBUI.scale(4));
                g.setColor(view.foreground());
                g.drawString(icon, at.x + JBUI.scale(3), at.y + fm.getAscent());
            } else {
                g.setColor(view.foreground());
                g.drawString(icon, at.x, at.y + fm.getAscent());
            }
        }
        g.setColor(view.foreground());
        String[] lines = view.text().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], parts.text().x, parts.text().y + i * fm.getHeight() + fm.getAscent());
        }
        if (parts.link() != null) {
            AllIcons.Ide.Link.paintIcon(component, g, parts.link().x, parts.link().y);
        }
        if (parts.note() != null) {
            AllIcons.General.Note.paintIcon(component, g, parts.note().x, parts.note().y);
        }
        if (parts.folded() != null) {
            paintFoldMark(g, parts.folded());
        }

        if (selected || focused) {
            Stroke stroke = selected ? new BasicStroke(JBUIScale.scale(2f))
                    : new BasicStroke(JBUIScale.scale(1f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f,
                            new float[] { JBUIScale.scale(2f), JBUIScale.scale(2f) }, 0f);
            g.setColor(SELECTION);
            g.setStroke(stroke);
            float grow = JBUIScale.scale(2f);
            g.draw(new RoundRectangle2D.Float(box.x - grow, box.y - grow, box.width + 2 * grow, box.height + 2 * grow, arc + grow, arc + grow));
        }
    }

    private static void paintFoldMark(Graphics2D g, Rectangle at) {
        g.setColor(CONNECTOR);
        g.setStroke(new BasicStroke(JBUIScale.scale(1f)));
        g.drawOval(at.x, at.y, at.width - 1, at.height - 1);
        int cx = at.x + at.width / 2;
        int cy = at.y + at.height / 2;
        int arm = at.width / 2 - JBUI.scale(2);
        g.drawLine(cx - arm, cy, cx + arm, cy);
        g.drawLine(cx, cy - arm, cx, cy + arm);
    }

    /** The line from a parent to a child, leaving the parent's side that faces the child. */
    static void paintConnector(Graphics2D g, Rectangle parent, Rectangle child, boolean left) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(CONNECTOR);
        g.setStroke(new BasicStroke(JBUIScale.scale(1f)));
        double sx = left ? parent.getMinX() : parent.getMaxX();
        double sy = parent.getCenterY();
        double ex = left ? child.getMaxX() : child.getMinX();
        double ey = child.getCenterY();
        double mid = (sx + ex) / 2;
        g.draw(new CubicCurve2D.Double(sx, sy, mid, sy, mid, ey, ex, ey));
    }

    /**
     * An arrow link as a cubic curve between the sides of the two nodes that face each other,
     * bowing outwards, with an arrowhead at each end asked for.
     */
    static void paintArrow(Graphics2D g, Rectangle source, Rectangle target, boolean startArrow, boolean endArrow) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean rightward = target.getCenterX() >= source.getCenterX();
        double sx = rightward ? source.getMaxX() : source.getMinX();
        double sy = source.getCenterY();
        double ex = rightward ? target.getMinX() : target.getMaxX();
        double ey = target.getCenterY();
        if (Math.abs(target.getCenterX() - source.getCenterX()) < (source.width + target.width) / 2.0) {
            // one above the other: leave and enter on the right, looping outwards
            sx = source.getMaxX();
            ex = target.getMaxX();
        }
        double bow = Math.max(JBUIScale.scale(40f), Math.abs(ex - sx) / 3);
        double c1x = sx + (sx >= source.getCenterX() ? bow : -bow);
        double c2x = ex + (ex >= target.getCenterX() ? bow : -bow);
        CubicCurve2D curve = new CubicCurve2D.Double(sx, sy, c1x, sy, c2x, ey, ex, ey);
        g.setColor(ARROW_LINK);
        g.setStroke(new BasicStroke(JBUIScale.scale(1.2f)));
        g.draw(curve);
        if (endArrow) {
            paintArrowhead(g, new Point2D.Double(c2x, ey), new Point2D.Double(ex, ey));
        }
        if (startArrow) {
            paintArrowhead(g, new Point2D.Double(c1x, sy), new Point2D.Double(sx, sy));
        }
    }

    private static void paintArrowhead(Graphics2D g, Point2D from, Point2D tip) {
        double angle = Math.atan2(tip.getY() - from.getY(), tip.getX() - from.getX());
        double length = JBUIScale.scale(8f);
        double spread = Math.toRadians(25);
        Path2D head = new Path2D.Double();
        head.moveTo(tip.getX(), tip.getY());
        head.lineTo(tip.getX() - length * Math.cos(angle - spread), tip.getY() - length * Math.sin(angle - spread));
        head.lineTo(tip.getX() - length * Math.cos(angle + spread), tip.getY() - length * Math.sin(angle + spread));
        head.closePath();
        g.fill(head);
    }
}
