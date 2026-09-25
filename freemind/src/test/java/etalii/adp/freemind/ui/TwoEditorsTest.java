package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.TextVisualSyncTest.MAP;

import java.util.List;

import javax.swing.SwingConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import etalii.adp.core.AdpEditorProvider;
import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.testing.DesignerDriver;

/** Spec 001 FR-006 and the "same file in two editors" edge case: one document, one history. */
@RunWith(JUnit4.class)
public class TwoEditorsTest extends FileEditorManagerTestCase {

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void anEditInOneEditorShowsInTheOtherAndTheyShareOneUndoHistory() {
        try (var d = DesignerDriver.openText(myFixture, "twice.mm", MAP)) {
            EditorWindow window = manager.getCurrentWindow();
            assertNotNull(window);
            EditorWindow split = window.split(SwingConstants.VERTICAL, true, d.file(), false);
            try {
                assertNotNull("the editor splits", split);
                // The split opens its editor in the background.
                PlatformTestUtil.waitWithEventsDispatching("no designer in the split window", () -> composite(split) != null, 10);
                AdpEditorProvider.Composite second = composite(split);
                assertNotSame("a second editor, not the first one reused", d.composite(), second);
                MindMapDesigner first = LayoutTest.designer(d);
                MindMapDesigner other = (MindMapDesigner) second.designer();
                assertSame("both edit the one shared document", first.document(), other.document());

                // An edit in the first editor shows in the second.
                var rename = MindMapEdits.rename(first.model(), key("B"), "From the first");
                first.execute(rename.label(), rename.changes());
                d.settle();
                String afterRename = d.text();
                assertEquals(afterRename, other.document().getText());
                assertEquals("From the first", other.model().node(key("B")).text());
                assertEquals("From the first", other.viewOf(key("B")).text());
                assertTrue(second.isModified());

                // An edit in the second editor shows in the first.
                other.select(List.of(key("L")));
                var delete = MindMapEdits.delete(other.model(), List.of(key("L")));
                other.execute(delete.label(), delete.changes());
                d.settle();
                String afterDelete = other.document().getText();
                assertFalse(afterDelete, afterDelete.contains("ID=\"L\""));
                assertEquals(afterDelete, d.text());
                assertNull("the first editor erases the node deleted in the second", d.viewOf(key("L")));
                assertNull(other.viewOf(key("L")));
                assertEquals("each editor keeps its own selection", List.of(), d.selectedKeys());

                // One undo history: undo in the first reverts the second's edit in both.
                d.undo();
                assertEquals(afterRename, d.text());
                assertNotNull(other.viewOf(key("L")));
                assertNotNull(d.viewOf(key("L")));

                // And undo in the second reverts the first's edit in both.
                UndoManager undo = UndoManager.getInstance(getProject());
                assertTrue(undo.isUndoAvailable(second));
                undo.undo(second);
                d.settle();
                assertEquals(MAP, other.document().getText());
                assertEquals("B", text(d, key("B")));
                assertEquals("B", other.viewOf(key("B")).text());
                assertFalse(d.isModified());
            } finally {
                if (split != null) {
                    split.closeFile(d.file());
                }
                d.settle();
            }
            assertEquals("the first editor keeps working after the second closes", "B", LayoutTest.designer(d).model().node(key("B")).text());
        }
    }

    private static AdpEditorProvider.Composite composite(EditorWindow window) {
        for (EditorComposite composite : window.getAllComposites()) {
            for (FileEditor editor : composite.getAllEditors()) {
                if (editor instanceof AdpEditorProvider.Composite adp) {
                    return adp;
                }
            }
        }
        return null;
    }

    private static String text(DesignerDriver d, NodeKey key) {
        return d.viewOf(key).text();
    }
}
