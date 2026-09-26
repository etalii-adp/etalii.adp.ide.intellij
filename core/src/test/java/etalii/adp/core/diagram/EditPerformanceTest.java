package etalii.adp.core.diagram;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.diagram.sample.SampleFiles;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.testing.DiagramDriver;

/**
 * T058, SC-005, edit half: on a generated 1,000-element, 1,500-connection diagram, a single move
 * dragged on the canvas is written, read again, laid out, routed and painted within 100 ms, the
 * median of five runs after a warm-up.
 */
@RunWith(JUnit4.class)
public class EditPerformanceTest extends FileEditorManagerTestCase {

    private static final long EDIT_BUDGET_MS = 100;

    /** As in {@link OpenPerformanceTest}: shared CI machines are slower and noisier, so the assertion allows three times the budget. */
    private static final int CI_HEADROOM = 3;

    @Override
    public void setUp() {
        super.setUp();
        SampleProvider.register(getTestRootDisposable());
    }

    @Test
    public void aSingleMoveOnAThousandElementDiagramShowsWithinBudget() {
        try (var d = DiagramDriver.openText(myFixture, "large.adpsample", SampleFiles.generate(1000, 1500, 42))) {
            paint(d);
            move(d, 0);
            move(d, 1);

            List<Long> times = new ArrayList<>();
            for (int run = 0; run < 5; run++) {
                String before = d.driver().text();
                long start = System.nanoTime();
                move(d, run);
                times.add((System.nanoTime() - start) / 1_000_000);
                assertFalse("the move was written", before.equals(d.driver().text()));
            }
            long median = times.stream().sorted().toList().get(2);
            System.out.printf("SC-005 move and paint %s: median %d ms (budget %d ms, CI limit %d ms)%n", times, median, EDIT_BUDGET_MS,
                    EDIT_BUDGET_MS * CI_HEADROOM);
            assertTrue("a move took " + median + " ms", median <= EDIT_BUDGET_MS * CI_HEADROOM);
        }
    }

    /** Drag t500 down and back up by turns, then paint what a window shows. */
    private static void move(DiagramDriver d, int run) {
        d.moveBy(0, run % 2 == 0 ? 20 : -20, "t500");
        paint(d);
    }

    /** Paints what a 1600 by 1000 window would show, now instead of when the event loop gets to it. */
    private static void paint(DiagramDriver d) {
        DiagramCanvas canvas = d.designer().canvas();
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
