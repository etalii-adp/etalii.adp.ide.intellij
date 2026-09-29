package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.ADD_CHILD;
import static etalii.adp.freemind.ui.AddNodeTest.ADD_SIBLING;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.AddNodeTest.cancelInPlace;
import static etalii.adp.freemind.ui.DeleteTest.DELETE;
import static etalii.adp.freemind.ui.FoldTest.TOGGLE_FOLD;
import static etalii.adp.freemind.ui.MoveNodeTest.INDENT;
import static etalii.adp.freemind.ui.MoveNodeTest.MOVE_DOWN;
import static etalii.adp.freemind.ui.MoveNodeTest.MOVE_UP;
import static etalii.adp.freemind.ui.MoveNodeTest.OUTDENT;
import static etalii.adp.freemind.ui.RenameTest.RENAME;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.freemind.FreeMindAsserts;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.ui.actions.MindMapAction;
import etalii.adp.testing.ToolDriver;
import etalii.adp.testing.DropPosition;

/**
 * Spec 001 FR-004, SC-002; FR-004, FR-008, SC-003: every action is one named step in the IDE's own
 * Undo and Redo, and every reference scenario, run through the diagram as a user would, saves the
 * bytes recorded under spec 001.
 */
@RunWith(JUnit4.class)
public class UndoRedoTest extends FileEditorManagerTestCase {

    /** {@link AddNodeTest#MAP} with branch A recorded as folded, for Unfold Branch. */
    static final String FOLDED_MAP = MAP.replace("ID=\"A\" MODIFIED", "FOLDED=\"true\" ID=\"A\" MODIFIED");

    @Override
    public void setUp() {
        super.setUp();
    }

    /** Each action type on {@link AddNodeTest#MAP} ({@link #FOLDED_MAP} for Unfold Branch), by the label its undo shows. */
    private static Map<String, Consumer<ToolDriver>> actions() {
        Map<String, Consumer<ToolDriver>> actions = new LinkedHashMap<>();
        actions.put("Add Child Node", d -> {
            d.select(key("B")).run(ADD_CHILD);
            cancelInPlace(d);
        });
        actions.put("Add Sibling Node", d -> {
            d.select(key("A1")).run(ADD_SIBLING);
            cancelInPlace(d);
        });
        actions.put("Rename Node", d -> d.select(key("L")).run(RENAME).typeInPlace("Renamed"));
        actions.put("Delete Node", d -> d.select(key("A2")).run(DELETE));
        actions.put("Delete Nodes", d -> d.select(key("A2"), key("B")).run(DELETE));
        actions.put("Move Node (up)", d -> d.select(key("A2")).run(MOVE_UP));
        actions.put("Move Node (down)", d -> d.select(key("A2")).run(MOVE_DOWN));
        actions.put("Move Node (indent)", d -> d.select(key("A2")).run(INDENT));
        actions.put("Move Node (outdent)", d -> d.select(key("A2")).run(OUTDENT));
        actions.put("Move Node (drag)", d -> d.dragOnto(key("A2"), key("L"), DropPosition.ONTO));
        actions.put("Fold Branch", d -> d.select(key("A")).run(TOGGLE_FOLD));
        actions.put("Unfold Branch", d -> d.select(key("A")).run(TOGGLE_FOLD));
        return actions;
    }

    @Test
    public void eachActionUndoesToTheOriginalBytesAndRedoes() {
        actions().forEach((name, action) -> {
            String label = "Undo " + name.replaceAll(" \\(.*\\)", "");
            String before = name.equals("Unfold Branch") ? FOLDED_MAP : MAP;
            String file = "undo-" + name.replaceAll("[^A-Za-z]", "") + ".mm";
            // The diagram has focus, as when the user presses the shortcut: the IDE then records
            // its view state with each step, and one Ctrl+Z must still undo the edit.
            UndoManagerImpl undo = (UndoManagerImpl) UndoManager.getInstance(getProject());
            try (var d = ToolDriver.openText(myFixture, file, before)) {
                undo.setOverriddenEditorProvider(new CurrentEditorProvider() {
                    @Override
                    public FileEditor getCurrentEditor(Project project) {
                        return d.composite();
                    }
                });
                action.accept(d);
                String after = d.text();
                assertFalse(name, before.equals(after));
                assertEquals(name, label, d.undoLabel());
                assertTrue(name, d.isModified());

                d.undo();
                assertEquals(name, before, d.text());
                assertFalse(name + ": undo clears the modified marker", d.isModified());
                assertNull(name, d.undoLabel());
                assertEquals(name, before, new String(d.savedBytes(), UTF_8));
                assertEquals(name, label.replace("Undo", "Redo"), d.redoLabel());

                d.redo();
                assertEquals(name, after, d.text());
                assertEquals(name, label, d.undoLabel());
            } finally {
                undo.setOverriddenEditorProvider(null);
            }
        });
    }

