package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.AddNodeTest.cancelInPlace;
import static etalii.adp.freemind.ui.TextVisualSyncTest.node;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.testing.ToolDriver;

/** Spec 001 FR-020, US2-AS2: rename in place by F2 and by double-click; a rich node asks first. */
@RunWith(JUnit4.class)
public class RenameTest extends FileEditorManagerTestCase {

    static final String RENAME = "etalii.adp.freemind.Rename";

    static final String RICH_MAP = """
            <map version="1.0.1">
            <node CREATED="1000" ID="R" MODIFIED="1000" TEXT="Root">
            <node CREATED="1000" ID="H" MODIFIED="1000" POSITION="right"><richcontent TYPE="NODE"><html>
              <head>
              </head>
              <body>
                <p>
                  Hello <b>rich</b> world
                </p>
              </body>
            </html>
            </richcontent>
            <richcontent TYPE="NOTE"><html><head></head><body><p>Keep this note</p></body></html></richcontent>
            </node>
            </node>
            </map>
            """;

    private final List<String> asked = new ArrayList<>();

    @Override
    public void setUp() {
        super.setUp();
    }

    /** Answer the IDE's questions with {@code answer}, remembering each one asked. */
    private void answer(int answer) {
        TestDialogManager.setTestDialog(message -> {
            asked.add(message);
            return answer;
        }, getTestRootDisposable());
    }

    @Test
    public void renameByF2() {
        try (var d = ToolDriver.openText(myFixture, "rename.mm", MAP)) {
            d.select(key("B")).press("F2");
            assertNotNull("F2 opens the in-place editor", d.inPlaceField());
            assertEquals("the editor starts from the node's text", "B", d.inPlaceField().getText());
            d.typeInPlace("Bee");
            assertEquals("Bee", node(d, key("B")).text());
            assertEquals("Bee", d.viewOf(key("B")).text());
            assertFalse("MODIFIED is updated (FR-011)", Long.valueOf(1000).equals(node(d, key("B")).modified()));
            assertEquals("Undo Rename Node", d.undoLabel());
            assertEquals(List.of(key("B")), d.selectedKeys());
            assertTrue(d.isModified());
            d.undo();
            assertEquals(MAP, d.text());
            assertFalse(d.isModified());
        }
    }

    @Test
    public void renameByDoubleClick() {
        try (var d = ToolDriver.openText(myFixture, "rename.mm", MAP)) {
            d.click(key("A2"), 2, 0);
            assertEquals(List.of(key("A2")), d.selectedKeys());
            assertNotNull("a double-click opens the in-place editor", d.inPlaceField());
            d.typeInPlace("Second");
            assertEquals("Second", node(d, key("A2")).text());
            assertEquals("Undo Rename Node", d.undoLabel());
        }
    }

    @Test
    public void escapeCancelsTheRename() {
        try (var d = ToolDriver.openText(myFixture, "rename.mm", MAP)) {
            d.select(key("B")).run(RENAME);
            d.inPlaceField().setText("Not kept");
            cancelInPlace(d);
            assertNull(d.inPlaceField());
            assertEquals(MAP, d.text());
            assertNull(d.undoLabel());
        }
    }

    @Test
    public void unchangedTextIsNoEdit() {
        try (var d = ToolDriver.openText(myFixture, "rename.mm", MAP)) {
            d.select(key("B")).run(RENAME).typeInPlace("B");
            assertEquals(MAP, d.text());
            assertFalse(d.isModified());
            assertNull(d.undoLabel());
        }
    }

    @Test
    public void renameNeedsExactlyOneNode() {
        try (var d = ToolDriver.openText(myFixture, "rename.mm", MAP)) {
            d.select(key("A"), key("B"));
            assertFalse(d.presentation(RENAME).isEnabled());
            d.run(RENAME);
            assertNull(d.inPlaceField());
            d.select();
            assertFalse(d.presentation(RENAME).isEnabled());
        }
    }

    @Test
    public void aRichNodeWarnsAndCancelChangesNothing() {
        answer(Messages.CANCEL);
        try (var d = ToolDriver.openText(myFixture, "rich.mm", RICH_MAP)) {
            d.select(key("H")).run(RENAME);
            assertEquals("Hello rich world", d.inPlaceField().getText());
            d.typeInPlace("Plain");
            assertEquals("the user is asked before the edit", 1, asked.size());
            assertTrue(asked.get(0), asked.get(0).contains("plain text"));
            assertEquals(RICH_MAP, d.text());
            assertFalse(d.isModified());
            assertNull(d.undoLabel());
        }
    }

    @Test
    public void aRichNodeConfirmedBecomesPlainText() {
        answer(Messages.OK);
        try (var d = ToolDriver.openText(myFixture, "rich.mm", RICH_MAP)) {
            d.select(key("H")).run(RENAME).typeInPlace("Plain");
            assertEquals(1, asked.size());
            assertFalse(d.text(), d.text().contains("richcontent TYPE=\"NODE\""));
            assertTrue(d.text(), d.text().contains("TEXT=\"Plain\""));
            assertTrue("the note stays", d.text().contains("<p>Keep this note</p>"));
            assertFalse(node(d, key("H")).rich());
            assertEquals("Plain", node(d, key("H")).text());
            assertEquals("Undo Rename Node", d.undoLabel());
            d.undo();
            assertEquals(RICH_MAP, d.text());
        }
    }

    @Test
    public void aPlainNodeIsNotWarned() {
        answer(Messages.CANCEL);
        try (var d = ToolDriver.openText(myFixture, "rename.mm", MAP)) {
            d.select(key("L")).run(RENAME).typeInPlace("Left");
            assertTrue(asked.isEmpty());
            assertEquals("Left", node(d, key("L")).text());
        }
    }
}
