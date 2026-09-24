package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.AddNodeTest.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.model.Side;
import etalii.adp.testing.DesignerDriver;

/** FR-022, US2-AS2: keyboard moves among siblings and between levels, with the command table's enablement. */
class MoveNodeTest {

    static final String MOVE_UP = "etalii.adp.freemind.moveUp";
    static final String MOVE_DOWN = "etalii.adp.freemind.moveDown";
    static final String INDENT = "etalii.adp.freemind.indent";
    static final String OUTDENT = "etalii.adp.freemind.outdent";

    @Test
    void moveUpAndDownAmongSiblings() {
        try (var d = DesignerDriver.openText("move.mm", MAP, MindMapEditor.ID)) {
            d.select(key("A2")).run(MOVE_UP);
            assertEquals(List.of(key("A2"), key("A1"), key("A3")), children(d, "A"));
            assertEquals("Move Node", d.undoLabel());
            assertEquals(List.of(key("A2")), d.selectedModels(), "the moved node stays selected");

            d.run(MOVE_DOWN).run(MOVE_DOWN);
            assertEquals(List.of(key("A1"), key("A3"), key("A2")), children(d, "A"));

            d.undo().undo().undo();
            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());
        }
    }

    @Test
    void indentMovesUnderThePreviousSibling() {
        try (var d = DesignerDriver.openText("move.mm", MAP, MindMapEditor.ID)) {
            d.select(key("A3")).run(INDENT);
            assertEquals(List.of(key("A1"), key("A2")), children(d, "A"));
            assertEquals(List.of(key("A3")), children(d, "A2"));
            assertEquals(List.of(key("A3")), d.selectedModels());
            d.undo();
            assertEquals(MAP, d.text());
        }
    }

    @Test
    void outdentMovesAfterTheParentAndGetsASide() {
        try (var d = DesignerDriver.openText("move.mm", MAP, MindMapEditor.ID)) {
            d.select(key("A2")).run(OUTDENT);
            assertEquals(List.of(key("A"), key("A2"), key("B"), key("L")), children(d, "R"));
            assertTrue(d.text().contains("<node CREATED=\"1000\" ID=\"A2\" MODIFIED=\"1000\" POSITION=\"right\" TEXT=\"A2\"/>"), d.text());
            assertEquals(Side.RIGHT, node(d, key("A2")).side());
            d.undo();
            assertEquals(MAP, d.text());
        }
    }

    @Test
    void indentingAFirstLevelNodeDropsItsSide() {
        try (var d = DesignerDriver.openText("move.mm", MAP, MindMapEditor.ID)) {
            d.select(key("B")).run(INDENT);
            assertEquals(List.of(key("A1"), key("A2"), key("A3"), key("B")), children(d, "A"));
            assertTrue(d.text().contains("<node CREATED=\"1000\" ID=\"B\" MODIFIED=\"1000\" TEXT=\"B\"/>"), d.text());
            d.undo();
            assertEquals(MAP, d.text());
        }
    }

    @Test
    void firstLevelNodesMoveAmongTheirOwnSide() {
        try (var d = DesignerDriver.openText("move.mm", MAP, MindMapEditor.ID)) {
            d.select(key("B")).run(MOVE_UP);
            assertEquals(List.of(key("B"), key("A"), key("L")), children(d, "R"));
            assertEquals(Side.RIGHT, node(d, key("B")).side());
            d.select(key("L"));
            assertThrows(IllegalStateException.class, () -> d.run(MOVE_UP), "no earlier node on the left");
            assertThrows(IllegalStateException.class, () -> d.run(MOVE_DOWN), "no later node on the left");
            assertThrows(IllegalStateException.class, () -> d.run(INDENT));
        }
    }

    @Test
    void enablementFollowsTheCommandTable() {
        try (var d = DesignerDriver.openText("move.mm", MAP, MindMapEditor.ID)) {
            d.select(key("A1"));
            assertThrows(IllegalStateException.class, () -> d.run(MOVE_UP), "no previous sibling");
            assertThrows(IllegalStateException.class, () -> d.run(INDENT), "no previous sibling");
            d.select(key("A3"));
            assertThrows(IllegalStateException.class, () -> d.run(MOVE_DOWN), "no next sibling");
            d.select(key("A"));
            assertThrows(IllegalStateException.class, () -> d.run(OUTDENT), "already on the first level");
            d.select(key("R"));
            for (String command : List.of(MOVE_UP, MOVE_DOWN, INDENT, OUTDENT)) {
                assertThrows(IllegalStateException.class, () -> d.run(command), "the root cannot be moved: " + command);
            }
            d.select(key("A1"), key("A2"));
            assertThrows(IllegalStateException.class, () -> d.run(MOVE_DOWN), "one node at a time");
            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());
        }
    }

    private static List<NodeKey> children(DesignerDriver d, String parentId) {
        return node(d, key(parentId)).children().stream().map(MapNode::key).toList();
    }
}
