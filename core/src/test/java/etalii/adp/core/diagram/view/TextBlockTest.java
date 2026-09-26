package etalii.adp.core.diagram.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import etalii.adp.core.diagram.SlotPosition;

/** FR-007, FR-008: wrapping, cutting off with an ellipsis, never taller than allowed, and placement. */
class TextBlockTest {

    private static final Font FONT = new Font(Font.DIALOG, Font.PLAIN, 12);
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final String LONG = "the quick brown fox jumps over the lazy dog";

    private static double width(String text) {
        return FONT.getStringBounds(text, FRC).getWidth();
    }

    private static List<String> words(List<String> lines) {
        return lines.stream().flatMap(line -> Arrays.stream(line.trim().split("\\s+"))).filter(w -> !w.isEmpty()).toList();
    }

    @Test
    void wrapsToTheWidthKeepingEveryWord() {
        double max = width("the quick brown") + 1;
        TextBlock block = TextBlock.layout(LONG, FONT, FRC, max, Double.MAX_VALUE, true);

        assertTrue(block.lines().size() >= 3, block.lines().toString());
        for (String line : block.lines()) {
            assertTrue(width(line) <= max + 0.5, line);
        }
        assertEquals(List.of(LONG.split(" ")), words(block.lines()));
        assertTrue(block.width() <= max + 0.5);
        assertEquals(String.join("\n", block.lines()), block.text());
    }

    @Test
    void lineBreaksInTheTextAreKept() {
        TextBlock block = TextBlock.layout("one\n\ntwo", FONT, FRC, 500, Double.MAX_VALUE, true);

        assertEquals(List.of("one", "", "two"), block.lines());
        assertEquals(3 * block.lineHeight(), block.height(), 1e-6);
    }

    @Test
    void withoutWrappingALongLineEndsInAnEllipsis() {
        TextBlock block = TextBlock.layout(LONG, FONT, FRC, 60, Double.MAX_VALUE, false);

        assertEquals(1, block.lines().size());
        String line = block.lines().get(0);
        assertTrue(line.endsWith(TextBlock.ELLIPSIS), line);
        assertTrue(LONG.startsWith(line.substring(0, line.length() - 1).trim()), line);
        assertTrue(width(line) <= 60.5, line);
        assertTrue(block.cut());
    }

    @Test
    void aTextThatFitsIsNotCut() {
        TextBlock block = TextBlock.layout("Place order", FONT, FRC, 200, Double.MAX_VALUE, false);

        assertEquals(List.of("Place order"), block.lines());
        assertTrue(!block.cut());
    }

    @Test
    void neverTallerThanTheBoundsAllow() {
        double max = width("the quick") + 1;
        double lineHeight = TextBlock.layout("x", FONT, FRC, 100, Double.MAX_VALUE, true).lineHeight();

        TextBlock two = TextBlock.layout(LONG, FONT, FRC, max, lineHeight * 2.5, true);
        assertEquals(2, two.lines().size());
        assertTrue(two.lines().get(1).endsWith(TextBlock.ELLIPSIS), two.lines().toString());
        assertTrue(two.height() <= lineHeight * 2.5);
        assertTrue(two.cut());

        TextBlock none = TextBlock.layout(LONG, FONT, FRC, max, lineHeight * 0.5, true);
        assertEquals(List.of(), none.lines());
        assertEquals(0, none.height(), 1e-9);
    }

    @Test
    void measuresTheNaturalSizeForAutoSizing() {
        TextBlock one = TextBlock.measure("Place order", FONT, FRC, 200, true);
        assertEquals(1, one.lines().size());
        assertEquals(width("Place order"), one.width(), 1.0);
        assertEquals(one.lineHeight(), one.height(), 1e-9);

        TextBlock wrapped = TextBlock.measure(LONG, FONT, FRC, 100, true);
        assertTrue(wrapped.width() <= 100.5);
        assertEquals(wrapped.lines().size() * wrapped.lineHeight(), wrapped.height(), 1e-6);

        TextBlock empty = TextBlock.measure("", FONT, FRC, 100, true);
        assertEquals(0, empty.width(), 1e-9);
        assertEquals(0, empty.height(), 1e-9);
    }

    @Test
    void placesTheBlockAtEachOfTheNinePositionsAndAboveAndBelow() {
        Rectangle2D area = new Rectangle2D.Double(0, 0, 100, 60);
        double w = 20;
        double h = 10;
        assertPlaced(0, 0, TextBlock.place(w, h, area, SlotPosition.TOP_LEFT, 4));
        assertPlaced(40, 0, TextBlock.place(w, h, area, SlotPosition.TOP, 4));
        assertPlaced(80, 0, TextBlock.place(w, h, area, SlotPosition.TOP_RIGHT, 4));
        assertPlaced(0, 25, TextBlock.place(w, h, area, SlotPosition.LEFT, 4));
        assertPlaced(40, 25, TextBlock.place(w, h, area, SlotPosition.CENTER, 4));
        assertPlaced(80, 25, TextBlock.place(w, h, area, SlotPosition.RIGHT, 4));
        assertPlaced(0, 50, TextBlock.place(w, h, area, SlotPosition.BOTTOM_LEFT, 4));
        assertPlaced(40, 50, TextBlock.place(w, h, area, SlotPosition.BOTTOM, 4));
        assertPlaced(80, 50, TextBlock.place(w, h, area, SlotPosition.BOTTOM_RIGHT, 4));
        assertPlaced(40, -14, TextBlock.place(w, h, area, SlotPosition.ABOVE, 4));
        assertPlaced(40, 64, TextBlock.place(w, h, area, SlotPosition.BELOW, 4));
    }

    private static void assertPlaced(double x, double y, Rectangle2D placed) {
        assertEquals(x, placed.getX(), 1e-9, placed.toString());
        assertEquals(y, placed.getY(), 1e-9, placed.toString());
        assertEquals(20, placed.getWidth(), 1e-9);
        assertEquals(10, placed.getHeight(), 1e-9);
    }

    @Test
    void htmlIsLaidOutAsHtmlAndReadAsPlainText() {
        TextBlock block = TextBlock.layout("<html><b>Interface</b><br/>+ field: <i>type</i>&nbsp;&laquo;x&raquo;", FONT, FRC, 500, Double.MAX_VALUE, true);
        assertEquals(List.of("Interface", "+ field: type «x»"), block.lines());
        assertTrue(block.height() > 0);
        assertTrue(block.width() <= 500);
        assertEquals(false, block.cut());
    }

    @Test
    void htmlWrapsToTheWidthAndIsCutToTheHeight() {
        TextBlock wide = TextBlock.layout("<html>" + LONG, FONT, FRC, 1000, Double.MAX_VALUE, true);
        TextBlock narrow = TextBlock.layout("<html>" + LONG, FONT, FRC, 60, Double.MAX_VALUE, true);
        assertTrue(narrow.width() <= 60);
        assertTrue(narrow.height() > wide.height());
        TextBlock low = TextBlock.layout("<html>" + LONG, FONT, FRC, 60, wide.height(), true);
        assertTrue(low.cut());
        assertEquals(wide.height(), low.height(), 1e-9);
    }
}
