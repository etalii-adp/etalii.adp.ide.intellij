package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.TextVisualSyncTest.node;

import java.awt.Rectangle;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.testing.DesignerDriver;
import etalii.adp.testing.DropPosition;

/** Spec 001 FR-022: dragging a node before, after or onto another moves it with its subtree as one edit. */
@RunWith(JUnit4.class)
public class DragMoveTest extends FileEditorManagerTestCase {

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

    @Override
    public void setUp() {
        super.setUp();
    }

    private DesignerDriver open() {
        return DesignerDriver.openText(myFixture, "drag.mm", MAP);
    }

    @Test
    public void theEditingGesturesAreAttachedWhenTheFileOpens() {
        try (var d = open()) {
            MindMapCanvas canvas = LayoutTest.designer(d).canvas();
            assertTrue("the IDE told the installer the file opened", EditingInstaller.isAttached(canvas));
        }
    }

    @Test
    public void droppingBeforeASiblingReordersAsOneMoveEdit() {
        try (var d = open()) {
            d.dragOnto(key("A2"), key("A1"), DropPosition.BEFORE);
            assertEquals(List.of(key("A2"), key("A1")), children(d, "A"));
            assertEquals(List.of(key("A21")), children(d, "A2"));
            assertEquals("Undo Move Node", d.undoLabel());
            assertEquals(List.of(key("A2")), d.selectedKeys());

            d.undo();
            assertEquals(MAP, d.text());
            assertFalse(d.isModified());
        }
    }

    @Test
    public void droppingAfterANodeMovesUnderItsParent() {
        try (var d = open()) {
            d.dragOnto(key("B"), key("A1"), DropPosition.AFTER);
            assertEquals(List.of(key("A1"), key("B"), key("A2")), children(d, "A"));
            assertNull("a node leaving the first level loses its POSITION", node(d, key("B")).side());
            assertEquals("Undo Move Node", d.undoLabel());
        }
    }

    @Test
    public void droppingOntoANodeMakesItTheLastChild() {
        try (var d = open()) {
            d.dragOnto(key("A2"), key("C"), DropPosition.ONTO);
            assertEquals(List.of(key("A2")), children(d, "C"));
            assertEquals("the subtree moves along", List.of(key("A21")), children(d, "A2"));
            assertEquals(List.of(key("A1")), children(d, "A"));
            assertSame(node(d, key("C")), node(d, key("A2")).parent());
        }
    }

    @Test
    public void droppingIntoItsOwnSubtreeOrDraggingTheRootChangesNothing() {
        try (var d = open()) {
            Rectangle before = d.viewOf(key("A")).bounds();
            d.dragOnto(key("A"), key("A21"), DropPosition.ONTO);
            d.dragOnto(key("A"), key("A1"), DropPosition.AFTER);
            d.dragOnto(key("R"), key("C"), DropPosition.ONTO);
            d.dragOnto(key("C"), key("R"), DropPosition.BEFORE);

            assertEquals(MAP, d.text());
            assertFalse(d.isModified());
            assertNull(d.undoLabel());
            assertEquals("nodes are never positioned freely", before, d.viewOf(key("A")).bounds());
            assertNull("no drop feedback is left behind", DragMove.feedbackOf(LayoutTest.designer(d).canvas()));
        }
    }

    @Test
    public void droppingOnAReadOnlyFileChangesNothing() {
        try (var d = open()) {
            d.setReadOnly(true);
            d.dragOnto(key("A2"), key("C"), DropPosition.ONTO);
            assertEquals(MAP, d.text());
            assertTrue(children(d, "C").isEmpty());
        }
    }

    private static List<NodeKey> children(DesignerDriver d, String id) {
        MapNode node = node(d, key(id));
        return node.children().stream().map(MapNode::key).toList();
    }
}
