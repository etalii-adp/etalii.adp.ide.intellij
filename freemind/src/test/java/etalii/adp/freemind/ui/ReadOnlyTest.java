package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.ADD_CHILD;
import static etalii.adp.freemind.ui.AddNodeTest.ADD_SIBLING;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.DeleteTest.DELETE;
import static etalii.adp.freemind.ui.FoldTest.TOGGLE_FOLD;
import static etalii.adp.freemind.ui.MoveNodeTest.INDENT;
import static etalii.adp.freemind.ui.MoveNodeTest.MOVE_DOWN;
import static etalii.adp.freemind.ui.MoveNodeTest.MOVE_UP;
import static etalii.adp.freemind.ui.MoveNodeTest.OUTDENT;
import static etalii.adp.freemind.ui.RenameTest.RENAME;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.ui.ReadOnlyBanner;
import etalii.adp.testing.ToolDriver;

/** Spec 001 FR-008; FR-010, US2-AS6: a read-only map is shown with the reason, every edit is disabled with it, and folding is view-only. */
@RunWith(JUnit4.class)
public class ReadOnlyTest extends FileEditorManagerTestCase {

    static final List<String> EDIT_ACTIONS = List.of(ADD_CHILD, ADD_SIBLING, RENAME, DELETE, MOVE_UP, MOVE_DOWN, INDENT, OUTDENT);

    @Override
    public void setUp() {
        super.setUp();
    }

    private ToolDriver open() {
        return ToolDriver.openText(myFixture, "readonly.mm", MAP);
    }

    @Test
    public void everyEditCommandIsDisabled() {
        try (var d = open()) {
            assertFalse(d.readOnlyBannerShown());
            d.setReadOnly(true);
            assertTrue("the banner states the reason", d.readOnlyBannerShown());
            assertFalse(d.tool().isEditable());
            assertNotNull("the map is still shown", d.viewOf(key("A2")));

            // A2 has siblings on both sides and a grandparent: on a writable file every action applies.
            for (String action : EDIT_ACTIONS) {
                d.select(key("A2"));
                assertFalse(action, d.presentation(action).isEnabled());
                assertEquals(action, ReadOnlyBanner.MESSAGE, d.presentation(action).getDescription());
                d.run(action);
                assertNull(action, d.inPlaceField());
            }
            d.press("DELETE").press("TAB").press("ENTER").press("F2");
            assertNull(d.inPlaceField());
            assertEquals(MAP, d.text());
            assertFalse(d.isModified());
            assertNull(d.undoLabel());
        }
    }

    @Test
    public void doubleClickDoesNotOpenAnEditor() {
        try (var d = open()) {
            d.setReadOnly(true);
            d.click(key("B"), 2, 0);
            assertNull(d.inPlaceField());
            assertEquals(MAP, d.text());
        }
    }

    @Test
    public void foldingIsViewOnly() {
        try (var d = open()) {
            d.setReadOnly(true);
            d.select(key("A"));
            assertTrue(d.presentation(TOGGLE_FOLD).isEnabled());
            d.run(TOGGLE_FOLD);
            assertNull(d.viewOf(key("A1")));
            assertEquals(MAP, d.text());
            assertFalse(d.isModified());
            assertNull(d.undoLabel());
        }
    }

    @Test
    public void theSameCommandsRunOnceWritableAgain() {
        try (var d = open()) {
            d.setReadOnly(true).setReadOnly(false);
            assertFalse(d.readOnlyBannerShown());
            d.select(key("A2"));
            for (String action : EDIT_ACTIONS) {
                assertTrue(action, d.presentation(action).isEnabled());
            }
            d.run(DELETE);
            assertEquals("Undo Delete Node", d.undoLabel());
        }
    }
}
