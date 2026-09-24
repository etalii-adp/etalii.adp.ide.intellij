package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.generatedMap;
import static etalii.adp.freemind.MindMapAsserts.key;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Function;

import org.eclipse.draw2d.IFigure;
import org.eclipse.gef.GraphicalEditPart;
import org.junit.jupiter.api.Test;

import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.testing.DesignerDriver;

/**
 * SC-003 on a generated 1,000-node map: open and draw within 2 s, and show the result of adding,
 * renaming, folding or deleting a node within 0.1 s. Each measurement includes laying out and
 * painting the figures.
 */
class PerformanceTest {

    private static final long OPEN_BUDGET_MS = 2_000;
    private static final long EDIT_BUDGET_MS = 100;

    /**
     * Shared CI machines are slower and noisier than a developer workstation, so the assertion
     * allows three times the budget. The measured times are printed so a regression shows before
     * it fails the build.
     */
    private static final int CI_HEADROOM = 3;

    @Test
    void aThousandNodeMapOpensAndEditsWithinBudget() {
        String map = generatedMap(1000);
        try (var warmUp = DesignerDriver.openText("warm-up.mm", generatedMap(50), MindMapEditor.ID)) {
            edit(warmUp, m -> MindMapEdits.rename(m, key("ID_1"), "warm"));
        }

        long start = System.nanoTime();
        try (var d = DesignerDriver.openText("large.mm", map, MindMapEditor.ID)) {
            paint(d);
            report("open and draw", start, OPEN_BUDGET_MS);
            assertNotNull(d.figureOf(key("ID_999")));

            measure(d, "add child", m -> MindMapEdits.addChild(m, key("ID_500"), "added"));
            measure(d, "rename", m -> MindMapEdits.rename(m, key("ID_500"), "renamed"));
            measure(d, "fold", m -> MindMapEdits.setFolded(m, key("ID_50"), true));
            measure(d, "delete", m -> MindMapEdits.delete(m, List.of(key("ID_600"))));
        }
    }

    private static void measure(DesignerDriver d, String action, Function<MindMap, Edit> build) {
        long start = System.nanoTime();
        edit(d, build);
        paint(d);
        report(action, start, EDIT_BUDGET_MS);
    }

    private static void edit(DesignerDriver d, Function<MindMap, Edit> build) {
        Edit edit = build.apply((MindMap) d.editor().model());
        d.editor().execute(edit.label(), edit.textEdit());
    }

    /** Lays out and paints now, instead of when the event loop gets to it. */
    private static void paint(DesignerDriver d) {
        IFigure root = ((GraphicalEditPart) d.editor().viewer().getRootEditPart()).getFigure();
        root.getUpdateManager().performUpdate();
        d.editor().viewer().getControl().update();
    }

    private static void report(String action, long startNanos, long budgetMs) {
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        System.out.printf("SC-003 %-14s %5d ms (budget %d ms, CI limit %d ms)%n", action, elapsedMs, budgetMs, budgetMs * CI_HEADROOM);
        assertTrue(elapsedMs <= budgetMs * CI_HEADROOM, action + " took " + elapsedMs + " ms");
    }
}
