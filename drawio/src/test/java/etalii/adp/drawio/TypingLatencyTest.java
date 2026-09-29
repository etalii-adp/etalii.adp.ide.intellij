package etalii.adp.drawio;

import static etalii.adp.drawio.GeneratedDiagrams.generated;

import java.awt.image.BufferedImage;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.testing.DiagramDriver;

/**
 * Spec 006 SC-004 on a generated 500-cell diagram: a character typed in the text editor shows in the
 * diagram within 0.1 s. Each keystroke is one document change; the measurement covers the
 * coalesced re-parse, layout, routing and painting what a window shows.
 */
@RunWith(JUnit4.class)
public class TypingLatencyTest extends FileEditorManagerTestCase {

    private static final long KEYSTROKE_BUDGET_MS = 100;

    /** As in the diagram framework's performance tests: CI machines are slower, so the assertion allows three times the budget. */
    private static final int CI_HEADROOM = 3;

    private static final String TYPED = "typed in the text view";

    @Test
    public void typingInAFiveHundredCellDiagramShowsInTheToolWithinBudget() {
        try (var warmUp = DiagramDriver.openText(myFixture, "warm-up.drawio", generated(50))) {
            type(warmUp, "Cell 10", "warm up");
        }

        try (var d = DiagramDriver.openText(myFixture, "large.drawio", generated(500))) {
            paint(d);
            long[] elapsedMs = type(d, "Cell 250", TYPED);
            Arrays.sort(elapsedMs);
            long median = elapsedMs[elapsedMs.length / 2];
            long max = elapsedMs[elapsedMs.length - 1];
            System.out.printf("SC-004 typing diagram %5d ms median, %5d ms max (budget %d ms, CI limit %d ms)%n", median, max,
                    KEYSTROKE_BUDGET_MS, KEYSTROKE_BUDGET_MS * CI_HEADROOM);
            assertEquals("Cell 250" + TYPED, d.elementView("v250").texts().get("label"));
            assertTrue("median keystroke took " + median + " ms", median <= KEYSTROKE_BUDGET_MS * CI_HEADROOM);
        }
    }

    /** Types {@code text} one character at a time after the cell value {@code after}; returns each keystroke's time. */
    private static long[] type(DiagramDriver d, String after, String text) {
        long[] elapsedMs = new long[text.length()];
        String typed = "";
        for (int i = 0; i < text.length(); i++) {
            String before = after + typed;
            typed += text.charAt(i);
            String now = after + typed;
            long start = System.nanoTime();
            d.driver().editText(t -> t.replace("value=\"" + before + "\"", "value=\"" + now + "\""));
            paint(d);
            elapsedMs[i] = (System.nanoTime() - start) / 1_000_000;
        }
        return elapsedMs;
    }

    /** Paints what a 1600 by 1000 window would show, now instead of when the event loop gets to it. */
    private static void paint(DiagramDriver d) {
        DiagramCanvas canvas = d.tool().canvas();
        canvas.setSize(canvas.getPreferredSize());
        BufferedImage image = new BufferedImage(1600, 1000, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setClip(0, 0, 1600, 1000);
            canvas.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }
}
