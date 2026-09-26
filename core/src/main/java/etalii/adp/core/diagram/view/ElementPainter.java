package etalii.adp.core.diagram.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.intellij.ide.ui.UISettings;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.diagram.Anchor;
import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.Outline;
import etalii.adp.core.diagram.SlotPosition;
import etalii.adp.core.diagram.Tone;
import etalii.adp.core.diagram.model.Element;
import etalii.adp.core.diagram.model.StyleOverride;
import etalii.adp.core.diagram.view.ElementMeasure.PlacedText;
import etalii.adp.core.diagram.view.Scene.ElementRender;

/**
 * How an element looks (FR-005 to FR-009, research R10, R18): its outline in its tone or the
 * file's colours, its texts, its visible anchors, and the selection. A file colour is used as
 * written; when text would not read on what it is drawn on, the texts get a plate, as FreeMind's
 * nodes do, so the author's colour is kept and stays legible in either theme. An undeclared type
 * is drawn as a dashed neutral box labelled with its type.
 */
public final class ElementPainter {

    public static final JBColor CANVAS = new JBColor(0xFFFFFF, 0x1E1F22);
    public static final JBColor TEXT = new JBColor(0x1E1F22, 0xDFE1E5);
    public static final JBColor SELECTION = new JBColor(0x3574F0, 0x548AF7);

    /** The neutral plates text is drawn on when its colour would not read. */
    static final Color LIGHT_PLATE = new Color(0xF7F8FA);
    static final Color DARK_PLATE = new Color(0x2B2D30);
    private static final List<Color> READABLE_TEXT = List.of(new Color(0x1E1F22), new Color(0xF7F8FA), Color.BLACK, Color.WHITE);

    /** WCAG 2.1 AA contrast for text. */
    static final double MIN_TEXT_CONTRAST = 4.5;

    private static final float ANCHOR_RADIUS = 3f;

    /** The colours an element is painted in. {@code fill} is {@code null} when the outline is not drawn; {@code plate} when none is needed. */
    public record Colours(Color fill, Color border, Color text, Color plate) {
    }

