package etalii.adp.core.diagram.view;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D;
import java.text.AttributedString;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.View;

import etalii.adp.core.diagram.SlotPosition;

/**
 * One text laid out in lines (FR-007, FR-008, research R10): wrapped to a width with
 * {@link LineBreakMeasurer}, or one line per paragraph cut off with an ellipsis, and never taller
 * than it may be. A text starting with {@code <html>} is HTML, laid out and drawn by Swing's HTML
 * renderer and clipped to its box. The lines are the text as drawn, as plain text.
 */
public final class TextBlock {

    public static final String ELLIPSIS = "…";

    private final List<String> lines;
    private final Font font;
    private final double width;
    private final double lineHeight;
    private final double ascent;
    private final boolean cut;
    /** The HTML source, or {@code null} for plain text. */
    private final String html;
    private final double htmlWidth;
    private final double htmlHeight;
    /** The HTML view for the colour it was last drawn in; a cache, rebuilt when the colour changes. */
    private View view;
    private Color viewColour;

    private TextBlock(List<String> lines, Font font, FontRenderContext frc, double lineHeight, double ascent, boolean cut) {
        this.lines = List.copyOf(lines);
        this.font = font;
        this.lineHeight = lineHeight;
        this.ascent = ascent;
        this.cut = cut;
        double widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, width(line, font, frc));
        }
        this.width = widest;
        this.html = null;
        this.htmlWidth = 0;
        this.htmlHeight = 0;
    }

    private TextBlock(String html, List<String> lines, Font font, double layoutWidth, double width, double height, boolean cut) {
        this.lines = List.copyOf(lines);
        this.font = font;
        this.lineHeight = lines.isEmpty() ? height : height / lines.size();
        this.ascent = 0;
        this.cut = cut;
        this.width = width;
        this.html = html;
        this.htmlWidth = layoutWidth;
        this.htmlHeight = height;
    }

    /** The natural size of a text for auto-sizing: wrapped at {@code maxWidth} when {@code wrap}, of any height. */
    public static TextBlock measure(String text, Font font, FontRenderContext frc, double maxWidth, boolean wrap) {
        return layout(text, font, frc, maxWidth, Double.MAX_VALUE, wrap);
    }

    /** The text within {@code maxWidth} by {@code maxHeight}; lines that do not fit are dropped and the last kept one ends in an ellipsis. */
    public static TextBlock layout(String text, Font font, FontRenderContext frc, double maxWidth, double maxHeight, boolean wrap) {
        if (text != null && BasicHTML.isHTMLString(text)) {
            return html(text, font, maxWidth, maxHeight, wrap);
        }
        LineMetrics metrics = font.getLineMetrics("Xg", frc);
        double lineHeight = Math.ceil(metrics.getAscent() + metrics.getDescent() + metrics.getLeading());
        List<String> lines = new ArrayList<>();
        boolean cut = false;
        if (text != null && !text.isEmpty()) {
            for (String paragraph : text.split("\r\n|\r|\n", -1)) {
                if (!wrap) {
                    String fitted = ellipsize(paragraph, font, frc, maxWidth);
                    cut |= !fitted.equals(paragraph);
                    lines.add(fitted);
                } else if (paragraph.isEmpty()) {
                    lines.add("");
                } else {
                    wrap(paragraph, font, frc, maxWidth, lines);
                }
            }
        }
        int room = maxHeight >= Double.MAX_VALUE / 2 ? lines.size() : (int) Math.floor((maxHeight + 1e-9) / lineHeight);
        if (room < lines.size()) {
            cut = true;
            List<String> kept = new ArrayList<>(lines.subList(0, Math.max(0, room)));
            if (!kept.isEmpty()) {
                int last = kept.size() - 1;
                kept.set(last, withEllipsis(kept.get(last), font, frc, maxWidth));
            }
            lines = kept;
        }
        return new TextBlock(lines, font, frc, lineHeight, Math.ceil(metrics.getAscent()), cut);
    }

    /** HTML wrapped to {@code maxWidth} when {@code wrap}, else at its natural width, cut to fit. */
    private static TextBlock html(String source, Font font, double maxWidth, double maxHeight, boolean wrap) {
        View view = view(source, font, Color.BLACK);
        double natural = Math.ceil(view.getPreferredSpan(View.X_AXIS));
        double layoutWidth = wrap ? Math.min(natural, Math.max(1, maxWidth)) : natural;
        view.setSize((float) layoutWidth, 0);
        double height = Math.ceil(view.getPreferredSpan(View.Y_AXIS));
        List<String> lines = new ArrayList<>();
        for (String line : plain(source).split("\n")) {
            if (!line.isBlank()) {
                lines.add(line.strip());
            }
        }
        boolean cut = height > maxHeight || layoutWidth > maxWidth;
        return new TextBlock(source, lines, font, layoutWidth, Math.min(layoutWidth, maxWidth), Math.min(height, maxHeight), cut);
    }

    /** The HTML as plain text, with a line break for each break, paragraph, division, list item and rule. */
    private static String plain(String source) {
        String marked = source.replaceAll("(?i)<br\\s*/?>|</p>|</div>|</li>|<hr[^>]*>", " ");
        HTMLEditorKit kit = new HTMLEditorKit();
        Document document = kit.createDefaultDocument();
        try {
            kit.read(new StringReader(marked), document, 0);
            return document.getText(0, document.getLength()).replace(' ', '\n');
        } catch (IOException | BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static View view(String source, Font font, Color colour) {
        JLabel label = new JLabel();
        label.setFont(font);
        label.setForeground(colour);
        return BasicHTML.createHTMLView(label, source);
    }

    private static void wrap(String paragraph, Font font, FontRenderContext frc, double maxWidth, List<String> lines) {
        AttributedString attributed = new AttributedString(paragraph);
        attributed.addAttribute(TextAttribute.FONT, font);
        LineBreakMeasurer measurer = new LineBreakMeasurer(attributed.getIterator(), frc);
        float width = (float) Math.max(1, maxWidth);
        while (measurer.getPosition() < paragraph.length()) {
            int start = measurer.getPosition();
            int end = measurer.nextOffset(width);
            if (end <= start) {
                end = start + 1;
            }
            measurer.setPosition(end);
            lines.add(paragraph.substring(start, end).stripTrailing());
        }
    }

    private static String ellipsize(String line, Font font, FontRenderContext frc, double maxWidth) {
        return width(line, font, frc) <= maxWidth ? line : withEllipsis(line, font, frc, maxWidth);
    }

    /** The longest start of {@code line} that fits with an ellipsis after it. */
    private static String withEllipsis(String line, Font font, FontRenderContext frc, double maxWidth) {
        String base = line.stripTrailing();
        int low = 0;
        int high = base.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (width(base.substring(0, mid).stripTrailing() + ELLIPSIS, font, frc) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        String kept = base.substring(0, low).stripTrailing() + ELLIPSIS;
        return width(kept, font, frc) <= maxWidth || low > 0 ? kept : "";
    }

    private static double width(String text, Font font, FontRenderContext frc) {
        return text.isEmpty() ? 0 : font.getStringBounds(text, frc).getWidth();
    }

    /**
     * Where a block of {@code width} by {@code height} sits in {@code area}: one of nine places
     * inside it, or {@code gap} above or below it, centred.
     */
    public static Rectangle2D place(double width, double height, Rectangle2D area, SlotPosition position, double gap) {
        double left = area.getX();
        double centre = area.getCenterX() - width / 2;
        double right = area.getMaxX() - width;
        double top = area.getY();
        double middle = area.getCenterY() - height / 2;
        double bottom = area.getMaxY() - height;
        return switch (position) {
        case TOP_LEFT -> new Rectangle2D.Double(left, top, width, height);
        case TOP -> new Rectangle2D.Double(centre, top, width, height);
        case TOP_RIGHT -> new Rectangle2D.Double(right, top, width, height);
        case LEFT -> new Rectangle2D.Double(left, middle, width, height);
        case CENTER -> new Rectangle2D.Double(centre, middle, width, height);
        case RIGHT -> new Rectangle2D.Double(right, middle, width, height);
        case BOTTOM_LEFT -> new Rectangle2D.Double(left, bottom, width, height);
        case BOTTOM -> new Rectangle2D.Double(centre, bottom, width, height);
        case BOTTOM_RIGHT -> new Rectangle2D.Double(right, bottom, width, height);
        case ABOVE -> new Rectangle2D.Double(centre, top - gap - height, width, height);
        case BELOW -> new Rectangle2D.Double(centre, area.getMaxY() + gap, width, height);
        };
    }

    /** Draws the lines in {@code box}, each aligned left, centred or right as {@code position} says. */
    public void paint(Graphics2D g, Rectangle2D box, SlotPosition position) {
        if (html != null) {
            if (view == null || !g.getColor().equals(viewColour)) {
                viewColour = g.getColor();
                view = view(html, font, viewColour);
                view.setSize((float) htmlWidth, 0);
            }
            Graphics2D clipped = (Graphics2D) g.create();
            try {
                clipped.clip(box);
                view.paint(clipped, new Rectangle((int) Math.round(box.getX()), (int) Math.round(box.getY()), (int) Math.ceil(htmlWidth),
                        (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS))));
            } finally {
                clipped.dispose();
            }
            return;
        }
        g.setFont(font);
        FontRenderContext frc = g.getFontRenderContext();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) {
                continue;
            }
            double lineWidth = width(line, font, frc);
            double x = switch (position) {
            case TOP_LEFT, LEFT, BOTTOM_LEFT -> box.getX();
            case TOP_RIGHT, RIGHT, BOTTOM_RIGHT -> box.getMaxX() - lineWidth;
            default -> box.getCenterX() - lineWidth / 2;
            };
            g.drawString(line, (float) x, (float) (box.getY() + i * lineHeight + ascent));
        }
    }

    public List<String> lines() {
        return lines;
    }

    /** The lines as drawn, joined by line breaks. */
    public String text() {
        return String.join("\n", lines);
    }

    public Font font() {
        return font;
    }

    /** The widest line. */
    public double width() {
        return width;
    }

    public double height() {
        return html != null ? htmlHeight : lines.size() * lineHeight;
    }

    public double lineHeight() {
        return lineHeight;
    }

    /** True when lines were dropped or a line was cut off. */
    public boolean cut() {
        return cut;
    }
}
