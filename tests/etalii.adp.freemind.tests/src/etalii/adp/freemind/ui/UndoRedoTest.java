package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import etalii.adp.testing.DesignerDriver;

/** FR-004, SC-002, US2-AS3, US2-AS4: every action is one labelled operation in the workbench history. */
class UndoRedoTest {

    static Stream<Arguments> actions() {
        return Stream.of(
                Arguments.of("Add Child Node", (Consumer<DesignerDriver>) d -> {
                    d.select(key("B")).run(ADD_CHILD);
                    cancelInPlace(d);
                }),
                Arguments.of("Add Sibling Node", (Consumer<DesignerDriver>) d -> {
                    d.select(key("A1")).run(ADD_SIBLING);
                    cancelInPlace(d);
                }),
                Arguments.of("Rename Node", (Consumer<DesignerDriver>) d -> d.select(key("L")).run(RENAME).typeInPlace("Renamed")),
                Arguments.of("Delete Node", (Consumer<DesignerDriver>) d -> d.select(key("A2")).run(DELETE)),
                Arguments.of("Delete Nodes", (Consumer<DesignerDriver>) d -> d.select(key("A2"), key("B")).run(DELETE)),
                Arguments.of("Move Node", (Consumer<DesignerDriver>) d -> d.select(key("A2")).run(MOVE_UP)),
                Arguments.of("Move Node", (Consumer<DesignerDriver>) d -> d.select(key("A2")).run(MOVE_DOWN)),
                Arguments.of("Move Node", (Consumer<DesignerDriver>) d -> d.select(key("A2")).run(INDENT)),
                Arguments.of("Move Node", (Consumer<DesignerDriver>) d -> d.select(key("A2")).run(OUTDENT)),
                Arguments.of("Fold Branch", (Consumer<DesignerDriver>) d -> d.select(key("A")).run(TOGGLE_FOLD)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("actions")
    void eachActionUndoesToTheOriginalBytesAndRedoes(String label, Consumer<DesignerDriver> action) {
        try (var d = DesignerDriver.openText("undo.mm", MAP, MindMapEditor.ID)) {
            action.accept(d);
            String after = d.text();
            assertNotEquals(MAP, after);
            assertEquals(label, d.undoLabel());
            assertTrue(d.isDirty());

            d.undo();
            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());
            assertNull(d.undoLabel());
            assertArrayEquals(MAP.getBytes(UTF_8), d.savedBytes());

            d.redo();
            assertEquals(after, d.text());
            assertEquals(label, d.undoLabel());
        }
    }

    @Test
    void undoGoesBackInReverseOrderAndRedoReapplies() {
        try (var d = DesignerDriver.openText("undo.mm", MAP, MindMapEditor.ID)) {
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
            assertEquals(List.of("Rename Node", "Move Node", "Fold Branch", "Delete Node", "Add Child Node"), labels);

            for (int i = labels.size() - 1; i >= 0; i--) {
                assertEquals(labels.get(i), d.undoLabel());
                d.undo();
                assertEquals(texts.get(i), d.text());
            }
            assertEquals(MAP, d.text());
            assertFalse(d.isDirty(), "undoing everything clears the dirty marker");

            for (int i = 1; i < texts.size(); i++) {
                d.redo();
                assertEquals(texts.get(i), d.text());
            }
            assertTrue(d.isDirty());
        }
    }
}