    private ElementPainter() {
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

    /**
     * The colours for a tone and the file's own. A file text colour is kept: as written on a file
     * fill, and on a plate when it would not read on the canvas or the tone's fill; without one, a
     * file fill gets a text colour that reads on it.
     *
     * @param drawn whether the outline is drawn; without it there is no fill and texts sit on the canvas
     */
    public static Colours colours(Tone tone, StyleOverride style, boolean drawn) {
        Color fill = !drawn ? null : style != null && style.fill() != null ? style.fill() : tone.fill();
        Color border = style != null && style.border() != null ? style.border() : tone.border();
        Color behind = fill != null ? fill : CANVAS;
        if (style != null && style.text() != null) {
            Color text = style.text();
            Color plate = null;
            // a text colour the file sets on a fill it also sets is the author's choice, drawn as written
            boolean ownFill = fill != null && style.fill() != null;
            if (!ownFill && contrast(text, behind) < MIN_TEXT_CONTRAST) {
                plate = contrast(text, LIGHT_PLATE) >= contrast(text, DARK_PLATE) ? LIGHT_PLATE : DARK_PLATE;
            }
            return new Colours(fill, border, text, plate);
        }
        Color text = tone.text();
        if (contrast(text, behind) < MIN_TEXT_CONTRAST) {
            text = READABLE_TEXT.stream().filter(c -> contrast(c, behind) >= MIN_TEXT_CONTRAST).findFirst()
                    .orElse(contrast(Color.BLACK, behind) >= contrast(Color.WHITE, behind) ? Color.BLACK : Color.WHITE);
        }
        return new Colours(fill, border, text, null);
    }

    /** Lay out one element at {@code bounds}: its texts, anchors and colours. */
    public static ElementRender render(ElementType type, Element element, Rectangle2D bounds, ElementMeasure measure) {
        boolean placeholder = type == null;
        Outline outline = placeholder ? Outline.RECTANGLE : type.outline();
        Colours colours = colours(placeholder ? Tone.NEUTRAL : type.tone(), placeholder ? null : element.style(), outline.drawn());
        List<PlacedText> texts = measure.place(type, element, bounds);
        Map<String, String> shown = new LinkedHashMap<>();
        Rectangle2D extent = (Rectangle2D) bounds.clone();
        for (PlacedText text : texts) {
            shown.put(text.slot().id(), text.block().text());
            if (!text.block().lines().isEmpty()) {
                extent.add(text.box());
            }
        }
        List<AnchorView> anchors = new ArrayList<>();
        if (!placeholder) {
            for (Anchor anchor : type.anchors()) {
                Point2D at = anchor.perimeter() ? new Point2D.Double(bounds.getCenterX(), bounds.getCenterY()) : AnchorGeometry.fixed(anchor, bounds);
                anchors.add(new AnchorView(anchor.id(), at, anchor.visible() && !anchor.perimeter()));
            }
        }
        double grow = JBUI.scale(6);
        extent.setRect(extent.getX() - grow, extent.getY() - grow, extent.getWidth() + 2 * grow, extent.getHeight() + 2 * grow);
        Font font = texts.isEmpty() ? JBFont.label() : texts.get(0).block().font();
        Rectangle box = new Rectangle((int) Math.round(bounds.getX()), (int) Math.round(bounds.getY()), (int) Math.round(bounds.getWidth()),
                (int) Math.round(bounds.getHeight()));
        ElementView view = new ElementView(element.key(), element.type(), box, shown, colours.fill(), colours.border(), colours.text(), font,
                placeholder, outline, colours.plate());
        return new ElementRender(view, type, bounds, outline.shape(bounds), texts, anchors, extent);
    }

    /** Paint one element. */
    public static void paint(Graphics2D g, ElementRender render, boolean selected) {
        ElementView view = render.view();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        float line = JBUIScale.scale(1f);
        if (view.outline().drawn()) {
            g.setColor(view.fill());
            g.fill(render.outline());
            g.setColor(view.border());
            g.setStroke(view.placeholder()
                    ? new BasicStroke(line, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 4f, new float[] { 4 * line, 3 * line }, 0f)
                    : new BasicStroke(line));
            g.draw(render.outline());
        }
        UISettings.setupAntialiasing(g);
        Color outside = contrast(view.text(), CANVAS) >= MIN_TEXT_CONTRAST || view.plate() != null ? view.text() : TEXT;
        for (PlacedText text : render.texts()) {
            if (text.block().lines().isEmpty()) {
                continue;
            }
            boolean inside = text.slot().position() != SlotPosition.ABOVE && text.slot().position() != SlotPosition.BELOW;
            if (view.plate() != null) {
                Rectangle2D box = text.box();
                double pad = JBUI.scale(2);
                g.setColor(view.plate());
                g.fill(new RoundRectangle2D.Double(box.getX() - pad, box.getY() - pad / 2, box.getWidth() + 2 * pad, box.getHeight() + pad, 4 * pad,
                        4 * pad));
            }
            g.setColor(inside ? view.text() : outside);
            text.block().paint(g, text.box(), text.slot().position());
        }
        float radius = JBUIScale.scale(ANCHOR_RADIUS);
        for (AnchorView anchor : render.anchors()) {
            if (anchor.visible()) {
                Point2D at = anchor.position();
                Shape marker = new Ellipse2D.Double(at.getX() - radius, at.getY() - radius, 2 * radius, 2 * radius);
                g.setColor(CANVAS);
                g.fill(marker);
                g.setColor(view.border());
                g.setStroke(new BasicStroke(line));
                g.draw(marker);
            }
        }
        if (selected) {
            Rectangle2D b = render.bounds();
            float grow = JBUIScale.scale(3f);
            float arc = JBUIScale.scale(8f);
            g.setColor(SELECTION);
            g.setStroke(new BasicStroke(JBUIScale.scale(2f)));
            g.draw(new RoundRectangle2D.Double(b.getX() - grow, b.getY() - grow, b.getWidth() + 2 * grow, b.getHeight() + 2 * grow, arc, arc));
        }
    }
}
