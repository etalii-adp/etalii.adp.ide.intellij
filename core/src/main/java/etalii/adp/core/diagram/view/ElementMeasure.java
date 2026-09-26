package etalii.adp.core.diagram.view;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.Sizing;
import etalii.adp.core.diagram.SlotPosition;
import etalii.adp.core.diagram.TextSlot;
import etalii.adp.core.diagram.model.Element;

/**
 * The size an element is drawn at and where its texts sit (FR-007, FR-008, research R10). Slots
 * inside the element share three rows (top, middle, bottom) of up to three cells (left, centre,
 * right), so they never overlap; the centre cell gets what the others leave. Slots above and below
 * sit outside the bounds. Sizes declared in unscaled pixels are scaled with {@link JBUI}; sizes
 * stored in the file are diagram coordinates and are not. An element's own font from the file
 * ({@code StyleOverride.font}) replaces the IDE's label font for its texts.
 */
public final class ElementMeasure {

    /** Placeholders show their type id in this slot. */
    public static final String PLACEHOLDER_SLOT = "type";

    private static final int PAD_H = 8;
    private static final int PAD_V = 4;
    private static final int GAP = 2;
    private static final int MIN_WIDTH = 24;
    private static final int MIN_HEIGHT = 16;
    private static final int PLACEHOLDER_WIDTH = 80;
    private static final int PLACEHOLDER_HEIGHT = 40;
    private static final double UNLIMITED = 100_000;
    private static final TextSlot PLACEHOLDER = new TextSlot(PLACEHOLDER_SLOT, PLACEHOLDER_SLOT, SlotPosition.CENTER, false,
            TextSlot.Style.ITALIC, false);

    /** One text slot laid out in its box, in diagram coordinates. */
    public record PlacedText(TextSlot slot, TextBlock block, Rectangle2D box) {
    }

    private record Size(double width, double height) {
    }

    private final FontRenderContext frc;

    public ElementMeasure(FontRenderContext frc) {
        this.frc = frc;
    }

    /** The IDE's label font in the slot's style; plain is plain even where the look and feel's label font is not. */
    public static Font font(TextSlot.Style style) {
        return switch (style) {
        case PLAIN -> JBFont.label().asPlain();
        case BOLD -> JBFont.label().asBold();
        case ITALIC -> JBFont.label().asItalic();
        case SMALL -> JBFont.small();
        };
    }

    /** The element's own font from the file in the slot's style, or the IDE's label font in it when the file sets none. */
    public static Font font(TextSlot.Style style, Element element) {
        Font own = element == null || element.style() == null ? null : element.style().font();
        if (own == null) {
            return font(style);
        }
        return switch (style) {
        case PLAIN -> own;
        case BOLD -> own.deriveFont(own.getStyle() | Font.BOLD);
        case ITALIC -> own.deriveFont(own.getStyle() | Font.ITALIC);
        case SMALL -> own.deriveFont(own.getSize2D() * JBFont.small().getSize2D() / JBFont.label().getSize2D());
        };
    }

    /** The size to draw an element of {@code type} ({@code null} for a placeholder), from its sizing and what the file stores. */
    public Dimension2D size(ElementType type, Element element) {
        Rectangle2D stored = element.bounds();
        double storedWidth = stored == null ? 0 : stored.getWidth();
        double storedHeight = stored == null ? 0 : stored.getHeight();
        List<TextSlot> slots = slots(type);
        double width;
        double height;
        if (type == null) {
            Size natural = content(slots, element, true, UNLIMITED);
            width = storedWidth > 0 ? storedWidth : Math.max(JBUI.scale(PLACEHOLDER_WIDTH), natural.width() + 2 * padH());
            height = storedHeight > 0 ? storedHeight : Math.max(JBUI.scale(PLACEHOLDER_HEIGHT), natural.height() + 2 * padV());
            return dimension(width, height);
        }
        switch (type.sizing()) {
        case Sizing.Fixed fixed -> {
            width = JBUI.scale(fixed.width());
            height = JBUI.scale(fixed.height());
        }
        case Sizing.FromDiagram from -> {
            Size natural = storedWidth > 0 && storedHeight > 0 ? new Size(0, 0) : content(slots, element, true, UNLIMITED);
            width = Math.max(storedWidth > 0 ? storedWidth : natural.width() + 2 * padH(), JBUI.scale(from.minWidth()));
            height = Math.max(storedHeight > 0 ? storedHeight : natural.height() + 2 * padV(), JBUI.scale(from.minHeight()));
        }
        case Sizing.Auto auto -> {
            double max = JBUI.scale(auto.maxWidth());
            if (storedWidth > 0) {
                width = storedWidth;
                height = content(slots, element, false, width - 2 * padH()).height() + 2 * padV();
            } else {
                Size natural = content(slots, element, true, max - 2 * padH());
                width = Math.min(max, natural.width() + 2 * padH());
                height = natural.height() + 2 * padV();
            }
            height = Math.max(height, storedHeight);
        }
        }
        return dimension(Math.max(width, JBUI.scale(MIN_WIDTH)), Math.max(height, JBUI.scale(MIN_HEIGHT)));
    }

