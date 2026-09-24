package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.Test;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.ui.figures.NodeFigure;
import etalii.adp.testing.DesignerDriver;

/** FR-019, FR-011, US2-AS1: a new node appears in the right place, ready for its text to be typed. */
class AddNodeTest {

    static final String ADD_CHILD = "etalii.adp.freemind.addChild";
    static final String ADD_SIBLING = "etalii.adp.freemind.addSibling";

    /** A small map every editing test shares: two branches on the right, one on the left. */
    static final String MAP = """
            <map version="1.0.1">
            <node CREATED="1000" ID="R" MODIFIED="1000" TEXT="Root">
            <node CREATED="1000" ID="A" MODIFIED="1000" POSITION="right" TEXT="A">
            <node CREATED="1000" ID="A1" MODIFIED="1000" TEXT="A1"/>
            <node CREATED="1000" ID="A2" MODIFIED="1000" TEXT="A2"/>
            <node CREATED="1000" ID="A3" MODIFIED="1000" TEXT="A3"/>
            </node>
            <node CREATED="1000" ID="B" MODIFIED="1000" POSITION="right" TEXT="B"/>
            <node CREATED="1000" ID="L" MODIFIED="1000" POSITION="left" TEXT="L"/>
            </node>
            </map>
            """;

    private static final Pattern NEW_NODE = Pattern
            .compile("<node CREATED=\"(\\d+)\" ID=\"(ID_\\d+)\" MODIFIED=\"(\\d+)\"( POSITION=\"(left|right)\")? TEXT=\"New Node\"/>");

    @Test
    void addChildOpensAnInPlaceEditorAndTypingSetsItsText() {
        try (var d = DesignerDriver.openText("add.mm", MAP, MindMapEditor.ID)) {
            long before = System.currentTimeMillis();
            d.select(key("A1")).run(ADD_CHILD);

            Matcher added = NEW_NODE.matcher(d.text());
            assertTrue(added.find(), d.text());
            assertEquals(added.group(1), added.group(3), "CREATED and MODIFIED come from one clock read");
            assertTrue(Long.parseLong(added.group(1)) >= before);
            assertNull(added.group(4), "no POSITION below the first level");
            NodeKey created = key(added.group(2));
            assertEquals(List.of(created), d.selectedModels(), "the new node is selected");
            assertNotNull(d.figureOf(created));
            assertEquals(key("A1"), node(d, created).parent().key());
            assertEquals("Add Child Node", d.undoLabel());
            assertNotNull(inPlaceEditor(d), "its text can be typed right away");

            d.typeInPlace("Idea & more");
            assertTrue(d.text().contains("ID=\"" + created.id() + "\""));
            assertEquals("Idea & more", node(d, created).text());
            assertTrue(d.text().contains("TEXT=\"Idea &amp; more\"/>"), d.text());
            assertEquals("Idea & more", ((NodeFigure) d.figureOf(created)).text());
            assertEquals("Rename Node", d.undoLabel());
            assertNull(inPlaceEditor(d));
        }
    }

    @Test
    void aFirstLevelChildGetsASide() {
        try (var d = DesignerDriver.openText("add.mm", MAP, MindMapEditor.ID)) {
            d.select(key("R")).run(ADD_CHILD);
            cancelInPlace(d);
            Matcher added = NEW_NODE.matcher(d.text());
            assertTrue(added.find(), d.text());
            assertEquals("left", added.group(5), "the lighter side");
            NodeKey created = key(added.group(2));
            assertEquals(key("R"), node(d, created).parent().key());
            assertEquals(List.of(created), d.selectedModels());
        }
    }

