package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.ADD_CHILD;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.AddNodeTest.cancelInPlace;
import static etalii.adp.freemind.ui.DeleteTest.DELETE;
import static etalii.adp.freemind.ui.RenameTest.RENAME;
import static etalii.adp.freemind.ui.TextVisualSyncTest.node;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.testing.ToolDriver;
import etalii.adp.testing.Layout;

/** Spec 001 FR-004, FR-006, US2-AS4: one undo history covers the diagram and the text, in the order the edits were made. */
@RunWith(JUnit4.class)
public class InterleavedUndoTest extends FileEditorManagerTestCase {

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void undoRevertsVisualAndTextEditsInReverseOrderOnEitherPage() {
        try (var d = ToolDriver.openText(myFixture, "interleaved.mm", MAP)) {
            List<String> texts = new ArrayList<>();
            texts.add(d.text());

            // 1: diagram
            d.select(key("B")).run(ADD_CHILD);
            cancelInPlace(d);
            texts.add(d.text());
            // 2: text
            d.showLayout(Layout.TEXT);
            d.editText(text -> text.replace("TEXT=\"A1\"", "TEXT=\"Typed A1\""));
            texts.add(d.text());
            // 3: diagram
            d.showLayout(Layout.TOOL);
            d.select(key("L")).run(RENAME).typeInPlace("Ell");
            texts.add(d.text());
            // 4: text
            d.showLayout(Layout.TEXT);
            d.editText(text -> text.replace("TEXT=\"A3\"", "TEXT=\"Typed A3\""));
            texts.add(d.text());
            // 5: diagram
            d.showLayout(Layout.TOOL);
            d.select(key("A2")).run(DELETE);
            texts.add(d.text());

            for (int i = 1; i < texts.size(); i++) {
                assertFalse("edit " + i + " changed the text", texts.get(i - 1).equals(texts.get(i)));
            }

            // Undo 5 and 4 from the text, 3 and 2 from the diagram, 1 from the text.
            Layout[] undoFrom = { Layout.TEXT, Layout.TEXT, Layout.TOOL, Layout.TOOL, Layout.TEXT };
            for (int step = 0; step < undoFrom.length; step++) {
                int expected = texts.size() - 2 - step;
                d.showLayout(undoFrom[step]);
                if (undoFrom[step] == Layout.TEXT) {
                    undoInText(d);
                } else {
                    d.undo();
                }
                assertEquals("after undo " + (step + 1) + " in the " + undoFrom[step] + " layout", texts.get(expected), d.text());
                assertEquals("undo does not switch layouts", undoFrom[step], d.layout());
            }

            assertEquals(MAP, d.text());
            assertFalse("undoing everything clears the modified marker", d.isModified());
            assertNull(d.undoLabel());
            d.showLayout(Layout.TOOL);
            assertEquals("A1", d.viewOf(key("A1")).text());
            assertEquals("L", d.viewOf(key("L")).text());
            assertNotNull(d.viewOf(key("A2")));
            assertEquals(0, node(d, key("B")).children().size());
            assertEquals(MAP, new String(d.savedBytes(), UTF_8));
        }
    }

    /** The IDE's own Undo with the text editor focused. */
    static void undoInText(ToolDriver d) {
        AnAction undo = ActionManager.getInstance().getAction("$Undo");
        AnActionEvent event = AnActionEvent.createEvent(d.textDataContext(), undo.getTemplatePresentation().clone(), "AdpTest",
                ActionUiKind.NONE, null);
        ActionUtil.updateAction(undo, event);
        assertTrue("Undo is available in the text editor", event.getPresentation().isEnabled());
        ActionUtil.performActionDumbAwareWithCallbacks(undo, event);
        d.settle();
    }

    @Test
    public void theVisualPageFollowsEachUndoOfATextEdit() {
        try (var d = ToolDriver.openText(myFixture, "interleaved.mm", MAP)) {
            d.select(key("B")).run(RENAME).typeInPlace("Visual B");
            d.showLayout(Layout.SPLIT);
            d.editText(text -> text.replace("TEXT=\"Visual B\"", "TEXT=\"Typed B\""));
            assertEquals("Typed B", d.viewOf(key("B")).text());

            d.showLayout(Layout.TOOL);
            d.undo();
            assertEquals("the typing is undone first", "Visual B", d.viewOf(key("B")).text());
            assertEquals("Undo Rename Node", d.undoLabel());

            d.undo();
            assertEquals("B", d.viewOf(key("B")).text());
            assertEquals(MAP, d.text());
            assertFalse(d.isModified());

            d.redo();
            assertEquals("Visual B", d.viewOf(key("B")).text());
            d.redo();
            assertEquals("Typed B", d.viewOf(key("B")).text());
            assertTrue(d.isModified());
        }
    }
}
