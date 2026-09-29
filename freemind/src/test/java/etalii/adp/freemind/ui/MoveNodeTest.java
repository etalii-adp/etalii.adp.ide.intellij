package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.TextVisualSyncTest.node;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.model.Side;
import etalii.adp.freemind.ui.actions.MindMapAction;
import etalii.adp.testing.ToolDriver;

/** Spec 001 FR-022, US2-AS2: keyboard moves among siblings and between levels, with the command table's enablement. */
@RunWith(JUnit4.class)
public class MoveNodeTest extends FileEditorManagerTestCase {

    static final String MOVE_UP = "etalii.adp.freemind.MoveUp";
    static final String MOVE_DOWN = "etalii.adp.freemind.MoveDown";
    static final String INDENT = "etalii.adp.freemind.Indent";
    static final String OUTDENT = "etalii.adp.freemind.Outdent";

    @Override
    public void setUp() {
        super.setUp();
    }

    private ToolDriver open() {
        return ToolDriver.openText(myFixture, "move.mm", MAP);
    }

    @Test
    public void moveUpAndDownAmongSiblings() {
        try (var d = open()) {
            d.select(key("A2")).press("control UP");
            assertEquals(List.of(key("A2"), key("A1"), key("A3")), children(d, "A"));
            assertEquals("Undo Move Node", d.undoLabel());
            assertEquals("the moved node stays selected", List.of(key("A2")), d.selectedKeys());

            d.run(MOVE_DOWN).press("control DOWN");
            assertEquals(List.of(key("A1"), key("A3"), key("A2")), children(d, "A"));

            d.undo().undo().undo();
            assertEquals(MAP, d.text());
            assertFalse(d.isModified());
        }
    }

    @Test
    public void indentMovesUnderThePreviousSibling() {
        try (var d = open()) {
            d.select(key("A3")).press("control RIGHT");
            assertEquals(List.of(key("A1"), key("A2")), children(d, "A"));
            assertEquals(List.of(key("A3")), children(d, "A2"));
            assertEquals(List.of(key("A3")), d.selectedKeys());
            d.undo();
            assertEquals(MAP, d.text());
        }
    }

    @Test
    public void outdentMovesAfterTheParentAndGetsASide() {
        try (var d = open()) {
            d.select(key("A2")).press("control LEFT");
            assertEquals(List.of(key("A"), key("A2"), key("B"), key("L")), children(d, "R"));
            assertTrue(d.text(), d.text().contains("<node CREATED=\"1000\" ID=\"A2\" MODIFIED=\"1000\" POSITION=\"right\" TEXT=\"A2\"/>"));
            assertEquals(Side.RIGHT, node(d, key("A2")).side());
            d.undo();
            assertEquals(MAP, d.text());
        }
    }

    @Test
    public void indentingAFirstLevelNodeDropsItsSide() {
        try (var d = open()) {
            d.select(key("B")).run(INDENT);
            assertEquals(List.of(key("A1"), key("A2"), key("A3"), key("B")), children(d, "A"));
            assertTrue(d.text(), d.text().contains("<node CREATED=\"1000\" ID=\"B\" MODIFIED=\"1000\" TEXT=\"B\"/>"));
            d.undo();
            assertEquals(MAP, d.text());
        }
    }

    @Test
    public void firstLevelNodesMoveAmongTheirOwnSide() {
        try (var d = open()) {
            d.select(key("B")).run(MOVE_UP);
            assertEquals(List.of(key("B"), key("A"), key("L")), children(d, "R"));
            assertEquals(Side.RIGHT, node(d, key("B")).side());
            d.select(key("L"));
            assertFalse("no earlier node on the left", d.presentation(MOVE_UP).isEnabled());
            assertFalse("no later node on the left", d.presentation(MOVE_DOWN).isEnabled());
            assertFalse(d.presentation(INDENT).isEnabled());
        }
    }

    @Test
    public void enablementFollowsTheCommandTable() {
        try (var d = open()) {
            d.select(key("A1"));
            assertFalse("no previous sibling", d.presentation(MOVE_UP).isEnabled());
            assertFalse("no previous sibling", d.presentation(INDENT).isEnabled());
            assertTrue(d.presentation(MOVE_DOWN).isEnabled());
            assertTrue(d.presentation(OUTDENT).isEnabled());
            d.select(key("A3"));
            assertFalse("no next sibling", d.presentation(MOVE_DOWN).isEnabled());
            d.select(key("A"));
            assertFalse("already on the first level", d.presentation(OUTDENT).isEnabled());
            d.select(key("R"));
            for (String action : List.of(MOVE_UP, MOVE_DOWN, INDENT, OUTDENT)) {
                assertFalse("the root cannot be moved: " + action, d.presentation(action).isEnabled());
                assertEquals(action, MindMapAction.ROOT_CANNOT_BE_MOVED, d.presentation(action).getDescription());
                d.run(action);
            }
            d.select(key("A1"), key("A2"));
            assertFalse("one node at a time", d.presentation(MOVE_DOWN).isEnabled());
            d.run(MOVE_DOWN);
            assertEquals(MAP, d.text());
            assertFalse(d.isModified());
        }
    }

    private static List<NodeKey> children(ToolDriver d, String parentId) {
        return node(d, key(parentId)).children().stream().map(MapNode::key).toList();
    }
}
