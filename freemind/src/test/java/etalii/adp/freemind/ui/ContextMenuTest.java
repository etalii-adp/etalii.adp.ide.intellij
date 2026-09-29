package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;

import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.ui.PopupHandler;

import etalii.adp.testing.ToolDriver;

/** Spec 001 FR-024, FR-012: every editing action is in the diagram's context menu, in the contract's order. */
@RunWith(JUnit4.class)
public class ContextMenuTest extends FileEditorManagerTestCase {

    static final String POPUP = MindMapFileEditor.POPUP_GROUP;
    static final String SEPARATOR = "---";

    /** The popup group as contracts/plugin-contributions.md lists it. */
    static final List<String> CONTRACT_ORDER = List.of("etalii.adp.freemind.AddChild", "etalii.adp.freemind.AddSibling", SEPARATOR,
            "etalii.adp.freemind.Rename", "etalii.adp.freemind.Delete", SEPARATOR, "etalii.adp.freemind.MoveUp",
            "etalii.adp.freemind.MoveDown", "etalii.adp.freemind.Indent", "etalii.adp.freemind.Outdent", SEPARATOR,
            "etalii.adp.freemind.ToggleFold", "etalii.adp.core.ZoomIn", "etalii.adp.core.ZoomOut", "etalii.adp.core.ZoomReset");

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void theContextMenuListsEveryCommandInTableOrder() {
        try (var d = ToolDriver.openText(myFixture, "menu.mm", MAP)) {
            assertEquals(CONTRACT_ORDER, contents());
            List<String> texts = new ArrayList<>();
            for (String id : CONTRACT_ORDER) {
                texts.add(SEPARATOR.equals(id) ? SEPARATOR : ActionManager.getInstance().getAction(id).getTemplatePresentation().getText());
            }
            assertEquals(List.of("Add Child Node", "Add Sibling Node", SEPARATOR, "Rename Node", "Delete Node", SEPARATOR, "Move Node Up",
                    "Move Node Down", "Move Under Previous Sibling", "Move Up a Level", SEPARATOR, "Fold / Unfold Branch", "Zoom In", "Zoom Out",
                    "Actual Size"), texts);

            MindMapCanvas canvas = LayoutTest.tool(d).canvas();
            boolean popup = false;
            for (MouseListener listener : canvas.getMouseListeners()) {
                popup |= listener instanceof PopupHandler;
            }
            assertTrue("a right-click on the canvas shows the group", popup);
        }
    }

    @Test
    public void menuItemsFollowTheSelection() {
        try (var d = ToolDriver.openText(myFixture, "menu.mm", MAP)) {
            d.select(key("R"));
            Map<String, Boolean> enabled = enablement(d);
            assertTrue(enabled.get("Add Child Node"));
            assertFalse("the root has no siblings", enabled.get("Add Sibling Node"));
            assertFalse("the root cannot be deleted", enabled.get("Delete Node"));
            assertFalse(enabled.get("Move Node Up"));
            assertTrue(enabled.get("Fold / Unfold Branch"));
            assertTrue(enabled.get("Zoom In"));

            d.select(key("A2"));
            enabled = enablement(d);
            assertTrue(enabled.get("Add Sibling Node"));
            assertTrue(enabled.get("Delete Node"));
            assertTrue(enabled.get("Move Node Up"));
            assertTrue(enabled.get("Move Up a Level"));
            assertFalse("a leaf has nothing to fold", enabled.get("Fold / Unfold Branch"));

            d.select();
            for (String id : CONTRACT_ORDER) {
                if (id.startsWith("etalii.adp.freemind.")) {
                    assertFalse("nothing selected: " + id, d.presentation(id).isEnabled());
                }
            }
        }
    }

    @Test
    public void everyCommandHasItsKeyInTheToolContext() {
        try (var d = ToolDriver.openText(myFixture, "menu.mm", MAP)) {
            List<String> registered = new ArrayList<>();
            for (AnAction action : ActionUtil.getActions(LayoutTest.tool(d).canvas())) {
                registered.add(ActionManager.getInstance().getId(action));
            }
            for (String id : CONTRACT_ORDER) {
                if (!SEPARATOR.equals(id)) {
                    assertTrue(id + " has its shortcut on the canvas", registered.contains(id));
                    assertTrue(id + " has a shortcut", ActionManager.getInstance().getAction(id).getShortcutSet().getShortcuts().length > 0);
                }
            }
        }
    }

    /** The group's children, by action id, with separators as {@link #SEPARATOR}. */
    private static List<String> contents() {
        ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction(POPUP);
        assertNotNull(POPUP + " is registered", group);
        List<String> ids = new ArrayList<>();
        for (AnAction child : group.getChildren(null)) {
            ids.add(child instanceof Separator ? SEPARATOR : ActionManager.getInstance().getId(child));
        }
        return ids;
    }

    /** Each menu item's enablement after an update in the diagram's context, by its text. */
    private static Map<String, Boolean> enablement(ToolDriver d) {
        Map<String, Boolean> enabled = new HashMap<>();
        for (String id : CONTRACT_ORDER) {
            if (!SEPARATOR.equals(id)) {
                enabled.put(ActionManager.getInstance().getAction(id).getTemplatePresentation().getText(), d.presentation(id).isEnabled());
            }
        }
        return enabled;
    }
}