    /** Every text slot laid out for an element drawn at {@code bounds}, in declaration order. */
    public List<PlacedText> place(ElementType type, Element element, Rectangle2D bounds) {
        List<TextSlot> slots = slots(type);
        List<PlacedText> placed = new ArrayList<>();
        Rectangle2D inner = new Rectangle2D.Double(bounds.getX() + padH(), bounds.getY() + padV(), Math.max(0, bounds.getWidth() - 2 * padH()),
                Math.max(0, bounds.getHeight() - 2 * padV()));
        double[] rowHeights = new double[3];
        double[][] cellWidths = new double[3][3];
        for (int row = 0; row < 3; row++) {
            measureRow(slots, element, row, inner.getWidth(), cellWidths[row]);
            rowHeights[row] = rowHeight(slots, element, row, cellWidths[row]);
        }
        double top = Math.min(rowHeights[0], inner.getHeight());
        double bottom = Math.min(rowHeights[2], Math.max(0, inner.getHeight() - top - (top > 0 ? gap() : 0)));
        double middleTop = inner.getY() + top + (top > 0 ? gap() : 0);
        double middleBottom = inner.getMaxY() - bottom - (bottom > 0 ? gap() : 0);
        Rectangle2D[] rows = {
                new Rectangle2D.Double(inner.getX(), inner.getY(), inner.getWidth(), top),
                new Rectangle2D.Double(inner.getX(), middleTop, inner.getWidth(), Math.max(0, middleBottom - middleTop)),
                new Rectangle2D.Double(inner.getX(), inner.getMaxY() - bottom, inner.getWidth(), bottom) };
        for (TextSlot slot : slots) {
            int row = row(slot.position());
            if (row < 0) {
                continue;
            }
            int column = column(slot.position());
            double[] widths = cellWidths[row];
            double x = switch (column) {
            case 0 -> rows[row].getX();
            case 1 -> rows[row].getX() + widths[0] + (widths[0] > 0 ? gap() : 0);
            default -> rows[row].getMaxX() - widths[2];
            };
            placed.add(placeInCell(slot, element, new Rectangle2D.Double(x, rows[row].getY(), widths[column], rows[row].getHeight()), slots));
        }
        double above = bounds.getY();
        double below = bounds.getMaxY();
        for (TextSlot slot : slots) {
            if (slot.position() == SlotPosition.ABOVE || slot.position() == SlotPosition.BELOW) {
                TextBlock block = TextBlock.measure(value(element, slot), font(slot.style(), element), frc, slot.wrap() ? bounds.getWidth() : UNLIMITED,
                        slot.wrap());
                boolean up = slot.position() == SlotPosition.ABOVE;
                Rectangle2D area = new Rectangle2D.Double(bounds.getX(), up ? above : bounds.getY(), bounds.getWidth(), up ? 0 : below - bounds.getY());
                Rectangle2D box = TextBlock.place(block.width(), block.height(), area, slot.position(), gap());
                placed.add(new PlacedText(slot, block, box));
                if (up) {
                    above = box.getY();
                } else {
                    below = box.getMaxY();
                }
            }
        }
        return placed;
    }

    /** One slot in its cell; slots sharing a cell are stacked in declaration order. */
    // simplified: stacked slots share the cell's height first come, first served; weigh them when a designer needs it
    private PlacedText placeInCell(TextSlot slot, Element element, Rectangle2D cell, List<TextSlot> slots) {
        List<TextSlot> sharing = slots.stream().filter(s -> row(s.position()) == row(slot.position()) && column(s.position()) == column(slot.position()))
                .toList();
        if (sharing.size() == 1) {
            TextBlock block = TextBlock.layout(value(element, slot), font(slot.style(), element), frc, cell.getWidth(), cell.getHeight(), slot.wrap());
            return new PlacedText(slot, block, TextBlock.place(block.width(), block.height(), cell, slot.position(), 0));
        }
        double total = 0;
        List<TextBlock> natural = new ArrayList<>();
        for (TextSlot s : sharing) {
            TextBlock block = TextBlock.measure(value(element, s), font(s.style(), element), frc, cell.getWidth(), s.wrap());
            natural.add(block);
            total += block.height();
        }
        double y = TextBlock.place(0, Math.min(total, cell.getHeight()), cell, slot.position(), 0).getY();
        double room = cell.getMaxY() - y;
        for (int i = 0; i < sharing.size(); i++) {
            TextSlot s = sharing.get(i);
            TextBlock block = TextBlock.layout(value(element, s), font(s.style(), element), frc, cell.getWidth(), Math.max(0, room), s.wrap());
            Rectangle2D row = new Rectangle2D.Double(cell.getX(), y, cell.getWidth(), block.height());
            if (s == slot) {
                return new PlacedText(slot, block, TextBlock.place(block.width(), block.height(), row, slot.position(), 0));
            }
            y += block.height();
            room -= block.height();
        }
        throw new IllegalStateException("slot " + slot.id() + " is not in its own cell");
    }