    @Test
    public void undoGoesBackInReverseOrderAndRedoReapplies() {
        try (var d = ToolDriver.openText(myFixture, "undo.mm", MAP)) {
            List<String> texts = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            texts.add(d.text());

            d.select(key("B")).run(RENAME).typeInPlace("Bee");
            texts.add(d.text());
            labels.add(d.undoLabel());
            d.select(key("A2")).run(MOVE_UP);
            texts.add(d.text());
            labels.add(d.undoLabel());
            d.select(key("A")).run(TOGGLE_FOLD);
            texts.add(d.text());
            labels.add(d.undoLabel());
            d.select(key("L")).run(DELETE);
            texts.add(d.text());
            labels.add(d.undoLabel());
            d.select(key("B")).run(ADD_CHILD);
            cancelInPlace(d);
            texts.add(d.text());
            labels.add(d.undoLabel());
            assertEquals(List.of("Undo Rename Node", "Undo Move Node", "Undo Fold Branch", "Undo Delete Node", "Undo Add Child Node"), labels);

            for (int i = labels.size() - 1; i >= 0; i--) {
                assertEquals(labels.get(i), d.undoLabel());
                d.undo();
                assertEquals(texts.get(i), d.text());
            }
            assertEquals(MAP, d.text());
            assertFalse("undoing everything clears the modified marker", d.isModified());

            for (int i = 1; i < texts.size(); i++) {
                d.redo();
                assertEquals(texts.get(i), d.text());
            }
            assertTrue(d.isModified());
        }
    }

    /**
     * Every scenario of {@code scenarios.json} through the diagram: select and run the action, or
     * drag, with the recorded clock and random seed. The saved file is the recorded result, and
     * undoing returns the original bytes.
     */
    @Test
    public void everyReferenceScenarioThroughTheToolMatchesItsRecordedResult() throws IOException {
        TestDialogManager.setTestDialog(message -> Messages.OK, getTestRootDisposable());
        List<String> failures = new ArrayList<>();
        int run = 0;
        for (JsonObject scenario : scenarios()) {
            String map = scenario.get("map").getAsString();
            String stem = map.substring(0, map.length() - ".mm".length());
            String result = scenario.get("nn").getAsString() + "-" + scenario.get("action").getAsString() + ".mm";
            byte[] original = Files.readAllBytes(FreeMindAsserts.example(map));
            byte[] expected = Files.readAllBytes(FreeMindAsserts.reference(stem, result));
            long now = scenario.get("now").getAsLong();
            long seed = scenario.get("seed").getAsLong();

            var clock = Disposer.newDisposable("scenario clock");
            MindMapAction.useClock(() -> now, () -> new Random(seed), clock);
            try (var d = ToolDriver.openBytes(myFixture, stem + "-" + result, original)) {
                int steps = perform(d, scenario);
                byte[] saved = d.savedBytes();
                if (!Arrays.equals(expected, saved)) {
                    failures.add(stem + "/" + result + ": saved bytes differ from the recorded result");
                    continue;
                }
                for (int i = 0; i < steps; i++) {
                    d.undo();
                }
                if (!Arrays.equals(original, d.savedBytes())) {
                    failures.add(stem + "/" + result + ": undo does not restore the original bytes");
                }
                run++;
            } finally {
                Disposer.dispose(clock);
            }
        }
        assertEquals(String.join("\n", failures), List.of(), failures);
        assertEquals(101, run);
    }

    /** Runs one scenario as a user would; returns the number of undo steps it made. */
    private static int perform(ToolDriver d, JsonObject s) {
        String action = s.get("action").getAsString();
        switch (action) {
        case "add-child", "add-child-root", "add-child-leaf" -> {
            d.select(node(s, "node")).run(ADD_CHILD).typeInPlace(s.get("text").getAsString());
            return 2;
        }
        case "add-sibling" -> {
            d.select(node(s, "node")).run(ADD_SIBLING).typeInPlace(s.get("text").getAsString());
            return 2;
        }
        case "rename", "rename-rich" -> {
            d.select(node(s, "node")).run(RENAME).typeInPlace(s.get("text").getAsString());
            return 1;
        }
        case "delete", "delete-several" -> {
            List<Object> keys = new ArrayList<>();
            s.getAsJsonArray("nodes").forEach(k -> keys.add(scenarioKey(k.getAsString())));
            d.select(keys.toArray()).run(DELETE);
            return 1;
        }
        case "move-up" -> d.select(node(s, "node")).run(MOVE_UP);
        case "move-down" -> d.select(node(s, "node")).run(MOVE_DOWN);
        case "indent" -> d.select(node(s, "node")).run(INDENT);
        case "outdent" -> d.select(node(s, "node")).run(OUTDENT);
        case "drag-to-first-level", "drag-from-first-level" -> {
            NodeKey node = node(s, "node");
            NodeKey target = node(s, "target");
            d.tool().reveal(node);
            d.tool().reveal(target);
            d.settle();
            d.dragOnto(node, target, DropPosition.ONTO);
        }
        case "fold", "unfold" -> d.select(node(s, "node")).run(TOGGLE_FOLD);
        default -> throw new IllegalArgumentException("Unknown action " + action);
        }
        return 1;
    }

    /** A key as {@code scenarios.json} writes it: {@code id:<ID>} or {@code path:<index path>}. */
    private static NodeKey scenarioKey(String written) {
        return written.startsWith("id:") ? NodeKey.ofId(written.substring(3)) : NodeKey.ofPath(written.substring(5));
    }

    private static NodeKey node(JsonObject s, String field) {
        return scenarioKey(s.get(field).getAsString());
    }

    private static List<JsonObject> scenarios() throws IOException {
        Path file = FreeMindAsserts.referenceDirectory().resolve("scenarios.json");
        List<JsonObject> scenarios = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(Files.readString(file, UTF_8)).getAsJsonArray()) {
            scenarios.add(element.getAsJsonObject());
        }
        return scenarios;
    }
}
