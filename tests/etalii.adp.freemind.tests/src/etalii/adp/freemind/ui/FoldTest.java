package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import etalii.adp.freemind.ui.figures.NodeFigure;
import etalii.adp.testing.DesignerDriver;

/** FR-016, FR-023, FR-008, US1-AS3, R7: folding is a persisted edit, or view-only on a read-only file. */
class FoldTest {

    static final String TOGGLE_FOLD = "etalii.adp.freemind.toggleFold";

    static final String MAP = """
            <map version="1.0.1">
            <node ID="R" TEXT="Root">
            <node FOLDED="true" ID="A" POSITION="right" TEXT="A">
            <node ID="A1" TEXT="A1">
            <node ID="A11" TEXT="A11"/>
            </node>
            <node ID="A2" TEXT="A2"/>
            </node>
            <node ID="B" POSITION="left" TEXT="B">
            <node ID="B1" TEXT="B1"/>
            </node>
            <node ID="C" POSITION="left" TEXT="C">
            <node ID="C1" TEXT="C1"/>
            </node>
            </node>
            </map>
            """;

    @Test
    void branchesRecordedAsFoldedShowCollapsed() {
        try (var d = DesignerDriver.openText("fold.mm", MAP, MindMapEditor.ID)) {
            assertNotNull(d.figureOf(key("A")));
            assertNull(d.figureOf(key("A1")));
            assertNull(d.figureOf(key("A11")));
            assertTrue(((NodeFigure) d.figureOf(key("A"))).foldedMarkerShown());
            assertNotNull(d.figureOf(key("B1")));
            assertFalse(((NodeFigure) d.figureOf(key("B"))).foldedMarkerShown());
        }
    }

    @Test
    void unfoldingIsALabelledUndoableEdit() {
        try (var d = DesignerDriver.openText("fold.mm", MAP, MindMapEditor.ID)) {
            d.select(key("A")).run(TOGGLE_FOLD);
            assertNotNull(d.figureOf(key("A1")));
            assertNotNull(d.figureOf(key("A11")));
            assertFalse(d.text().contains("FOLDED"));
            assertEquals("Unfold Branch", d.undoLabel());
            assertTrue(d.isDirty());
            assertEquals(List.of(key("A")), d.selectedModels());

            d.undo();
            assertEquals(MAP, d.text());
            assertNull(d.figureOf(key("A1")));
            assertFalse(d.isDirty());

            d.redo();
            assertNotNull(d.figureOf(key("A1")));
        }
    }

    @Test
    void foldingWritesFoldedAndHidesTheBranch() {
        try (var d = DesignerDriver.openText("fold.mm", MAP, MindMapEditor.ID)) {
            d.select(key("B")).run(TOGGLE_FOLD);
            assertNull(d.figureOf(key("B1")));
            assertTrue(d.text().contains("<node FOLDED=\"true\" ID=\"B\" POSITION=\"left\" TEXT=\"B\">"), d.text());
            assertEquals("Fold Branch", d.undoLabel());
        }
    }

    @Test
    void severalSelectedBranchesFoldAsOneEdit() {
        try (var d = DesignerDriver.openText("fold.mm", MAP, MindMapEditor.ID)) {
            d.select(key("B"), key("C")).run(TOGGLE_FOLD);
            assertNull(d.figureOf(key("B1")));
            assertNull(d.figureOf(key("C1")));
            assertEquals("Fold Branch", d.undoLabel());

            d.undo();
            assertEquals(MAP, d.text());
            assertNotNull(d.figureOf(key("B1")));
            assertNotNull(d.figureOf(key("C1")));
        }
    }

    @Test
    void aLeafCannotBeFolded() {
        try (var d = DesignerDriver.openText("fold.mm", MAP, MindMapEditor.ID)) {
            d.select(key("B1"));
            assertThrows(IllegalStateException.class, () -> d.run(TOGGLE_FOLD));
            assertEquals(MAP, d.text());
        }
    }

    @Test
    void onAReadOnlyFileFoldingIsViewOnly() {
        try (var d = DesignerDriver.openText("fold.mm", MAP, MindMapEditor.ID)) {
            d.setReadOnly(true);
            assertFalse(d.editor().isEditable());

            d.select(key("A")).run(TOGGLE_FOLD);
            assertNotNull(d.figureOf(key("A1")));
            d.select(key("B")).run(TOGGLE_FOLD);
            assertNull(d.figureOf(key("B1")));

            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());
            assertNull(d.undoLabel());

            d.select(key("A")).run(TOGGLE_FOLD);
            assertNull(d.figureOf(key("A1")));
        }
    }

    @Test
    void revealExpandsCollapsedAncestorsWithoutAnEdit() {
        try (var d = DesignerDriver.openText("fold.mm", MAP, MindMapEditor.ID)) {
            ((MindMapEditor) d.editor()).reveal(key("A11"));
            d.settle();
            assertNotNull(d.figureOf(key("A11")));
            assertEquals(List.of(key("A11")), d.selectedModels());
            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());
            assertNull(d.undoLabel());

            // The branch now shows unfolded though the file says folded: folding it again is view-only.
            d.select(key("A")).run(TOGGLE_FOLD);
            assertNull(d.figureOf(key("A1")));
            assertEquals(MAP, d.text());
        }
    }
}
