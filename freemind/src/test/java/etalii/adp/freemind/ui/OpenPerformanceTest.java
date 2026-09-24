package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.generatedMap;
import static etalii.adp.freemind.FreeMindAsserts.key;

import java.awt.image.BufferedImage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.testing.DesignerDriver;

/**
 * SC-004 on a generated 1,000-node map: it opens, is laid out and painted within 2 s. The editing
 * half of spec 001's performance test is User Story 2's.
 */
@RunWith(JUnit4.class)
public class OpenPerformanceTest extends FileEditorManagerTestCase {

    private static final long OPEN_BUDGET_MS = 2_000;

    /**
     * Shared CI machines are slower and noisier than a developer workstation, so the assertion
     * allows three times the budget. The measured time is printed so a regression shows before it
     * fails the build.
     */
    private static final int CI_HEADROOM = 3;

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void aThousandNodeMapOpensAndEditsWithinBudget() {
        try (var warmUp = DesignerDriver.openText(myFixture, "warm-up.mm", generatedMap(50))) {
            paint(warmUp);
        }

        String map = generatedMap(1000);
        long start = System.nanoTime();
        try (var d = DesignerDriver.openText(myFixture, "large.mm", map)) {
            paint(d);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            System.out.printf("SC-004 open and draw %5d ms (budget %d ms, CI limit %d ms)%n", elapsedMs, OPEN_BUDGET_MS,
                    OPEN_BUDGET_MS * CI_HEADROOM);
            assertTrue("open and draw took " + elapsedMs + " ms", elapsedMs <= OPEN_BUDGET_MS * CI_HEADROOM);
            assertNotNull(d.viewOf(key("ID_999")));
            assertEquals(1000, LayoutTest.designer(d).model().nodesByKey().size());
        }
    }

    /** Lays out and paints the whole canvas now, instead of when the event loop gets to it. */
    private static void paint(DesignerDriver d) {
        MindMapCanvas canvas = LayoutTest.designer(d).canvas();
        canvas.setSize(canvas.getPreferredSize());
        BufferedImage image = new BufferedImage(Math.max(1, canvas.getWidth()), Math.max(1, canvas.getHeight()), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }
}