    /** The widths of the left, centre and right cells of a row within {@code width}; the centre gets what the others leave. */
    private void measureRow(List<TextSlot> slots, Element element, int row, double width, double[] cells) {
        cells[0] = cellWidth(slots, element, row, 0, width);
        cells[2] = cellWidth(slots, element, row, 2, width);
        double rest = width - cells[0] - cells[2] - (cells[0] > 0 ? gap() : 0) - (cells[2] > 0 ? gap() : 0);
        cells[1] = Math.max(0, rest);
    }

    private double cellWidth(List<TextSlot> slots, Element element, int row, int column, double width) {
        double widest = 0;
        for (TextSlot slot : slots) {
            if (row(slot.position()) == row && column(slot.position()) == column) {
                widest = Math.max(widest, TextBlock.measure(value(element, slot), font(slot.style(), element), frc, width, slot.wrap()).width());
            }
        }
        return Math.min(widest, width);
    }

    private double rowHeight(List<TextSlot> slots, Element element, int row, double[] cells) {
        double height = 0;
        for (int column = 0; column < 3; column++) {
            double cell = 0;
            for (TextSlot slot : slots) {
                if (row(slot.position()) == row && column(slot.position()) == column) {
                    cell += TextBlock.measure(value(element, slot), font(slot.style(), element), frc, cells[column], slot.wrap()).height();
                }
            }
            height = Math.max(height, cell);
        }
        return height;
    }

    /**
     * The inner size the slots need within {@code width}: at their natural width when
     * {@code natural}, else the height they need at exactly that width.
     */
    private Size content(List<TextSlot> slots, Element element, boolean natural, double width) {
        double totalWidth = 0;
        double totalHeight = 0;
        int rows = 0;
        for (int row = 0; row < 3; row++) {
            double[] cells = new double[3];
            cells[0] = cellWidth(slots, element, row, 0, width);
            cells[2] = cellWidth(slots, element, row, 2, width);
            double rest = Math.max(0, width - cells[0] - cells[2] - (cells[0] > 0 ? gap() : 0) - (cells[2] > 0 ? gap() : 0));
            cells[1] = natural ? cellWidth(slots, element, row, 1, rest) : rest;
            double height = rowHeight(slots, element, row, cells);
            if (height > 0) {
                rows++;
                totalHeight += height;
                int used = (cells[0] > 0 ? 1 : 0) + (cells[1] > 0 ? 1 : 0) + (cells[2] > 0 ? 1 : 0);
                totalWidth = Math.max(totalWidth, cells[0] + cells[1] + cells[2] + Math.max(0, used - 1) * gap());
            }
        }
        return new Size(totalWidth, totalHeight + Math.max(0, rows - 1) * gap());
    }

    private static List<TextSlot> slots(ElementType type) {
        return type == null ? List.of(PLACEHOLDER) : type.texts();
    }

    private static String value(Element element, TextSlot slot) {
        String value = slot == PLACEHOLDER ? element.type() : element.property(slot.property());
        return slot != PLACEHOLDER && element.style() != null && element.style().html() && !value.isEmpty() ? "<html>" + value : value;
    }

    /** 0, 1 or 2 for the top, middle and bottom rows; -1 outside the element. */
    private static int row(SlotPosition position) {
        return switch (position) {
        case TOP_LEFT, TOP, TOP_RIGHT -> 0;
        case LEFT, CENTER, RIGHT -> 1;
        case BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT -> 2;
        case ABOVE, BELOW -> -1;
        };
    }

    /** 0, 1 or 2 for the left, centre and right cells. */
    private static int column(SlotPosition position) {
        return switch (position) {
        case TOP_LEFT, LEFT, BOTTOM_LEFT -> 0;
        case TOP_RIGHT, RIGHT, BOTTOM_RIGHT -> 2;
        default -> 1;
        };
    }

    private static int padH() {
        return JBUI.scale(PAD_H);
    }

    private static int padV() {
        return JBUI.scale(PAD_V);
    }

    private static int gap() {
        return JBUI.scale(GAP);
    }

    private static Dimension2D dimension(double width, double height) {
        return new Dimension2D() {
            private double w = width;
            private double h = height;

            @Override
            public double getWidth() {
                return w;
            }

            @Override
            public double getHeight() {
                return h;
            }

            @Override
            public void setSize(double width, double height) {
                w = width;
                h = height;
            }
        };
    }
}
