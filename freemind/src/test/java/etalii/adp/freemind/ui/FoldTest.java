package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.testing.DesignerDriver;

/** Spec 001 FR-016, FR-023, US1-AS3: folding is a saved edit, or view-only on a read-only file. */
@RunWith(JUnit4.class)
public class FoldTest extends FileEditorManagerTestCase {

    static final String TOGGLE_FOLD = "etalii.adp.freemind.ToggleFold";

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

    @Override
    public void setUp() {
        super.setUp();
    }

    private DesignerDriver open() {
        return DesignerDriver.openText(myFixture, "fold.mm", MAP);
    }

    @Test
    public void branchesRecordedAsFoldedShowCollapsed() {
        try (var d = open()) {
            assertNotNull(d.viewOf(key("A")));
            assertNull(d.viewOf(key("A1")));
            assertNull(d.viewOf(key("A11")));
            assertTrue(d.viewOf(key("A")).folded());
            assertNotNull(d.viewOf(key("B1")));
            assertFalse(d.viewOf(key("B")).folded());
        }
    }

    @Test
    public void unfoldingIsALabelledUndoableEdit() {
        try (var d = open()) {
            d.select(key("A")).press("SPACE");
            assertNotNull(d.viewOf(key("A1")));
            assertNotNull(d.viewOf(key("A11")));
            assertFalse(d.text().contains("FOLDED"));
            assertEquals("Undo Unfold Branch", d.undoLabel());
            assertTrue(d.isModified());
            assertEquals(List.of(key("A")), d.selectedKeys());

            d.undo();
            assertEquals(MAP, d.text());
            assertNull(d.viewOf(key("A1")));
            assertFalse(d.isModified());

            d.redo();
            assertNotNull(d.viewOf(key("A1")));
        }
    }

    @Test
    public void foldingWritesFoldedAndHidesTheBranch() {
        try (var d = open()) {
            d.select(key("B")).press("SPACE");
            assertNull(d.viewOf(key("B1")));
            assertTrue(d.text(), d.text().contains("<node FOLDED=\"true\" ID=\"B\" POSITION=\"left\" TEXT=\"B\">"));
            assertEquals("Undo Fold Branch", d.undoLabel());
        }
    }

    @Test
    public void severalSelectedBranchesFoldAsOneEdit() {
        try (var d = open()) {
            d.select(key("B"), key("C")).run(TOGGLE_FOLD);
            assertNull(d.viewOf(key("B1")));
            assertNull(d.viewOf(key("C1")));
            assertEquals("Undo Fold Branch", d.undoLabel());

            d.undo();
            assertEquals(MAP, d.text());
            assertNotNull(d.viewOf(key("B1")));
            assertNotNull(d.viewOf(key("C1")));
        }
    }

    @Test
    public void aLeafCannotBeFolded() {
        try (var d = open()) {
            d.select(key("B1"));
            assertFalse(d.presentation(TOGGLE_FOLD).isEnabled());
            d.run(TOGGLE_FOLD);
            assertEquals(MAP, d.text());
        }
    }

    @Test
    public void onAReadOnlyFileFoldingIsViewOnly() {
        try (var d = open()) {
            d.setReadOnly(true);
            assertFalse(d.designer().isEditable());

            d.select(key("A"));
            assertTrue("folding stays available on a read-only file", d.presentation(TOGGLE_FOLD).isEnabled());
            d.press("SPACE");
            assertNotNull(d.viewOf(key("A1")));
            d.select(key("B")).run(TOGGLE_FOLD);
            assertNull(d.viewOf(key("B1")));

            assertEquals(MAP, d.text());
            assertFalse(d.isModified());
            assertNull(d.undoLabel());

            d.select(key("A")).run(TOGGLE_FOLD);
            assertNull(d.viewOf(key("A1")));
        }
    }

    @Test
    public void revealExpandsCollapsedAncestorsWithoutAnEdit() {
        try (var d = open()) {
            d.designer().reveal(key("A11"));
            d.settle();
            assertNotNull(d.viewOf(key("A11")));
            assertEquals(List.of(key("A11")), d.selectedKeys());
            assertEquals(MAP, d.text());
            assertFalse(d.isModified());
            assertNull(d.undoLabel());

            // The branch now shows unfolded though the file says folded: folding it again is view-only.
            d.select(key("A")).run(TOGGLE_FOLD);
            assertNull(d.viewOf(key("A1")));
            assertEquals(MAP, d.text());
        }
    }
}
