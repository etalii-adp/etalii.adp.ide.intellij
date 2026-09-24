package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.draw2d.geometry.Rectangle;
import org.junit.jupiter.api.Test;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.testing.DesignerDriver;
import etalii.adp.testing.DropPosition;

/** FR-022: dragging a node before, after or onto another moves it with its subtree as one edit. */
class DragMoveTest {

    static final String MAP = """
            <map version="1.0.1">
            <node ID="R" TEXT="Root">
            <node ID="A" POSITION="right" TEXT="A">
            <node ID="A1" TEXT="A1"/>
            <node ID="A2" TEXT="A2">
            <node ID="A21" TEXT="A21"/>
            </node>
            </node>
            <node ID="B" POSITION="left" TEXT="B"/>
            <node ID="C" POSITION="right" TEXT="C"/>
            </node>
            </map>
            """;

    @Test
    void droppingBeforeASiblingReordersAsOneMoveEdit() {
        try (var d = DesignerDriver.openText("drag.mm", MAP, MindMapEditor.ID)) {
            d.dragOnto(key("A2"), key("A1"), DropPosition.BEFORE);
            assertEquals(List.of(key("A2"), key("A1")), children(d, "A"));
            assertEquals(List.of(key("A21")), children(d, "A2"));
            assertEquals("Move Node", d.undoLabel());

            d.undo();
            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());
        }
    }

    @Test
    void droppingAfterANodeMovesUnderItsParent() {
        try (var d = DesignerDriver.openText("drag.mm", MAP, MindMapEditor.ID)) {
            d.dragOnto(key("B"), key("A1"), DropPosition.AFTER);
            assertEquals(List.of(key("A1"), key("B"), key("A2")), children(d, "A"));
            assertNull(node(d, "B").side(), "a node leaving the first level loses its POSITION");
            assertEquals("Move Node", d.undoLabel());
        }
    }

    @Test
    void droppingOntoANodeMakesItTheLastChild() {
        try (var d = DesignerDriver.openText("drag.mm", MAP, MindMapEditor.ID)) {
            d.dragOnto(key("A2"), key("C"), DropPosition.ONTO);
            assertEquals(List.of(key("A2")), children(d, "C"));
            assertEquals(List.of(key("A21")), children(d, "A2"), "the subtree moves along");
            assertEquals(List.of(key("A1")), children(d, "A"));
            assertSame(node(d, "C"), node(d, "A2").parent());
        }
    }

    @Test
    void droppingIntoItsOwnSubtreeOrDraggingTheRootChangesNothing() {
        try (var d = DesignerDriver.openText("drag.mm", MAP, MindMapEditor.ID)) {
            Rectangle before = d.figureOf(key("A")).getBounds().getCopy();
            d.dragOnto(key("A"), key("A21"), DropPosition.ONTO);
            d.dragOnto(key("A"), key("A1"), DropPosition.AFTER);
            d.dragOnto(key("R"), key("C"), DropPosition.ONTO);
            d.dragOnto(key("C"), key("R"), DropPosition.BEFORE);

            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());
            assertNull(d.undoLabel());
            assertEquals(before, d.figureOf(key("A")).getBounds(), "nodes are never positioned freely");
        }
    }

    @Test
    void droppingOnAReadOnlyFileChangesNothing() {
        try (var d = DesignerDriver.openText("drag.mm", MAP, MindMapEditor.ID)) {
            d.setReadOnly(true);
            d.dragOnto(key("A2"), key("C"), DropPosition.ONTO);
            assertEquals(MAP, d.text());
            assertTrue(children(d, "C").isEmpty());
        }
    }

    private static MapNode node(DesignerDriver d, String id) {
        return ((MindMap) d.editor().model()).node(key(id));
    }

    private static List<NodeKey> children(DesignerDriver d, String id) {
        return node(d, id).children().stream().map(MapNode::key).toList();
    }
}
