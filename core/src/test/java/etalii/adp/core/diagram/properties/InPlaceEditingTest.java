package etalii.adp.core.diagram.properties;

import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.util.ui.JBFont;

import etalii.adp.core.diagram.sample.SampleFiles;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.diagram.toolbox.ToolboxTest;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.testing.DiagramDriver;

/**
 * T070, US3-2 and FR-019: a double-click on an editable text slot or connection label edits it in
 * place, and only there. Enter commits a single-line editor, Ctrl+Enter a multi-line one; Escape
 * cancels. F2 opens the selected item's first editable text. An item that disappears while it is
 * edited gets nothing applied. An edit in place and one in the panel take the same path and agree.
 */
@RunWith(JUnit4.class)
public class InPlaceEditingTest extends FileEditorManagerTestCase {

    /** A flow long enough that its three labels stand apart: an editable middle, read-only ends. */
    private static final String LABELLED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sample>
              <box id="a" type="task" x="40" y="40" owner="Ann">Check payment</box>
              <box id="d" type="decision" x="640" y="40">Paid?</box>
              <link id="f1" type="flow" from="a" fromAnchor="out" to="d" toAnchor="in" tag="t1" note="always">check</link>
            </sample>
            """;

    @Override
    public void setUp() {
        super.setUp();
        SampleProvider.register(getTestRootDisposable());
        ToolboxTest.install(getProject(), getTestRootDisposable());
        PropertyPanelTest.install(getProject(), getTestRootDisposable());
    }

    private DiagramDriver open() {
        return DiagramDriver.open(myFixture, SampleFiles.directory().resolve("two-tasks.adpsample"));
    }

    private DiagramDriver labelled() {
        return DiagramDriver.openText(myFixture, "labelled.adpsample", LABELLED);
    }

    private static JTextComponent editor(DiagramDriver d) {
        return InPlaceEditor.component(d.designer().canvas());
    }

    /** A key press on the in-place editor: an action registered for it on the component, else its key binding. */
    static void press(JComponent component, String keystroke) {
        KeyStroke stroke = KeyStroke.getKeyStroke(keystroke);
        assertNotNull(keystroke, stroke);
        for (AnAction action : ActionUtil.getActions(component)) {
            for (Shortcut shortcut : action.getShortcutSet().getShortcuts()) {
                if (shortcut instanceof KeyboardShortcut keyboard && keyboard.getFirstKeyStroke().equals(stroke)) {
                    AnActionEvent event = AnActionEvent.createEvent(DataManager.getInstance().getDataContext(component),
                            action.getTemplatePresentation().clone(), "AdpTest", ActionUiKind.NONE, null);
                    ActionUtil.performActionDumbAwareWithCallbacks(action, event);
                    return;
                }
            }
        }
        Object name = component.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
        Action binding = name == null ? null : component.getActionMap().get(name);
        if (binding != null && binding.isEnabled()) {
            KeyEvent event = new KeyEvent(component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), stroke.getModifiers(), stroke.getKeyCode(),
                    KeyEvent.CHAR_UNDEFINED);
            SwingUtilities.notifyAction(binding, stroke, event, component, stroke.getModifiers());
        }
    }

    @Test
    public void onlyTheEditableMiddleLabelOfAFlowEntersInPlaceEditing() {
        try (var d = labelled()) {
            String before = d.driver().text();
            d.doubleClickText("f1", "MIDDLE");
            assertTrue("the middle label is editable", d.inPlaceEditing());
            assertEquals("check", editor(d).getText());
            press(editor(d), "ESCAPE");
            assertFalse(d.inPlaceEditing());

            d.doubleClickText("f1", "SOURCE");
            assertFalse("the source label is read-only", d.inPlaceEditing());
            d.doubleClickText("f1", "TARGET");
            assertFalse("the target label is read-only", d.inPlaceEditing());
            assertEquals(before, d.driver().text());
        }
    }

    @Test
    public void enterCommitsAndEscapeCancels() {
        try (var d = open()) {
            String before = d.driver().text();
            d.doubleClickText("a", "owner");
            assertTrue(d.inPlaceEditing());
            assertTrue("a plain slot gets a text field", editor(d) instanceof JTextField);
            assertEquals("Ann", editor(d).getText());

            d.driver().typeInPlace("Bob");
            assertFalse("Enter closes it", d.inPlaceEditing());
            assertEquals(before.replace("owner=\"Ann\"", "owner=\"Bob\""), d.driver().text());
            assertEquals("Undo Change Owner", d.driver().undoLabel());
            assertEquals(List.of("a"), d.driver().selectedKeys());

            String edited = d.driver().text();
            d.doubleClickText("a", "owner");
            editor(d).setText("Zed");
            press(editor(d), "ESCAPE");
            assertFalse("Escape closes it", d.inPlaceEditing());
            assertEquals("Escape applies nothing", edited, d.driver().text());

            d.driver().undo();
            assertEquals("one step", before, d.driver().text());
        }
    }

    @Test
    public void aMultiLineSlotCommitsWithCtrlEnter() {
        try (var d = open()) {
            String before = d.driver().text();
            d.doubleClickText("a", "title");
            assertTrue("a wrapping, multi-line slot gets a text area", editor(d) instanceof JTextArea);
            JTextArea area = (JTextArea) editor(d);
            assertEquals("Place order", area.getText());

            press(area, "ENTER");
            assertTrue("Enter does not commit a multi-line editor", d.inPlaceEditing());
            assertEquals(before, d.driver().text());

            area.setText("Place\norder");
            press(area, "ctrl ENTER");
            d.driver().settle();
            assertFalse(d.inPlaceEditing());
            assertEquals(before.replace(">Place order<", ">Place\norder<"), d.driver().text());
            assertEquals("Undo Change Title", d.driver().undoLabel());
        }
    }

    @Test
    public void f2OpensTheSelectedItemsFirstEditableText() {
        try (var d = open()) {
            d.driver().select("a");
            d.driver().press("F2");
            assertTrue(d.inPlaceEditing());
            assertEquals("the title comes before the owner", "Place order", editor(d).getText());
            press(editor(d), "ESCAPE");

            d.driver().select("f1");
            d.driver().press("F2");
            assertTrue(d.inPlaceEditing());
            assertEquals("a connection's editable label", "submit", editor(d).getText());
            press(editor(d), "ESCAPE");

            d.driver().select();
            assertFalse("nothing selected, nothing to edit", d.driver().presentation(EditInPlaceAction.ID).isEnabled());
        }
    }

    @Test
    public void anItemThatDisappearsWhileEditedGetsNothingApplied() {
        try (var d = open()) {
            d.doubleClickText("a", "title");
            JTextArea area = (JTextArea) editor(d);
            area.setText("Stale");

            d.driver().editText(text -> text.replaceAll("  <box id=\"a\"[^\n]*\n", "").replaceAll("  <link[^\n]*\n", ""));
            String removed = d.driver().text();

            assertFalse("the editor closes with its item", d.inPlaceEditing());
            press(area, "ctrl ENTER");
            d.driver().settle();
            assertEquals("nothing stale is applied", removed, d.driver().text());
            assertEquals("Undo Typing", d.driver().undoLabel());
        }
    }

    @Test
    public void aPanelEditAndAnInPlaceEditAgree() {
        try (var d = open()) {
            d.driver().select("a");
            d.doubleClickText("a", "owner");
            d.driver().typeInPlace("Bob");
            assertEquals("the panel shows the in-place edit", "Bob", d.properties().row("owner").value());

            d.setProperty("owner", "Cy");
            assertEquals("the canvas shows the panel edit", "Cy", d.elementView("a").texts().get("owner"));
            d.doubleClickText("a", "owner");
            assertEquals("Cy", editor(d).getText());
            press(editor(d), "ESCAPE");
        }
    }

    @Test
    public void theEditorSitsOverItsSlotWithTheFontScaledByTheZoom() {
        try (var d = open()) {
            d.designer().zoomIn();
            d.driver().settle();
            double zoom = d.zoomLevel();
            assertTrue(zoom > 1);
            d.doubleClickText("a", "owner");
            JTextComponent field = editor(d);
            assertEquals(JBFont.small().getSize2D() * zoom, field.getFont().getSize2D(), 0.01);
            DiagramCanvas canvas = d.designer().canvas();
            Rectangle slot = canvas.toCanvas(d.designer().textBounds("a", "owner"));
            assertTrue("over the slot: " + field.getBounds() + " " + slot, field.getBounds().contains(slot.x + 1, slot.y + 1));
            press(field, "ESCAPE");
        }
    }

    @Test
    public void aReadOnlyFileOpensNoEditor() {
        try (var d = open()) {
            d.driver().setReadOnly(true);
            d.doubleClickText("a", "owner");
            assertFalse(d.inPlaceEditing());
            d.driver().select("a");
            assertFalse(d.driver().presentation(EditInPlaceAction.ID).isEnabled());
        }
    }

    @Test
    public void showPropertiesIsOfferedWhileTheCanvasHasFocus() {
        try (var d = open()) {
            DiagramCanvas canvas = d.designer().canvas();
            assertTrue(d.driver().presentation(ShowPropertiesAction.ID).isEnabled());
            KeyStroke altShiftP = KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
            boolean registered = ActionUtil.getActions(canvas).stream().anyMatch(action -> action instanceof ShowPropertiesAction
                    && List.of(action.getShortcutSet().getShortcuts()).stream()
                            .anyMatch(s -> s instanceof KeyboardShortcut k && k.getFirstKeyStroke().equals(altShiftP)));
            assertTrue("Alt+Shift+P while the canvas has focus", registered);
            d.driver().press("alt shift P");
        }
    }
}
