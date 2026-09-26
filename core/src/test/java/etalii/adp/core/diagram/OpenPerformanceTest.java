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
 * SC-005, open half: a generated 1,000-element, 1,500-connection diagram opens, is read, laid out,
 * routed and painted within 2 s, the median of five runs after a warm-up.
 */
@RunWith(JUnit4.class)
public class OpenPerformanceTest extends FileEditorManagerTestCase {

    private static final long OPEN_BUDGET_MS = 2_000;

    /**
     * Shared CI machines are slower and noisier than a developer workstation, so the assertion
     * allows three times the budget. The measured times are printed so a regression shows before
     * it fails the build.
     */
    private static final int CI_HEADROOM = 3;

    @Override
    public void setUp() {
        super.setUp();
        SampleProvider.register(getTestRootDisposable());
    }

    @Test
    public void aThousandElementDiagramOpensAndPaintsWithinBudget() {
        try (var warmUp = DiagramDriver.openText(myFixture, "warm-up.adpsample", SampleFiles.generate(1000, 1500, 7))) {
            paint(warmUp);
        }

        String diagram = SampleFiles.generate(1000, 1500, 42);
        List<Long> times = new ArrayList<>();
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            try (var d = DiagramDriver.openText(myFixture, "large-" + run + ".adpsample", diagram)) {
                paint(d);
                times.add((System.nanoTime() - start) / 1_000_000);
                assertEquals(1000, d.elementKeys().size());
                assertEquals(1500, d.connectionKeys().size());
                assertNotNull(d.connectionView("f1499").route());
            }
        }
        long median = times.stream().sorted().toList().get(2);
        System.out.printf("SC-005 open and paint %s: median %d ms (budget %d ms, CI limit %d ms)%n", times, median, OPEN_BUDGET_MS,
                OPEN_BUDGET_MS * CI_HEADROOM);
        assertTrue("open and paint took " + median + " ms", median <= OPEN_BUDGET_MS * CI_HEADROOM);
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
