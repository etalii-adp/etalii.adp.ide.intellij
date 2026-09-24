package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.bindings.Binding;
import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.jface.bindings.keys.KeySequence;
import org.eclipse.jface.bindings.keys.ParseException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.keys.IBindingService;
import org.junit.jupiter.api.Test;

import etalii.adp.testing.DesignerDriver;

/** FR-024: every editing action is in the context menu, in the command table's order, and has a key. */
class ContextMenuTest {

    static final String SEPARATOR = "---";

    @Test
    void theContextMenuListsEveryCommandInTableOrder() {
        try (var d = DesignerDriver.openText("menu.mm", MAP, MindMapEditor.ID)) {
            d.select(key("A2"));
            Menu menu = show(d);
            List<String> labels = labels(menu);
            // Contributions for any popup menu, from other plug-ins, may follow in the additions group.
            List<String> ours = labels.subList(0, labels.indexOf("Fold / Unfold Branch") + 1);
            assertEquals(List.of("Add Child Node", "Add Sibling Node", SEPARATOR, "Rename Node", "Delete Node", SEPARATOR, "Move Node Up",
                    "Move Node Down", "Move Under Previous Sibling", "Move Up a Level", SEPARATOR, "Fold / Unfold Branch"), ours, labels.toString());

            MenuManager manager = (MenuManager) d.editor().viewer().getContextMenu();
            List<String> ids = new ArrayList<>();
            for (IContributionItem item : manager.getItems()) {
                ids.add(item.getId());
            }
            int add = ids.indexOf("etalii.adp.freemind.add");
            int change = ids.indexOf("etalii.adp.freemind.change");
            int move = ids.indexOf("etalii.adp.freemind.move");
            int additions = ids.indexOf("additions");
            assertTrue(0 < add && add < change && change < move && move < additions, ids.toString());
            assertNotNull(manager.find("additions"), "the standard additions group");
            menu.notifyListeners(SWT.Hide, new Event());
        }
    }

    @Test
    void menuItemsFollowTheSelection() {
        try (var d = DesignerDriver.openText("menu.mm", MAP, MindMapEditor.ID)) {
            d.select(key("R"));
            Menu menu = show(d);
            Map<String, Boolean> enabled = enablement(menu);
            assertTrue(enabled.get("Add Child Node"));
            assertFalse(enabled.get("Add Sibling Node"), "the root has no siblings");
            assertFalse(enabled.get("Delete Node"), "the root cannot be deleted");
            assertFalse(enabled.get("Move Node Up"));
            assertTrue(enabled.get("Fold / Unfold Branch"));
            menu.notifyListeners(SWT.Hide, new Event());

            d.select(key("A2"));
            menu = show(d);
            enabled = enablement(menu);
            assertTrue(enabled.get("Add Sibling Node"));
            assertTrue(enabled.get("Delete Node"));
            assertTrue(enabled.get("Move Node Up"));
            assertTrue(enabled.get("Move Up a Level"));
            assertFalse(enabled.get("Fold / Unfold Branch"), "a leaf has nothing to fold");
            menu.notifyListeners(SWT.Hide, new Event());
        }
    }

    @Test
    void everyCommandHasItsKeyInTheDesignerContext() throws ParseException {
        Map<String, Set<TriggerSequence>> expected = new HashMap<>();
        expected.put("etalii.adp.freemind.addChild", keys("INSERT", "TAB"));
        expected.put("etalii.adp.freemind.addSibling", keys("CR"));
        expected.put("etalii.adp.freemind.rename", keys("F2"));
        expected.put("etalii.adp.freemind.delete", keys("DEL"));
        expected.put("etalii.adp.freemind.moveUp", keys("M1+ARROW_UP"));
        expected.put("etalii.adp.freemind.moveDown", keys("M1+ARROW_DOWN"));
        expected.put("etalii.adp.freemind.indent", keys("M1+ARROW_RIGHT"));
        expected.put("etalii.adp.freemind.outdent", keys("M1+ARROW_LEFT"));
        expected.put("etalii.adp.freemind.toggleFold", keys("SPACE"));

        IBindingService bindings = PlatformUI.getWorkbench().getService(IBindingService.class);
        Map<String, Set<TriggerSequence>> actual = new HashMap<>();
        for (Binding binding : bindings.getBindings()) {
            if (MindMapEditor.CONTEXT_ID.equals(binding.getContextId()) && binding.getParameterizedCommand() != null
                    && binding.getType() == Binding.SYSTEM) {
                actual.computeIfAbsent(binding.getParameterizedCommand().getId(), id -> new HashSet<>()).add(binding.getTriggerSequence());
            }
        }
        assertEquals(expected, actual);
    }

    private static Set<TriggerSequence> keys(String... sequences) throws ParseException {
        Set<TriggerSequence> keys = new HashSet<>();
        for (String sequence : sequences) {
            keys.add(KeySequence.getInstance(sequence));
        }
        return keys;
    }

    /** Show the viewer's context menu as a right-click does, which fills it. */
    private static Menu show(DesignerDriver d) {
        MenuManager manager = (MenuManager) d.editor().viewer().getContextMenu();
        Menu menu = manager.getMenu();
        if (menu == null || menu.isDisposed()) {
            menu = manager.createContextMenu(d.editor().viewer().getControl());
        }
        menu.notifyListeners(SWT.Show, new Event());
        d.settle();
        return menu;
    }

    private static List<String> labels(Menu menu) {
        List<String> labels = new ArrayList<>();
        for (MenuItem item : menu.getItems()) {
            labels.add((item.getStyle() & SWT.SEPARATOR) != 0 ? SEPARATOR : label(item));
        }
        return labels;
    }

    private static Map<String, Boolean> enablement(Menu menu) {
        Map<String, Boolean> enabled = new HashMap<>();
        for (MenuItem item : menu.getItems()) {
            if ((item.getStyle() & SWT.SEPARATOR) == 0) {
                enabled.put(label(item), item.getEnabled());
            }
        }
        return enabled;
    }

    /** The item's text without its mnemonic and accelerator. */
    private static String label(MenuItem item) {
        String text = item.getText();
        int tab = text.indexOf('\t');
        return (tab < 0 ? text : text.substring(0, tab)).replace("&", "");
    }
}