    @Test
    void addSiblingGoesRightAfterTheSelectedNode() {
        try (var d = DesignerDriver.openText("add.mm", MAP, MindMapEditor.ID)) {
            d.select(key("A2")).run(ADD_SIBLING);
            cancelInPlace(d);
            Matcher added = NEW_NODE.matcher(d.text());
            assertTrue(added.find(), d.text());
            assertNull(added.group(4));
            NodeKey created = key(added.group(2));
            List<NodeKey> children = node(d, key("A")).children().stream().map(MapNode::key).toList();
            assertEquals(List.of(key("A1"), key("A2"), created, key("A3")), children);
            assertEquals("Add Sibling Node", d.undoLabel());
            assertEquals(List.of(created), d.selectedModels());
        }
    }

    @Test
    void aFirstLevelSiblingKeepsTheSide() {
        try (var d = DesignerDriver.openText("add.mm", MAP, MindMapEditor.ID)) {
            d.select(key("L")).run(ADD_SIBLING);
            cancelInPlace(d);
            Matcher added = NEW_NODE.matcher(d.text());
            assertTrue(added.find(), d.text());
            assertEquals("left", added.group(5));
        }
    }

    @Test
    void theRootHasNoSiblings() {
        try (var d = DesignerDriver.openText("add.mm", MAP, MindMapEditor.ID)) {
            d.select(key("R"));
            assertThrows(IllegalStateException.class, () -> d.run(ADD_SIBLING));
            d.select(key("A"), key("B"));
            assertThrows(IllegalStateException.class, () -> d.run(ADD_SIBLING), "one node at a time");
            assertThrows(IllegalStateException.class, () -> d.run(ADD_CHILD), "one node at a time");
            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());
        }
    }

    @Test
    void aChildOfAFoldedBranchIsShownWithoutAnotherEdit() {
        String folded = MAP.replace("ID=\"A\" MODIFIED=\"1000\"", "FOLDED=\"true\" ID=\"A\" MODIFIED=\"1000\"");
        try (var d = DesignerDriver.openText("add.mm", folded, MindMapEditor.ID)) {
            assertNull(d.figureOf(key("A1")));
            d.select(key("A")).run(ADD_CHILD);
            Matcher added = NEW_NODE.matcher(d.text());
            assertTrue(added.find(), d.text());
            assertNotNull(d.figureOf(key(added.group(2))), "the new node is drawn");
            assertTrue(d.text().contains("FOLDED=\"true\""), "the file still records the branch as folded");
            assertEquals("Add Child Node", d.undoLabel());
            d.typeInPlace("Visible");
            assertEquals("Visible", ((NodeFigure) d.figureOf(key(added.group(2)))).text());
        }
    }

    @Test
    void cancellingTheInPlaceEditorKeepsTheDefaultText() {
        try (var d = DesignerDriver.openText("add.mm", MAP, MindMapEditor.ID)) {
            d.select(key("B")).run(ADD_CHILD);
            cancelInPlace(d);
            assertNull(inPlaceEditor(d));
            assertTrue(NEW_NODE.matcher(d.text()).find());
            assertEquals("Add Child Node", d.undoLabel());
            d.undo();
            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());
        }
    }

    static MapNode node(DesignerDriver d, NodeKey key) {
        MindMap map = (MindMap) d.editor().model();
        MapNode node = map.node(key);
        assertNotNull(node, "no node " + key);
        return node;
    }

    /** The open in-place editor's text field, or {@code null}. */
    static Text inPlaceEditor(DesignerDriver d) {
        return findText(d.editor().viewer().getControl());
    }

    /** Press Escape in the open in-place editor, which closes it without a change. */
    static void cancelInPlace(DesignerDriver d) {
        Text text = inPlaceEditor(d);
        assertNotNull(text, "an in-place editor is open");
        Event escape = new Event();
        escape.character = SWT.ESC;
        escape.keyCode = SWT.ESC;
        text.notifyListeners(SWT.KeyDown, escape);
        d.settle();
    }

    private static Text findText(Control control) {
        if (control instanceof Text text && !text.isDisposed() && text.isVisible()) {
            return text;
        }
        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                Text found = findText(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
