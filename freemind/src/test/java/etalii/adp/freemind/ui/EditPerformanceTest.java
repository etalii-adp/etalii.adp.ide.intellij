package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.generatedMap;
import static etalii.adp.freemind.FreeMindAsserts.key;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.testing.DesignerDriver;

/**
 * SC-004 (spec 001 SC-003, edit half) on a generated 1,000-node map: the result of adding,
 * renaming, folding or deleting a node shows within 0.1 s. Each measurement runs the edit as one
 * command, re-parses, lays out and paints the visible part of the map.
 */
@RunWith(JUnit4.class)
public class EditPerformanceTest extends FileEditorManagerTestCase {

    private static final long EDIT_BUDGET_MS = 100;

    /**
     * Shared CI machines are slower and noisier than a developer workstation, so the assertion
     * allows three times the budget. The measured times are printed so a regression shows before
     * it fails the build.
     */
    private static final int CI_HEADROOM = 3;

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void aThousandNodeMapOpensAndEditsWithinBudget() {
        try (var warmUp = DesignerDriver.openText(myFixture, "warm-up.mm", generatedMap(50))) {
            for (int i = 0; i < 5; i++) {
                edit(warmUp, m -> MindMapEdits.rename(m, key("ID_1"), "warm " + System.nanoTime()));
                paint(warmUp);
            }
        }

        try (var d = DesignerDriver.openText(myFixture, "large.mm", generatedMap(1000))) {
            paint(d);
            assertNotNull(d.viewOf(key("ID_999")));

            measure(d, "add child", m -> MindMapEdits.addChild(m, key("ID_500"), "added"));
            measure(d, "rename", m -> MindMapEdits.rename(m, key("ID_500"), "renamed"));
            measure(d, "fold", m -> MindMapEdits.setFolded(m, key("ID_50"), true));
            measure(d, "delete", m -> MindMapEdits.delete(m, List.of(key("ID_600"))));
            assertNull(d.viewOf(key("ID_600")));
        }
    }

    private static void measure(DesignerDriver d, String action, Function<MindMap, Edit> build) {
        long start = System.nanoTime();
        edit(d, build);
        paint(d);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("SC-004 %-10s %5d ms (budget %d ms, CI limit %d ms)%n", action, elapsedMs, EDIT_BUDGET_MS, EDIT_BUDGET_MS * CI_HEADROOM);
        assertTrue(action + " took " + elapsedMs + " ms", elapsedMs <= EDIT_BUDGET_MS * CI_HEADROOM);
    }

    private static void edit(DesignerDriver d, Function<MindMap, Edit> build) {
        MindMapDesigner designer = LayoutTest.designer(d);
        Edit edit = build.apply(designer.model());
        designer.execute(edit.label(), edit.changes());
        d.settle();
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
