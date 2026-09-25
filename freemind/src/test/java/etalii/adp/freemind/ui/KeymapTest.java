package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.KeyStroke;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.testing.DesignerDriver;

/** FR-013, US2-AS7: every designer action is in the IDE's keymap, can be rebound, and stays out of text editors. */
@RunWith(JUnit4.class)
public class KeymapTest extends FileEditorManagerTestCase {

    /** The contract's default shortcuts, as {@link KeyStroke#getKeyStroke(String)} writes them. */
    static final Map<String, List<String>> DEFAULT_SHORTCUTS = new LinkedHashMap<>();

    static {
        DEFAULT_SHORTCUTS.put("etalii.adp.freemind.AddChild", List.of("INSERT", "TAB"));
        DEFAULT_SHORTCUTS.put("etalii.adp.freemind.AddSibling", List.of("ENTER"));
        DEFAULT_SHORTCUTS.put("etalii.adp.freemind.Rename", List.of("F2"));
        DEFAULT_SHORTCUTS.put("etalii.adp.freemind.Delete", List.of("DELETE"));
        DEFAULT_SHORTCUTS.put("etalii.adp.freemind.MoveUp", List.of("control UP"));
        DEFAULT_SHORTCUTS.put("etalii.adp.freemind.MoveDown", List.of("control DOWN"));
        DEFAULT_SHORTCUTS.put("etalii.adp.freemind.Indent", List.of("control RIGHT"));
        DEFAULT_SHORTCUTS.put("etalii.adp.freemind.Outdent", List.of("control LEFT"));
        DEFAULT_SHORTCUTS.put("etalii.adp.freemind.ToggleFold", List.of("SPACE"));
        DEFAULT_SHORTCUTS.put("etalii.adp.core.SelectAll", List.of("control A"));
        DEFAULT_SHORTCUTS.put("etalii.adp.core.ZoomIn", List.of("control EQUALS"));
        DEFAULT_SHORTCUTS.put("etalii.adp.core.ZoomOut", List.of("control MINUS"));
        DEFAULT_SHORTCUTS.put("etalii.adp.core.ZoomReset", List.of("control 0"));
    }

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void everyActionHasItsDefaultShortcut() {
        Keymap keymap = KeymapManager.getInstance().getKeymap(KeymapManager.DEFAULT_IDEA_KEYMAP);
        assertNotNull(keymap);
        DEFAULT_SHORTCUTS.forEach((id, strokes) -> {
            assertNotNull(id + " is registered", ActionManager.getInstance().getAction(id));
            Set<KeyStroke> expected = new HashSet<>();
            strokes.forEach(stroke -> expected.add(KeyStroke.getKeyStroke(stroke)));
            Set<KeyStroke> actual = new HashSet<>();
            for (Shortcut shortcut : keymap.getShortcuts(id)) {
                if (shortcut instanceof KeyboardShortcut keyboard && keyboard.getSecondKeyStroke() == null) {
                    actual.add(keyboard.getFirstKeyStroke());
                }
            }
            assertEquals(id, expected, actual);
        });
    }

    @Test
    public void aReboundShortcutRunsTheAction() {
        Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
        KeyboardShortcut rebound = new KeyboardShortcut(KeyStroke.getKeyStroke("alt shift R"), null);
        keymap.addShortcut("etalii.adp.freemind.Rename", rebound);
        try (var d = DesignerDriver.openText(myFixture, "keys.mm", MAP)) {
            d.select(key("B")).press("alt shift R");
            assertNotNull("the new shortcut opens the in-place editor", d.inPlaceField());
            d.typeInPlace("Rebound");
            assertEquals("Undo Rename Node", d.undoLabel());
        } finally {
            keymap.removeShortcut("etalii.adp.freemind.Rename", rebound);
        }
    }

    @Test
    public void textEditorsKeepTheirOwnMeaningForTheKeys() {
        try (var d = DesignerDriver.openText(myFixture, "keys.mm", MAP)) {
            d.select(key("A2"));
            TextEditor text = (TextEditor) d.composite().getTextEditor();
            List<AnAction> local = ActionUtil.getActions(text.getEditor().getContentComponent());
            for (String id : DEFAULT_SHORTCUTS.keySet()) {
                AnAction action = ActionManager.getInstance().getAction(id);
                assertFalse(id + " is not bound on the text editor", local.contains(action));
                AnActionEvent event = AnActionEvent.createEvent(d.textDataContext(), action.getTemplatePresentation().clone(), "AdpTest",
                        ActionUiKind.NONE, null);
                ActionUtil.updateAction(action, event);
                assertFalse(id + " is disabled in a text editor, so Tab, Enter, Space and Delete type there", event.getPresentation().isEnabled());
            }
            assertTrue("while the designer has focus the same action is enabled", d.presentation("etalii.adp.freemind.AddChild").isEnabled());
        }
    }
}
