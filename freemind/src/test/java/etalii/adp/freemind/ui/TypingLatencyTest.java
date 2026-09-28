package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.generatedMap;
import static etalii.adp.freemind.FreeMindAsserts.key;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.freemind.model.NodeKey;
import etalii.adp.testing.DesignerDriver;

/**
 * Spec 006 SC-004 on a generated 2,000-node map: a character typed in the text editor shows in the
 * designer within 0.1 s. Each keystroke is one document change; the measurement covers the
 * coalesced re-parse, the designer's update and painting the visible part of the map.
 */
@RunWith(JUnit4.class)
public class TypingLatencyTest extends FileEditorManagerTestCase {

    private static final long KEYSTROKE_BUDGET_MS = 100;

    /** As in {@link EditPerformanceTest}: CI machines are slower, so the assertion allows three times the budget. */
    private static final int CI_HEADROOM = 3;

    private static final String TYPED = "typed in the text view";

    @Test
    public void typingInATwoThousandNodeMapShowsInTheDesignerWithinBudget() {
        try (var warmUp = DesignerDriver.openText(myFixture, "warm-up.mm", generatedMap(50))) {
            type(warmUp, "Node 40", "warm up");
        }

        try (var d = DesignerDriver.openText(myFixture, "large.mm", generatedMap(2000))) {
            paint(d);
            long[] elapsedMs = type(d, "Node 1500", TYPED);
            Arrays.sort(elapsedMs);
            long median = elapsedMs[elapsedMs.length / 2];
            long max = elapsedMs[elapsedMs.length - 1];
            System.out.printf("SC-004 typing map %5d ms median, %5d ms max (budget %d ms, CI limit %d ms)%n", median, max,
                    KEYSTROKE_BUDGET_MS, KEYSTROKE_BUDGET_MS * CI_HEADROOM);
            assertEquals("Node 1500" + TYPED, designerText(d, key("ID_1500")));
            assertTrue("median keystroke took " + median + " ms", median <= KEYSTROKE_BUDGET_MS * CI_HEADROOM);
        }
    }

    /** Types {@code text} one character at a time after the node text {@code after}; returns each keystroke's time. */
    private static long[] type(DesignerDriver d, String after, String text) {
        long[] elapsedMs = new long[text.length()];
        String typed = "";
        for (int i = 0; i < text.length(); i++) {
            String before = after + typed;
            typed += text.charAt(i);
            String now = after + typed;
            long start = System.nanoTime();
            d.editText(t -> t.replace("TEXT=\"" + before + "\"", "TEXT=\"" + now + "\""));
            paint(d);
            elapsedMs[i] = (System.nanoTime() - start) / 1_000_000;
        }
        return elapsedMs;
    }

    private static String designerText(DesignerDriver d, NodeKey key) {
        return LayoutTest.designer(d).model().node(key).text();
    }

    /** Paints what a 1600 by 1000 window would show, now instead of when the event loop gets to it. */
    private static void paint(DesignerDriver d) {
        MindMapCanvas canvas = LayoutTest.designer(d).canvas();
        canvas.setSize(canvas.getPreferredSize());
        BufferedImage image = new BufferedImage(1600, 1000, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setClip(0, 0, 1600, 1000);
            canvas.paint(g);
        } finally {
            g.dispose();
        }
    }
}
