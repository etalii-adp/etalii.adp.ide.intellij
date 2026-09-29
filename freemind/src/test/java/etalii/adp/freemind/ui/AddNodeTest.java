package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.TextVisualSyncTest.node;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JTextField;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.ui.actions.MindMapAction;
import etalii.adp.testing.ToolDriver;

/** Spec 001 FR-019, FR-011, US2-AS1: a new node appears in the right place, ready for its text to be typed. */
@RunWith(JUnit4.class)
public class AddNodeTest extends FileEditorManagerTestCase {

    static final String ADD_CHILD = "etalii.adp.freemind.AddChild";
    static final String ADD_SIBLING = "etalii.adp.freemind.AddSibling";

    /** The small map every editing test shares: two branches on the right, one on the left. */
    static final String MAP = TextVisualSyncTest.MAP;

    private static final Pattern NEW_NODE = Pattern
            .compile("<node CREATED=\"(\\d+)\" ID=\"(ID_\\d+)\" MODIFIED=\"(\\d+)\"( POSITION=\"(left|right)\")? TEXT=\"New Node\"/>");

    @Override
    public void setUp() {
        super.setUp();
    }

    private ToolDriver open(String text) {
        return ToolDriver.openText(myFixture, "add.mm", text);
    }

    @Test
    public void addChildOpensAnInPlaceEditorAndTypingSetsItsText() {
        try (var d = open(MAP)) {
            long before = System.currentTimeMillis();
            d.select(key("A1")).run(ADD_CHILD);

            Matcher added = NEW_NODE.matcher(d.text());
            assertTrue(d.text(), added.find());
            assertEquals("CREATED and MODIFIED come from one clock read", added.group(1), added.group(3));
            assertTrue(Long.parseLong(added.group(1)) >= before);
            assertNull("no POSITION below the first level", added.group(4));
            NodeKey created = key(added.group(2));
            assertEquals("the new node is selected", List.of(created), d.selectedKeys());
            assertNotNull(d.viewOf(created));
            assertEquals(key("A1"), node(d, created).parent().key());
            assertEquals("Undo Add Child Node", d.undoLabel());
            assertNotNull("its text can be typed right away", d.inPlaceField());

            d.typeInPlace("Idea & more");
            assertEquals("Idea & more", node(d, created).text());
            assertTrue(d.text(), d.text().contains("TEXT=\"Idea &amp; more\"/>"));
            assertEquals("Idea & more", d.viewOf(created).text());
            assertEquals("Undo Rename Node", d.undoLabel());
            assertNull(d.inPlaceField());
        }
    }

    @Test
    public void addChildIntoASelfClosingNode() {
        try (var d = open(MAP)) {
            d.select(key("B")).run(ADD_CHILD);
            cancelInPlace(d);
            Matcher added = NEW_NODE.matcher(d.text());
            assertTrue(d.text(), added.find());
            assertFalse("B is no longer self-closing", d.text().contains("TEXT=\"B\"/>"));
            assertEquals(List.of(key(added.group(2))), node(d, key("B")).children().stream().map(MapNode::key).toList());
            assertEquals("Undo Add Child Node", d.undoLabel());
            d.undo();
            assertEquals(MAP, d.text());
        }
    }

    @Test
    public void aFirstLevelChildGetsASide() {
        try (var d = open(MAP)) {
            d.select(key("R")).run(ADD_CHILD);
            cancelInPlace(d);
            Matcher added = NEW_NODE.matcher(d.text());
            assertTrue(d.text(), added.find());
            assertEquals("the lighter side", "left", added.group(5));
            NodeKey created = key(added.group(2));
            assertEquals(key("R"), node(d, created).parent().key());
            assertEquals(List.of(created), d.selectedKeys());
        }
    }

    @Test
    public void addSiblingGoesRightAfterTheSelectedNode() {
        try (var d = open(MAP)) {
            d.select(key("A2")).run(ADD_SIBLING);
            cancelInPlace(d);
            Matcher added = NEW_NODE.matcher(d.text());
            assertTrue(d.text(), added.find());
            assertNull(added.group(4));
            NodeKey created = key(added.group(2));
            List<NodeKey> children = node(d, key("A")).children().stream().map(MapNode::key).toList();
            assertEquals(List.of(key("A1"), key("A2"), created, key("A3")), children);
            assertEquals("Undo Add Sibling Node", d.undoLabel());
            assertEquals(List.of(created), d.selectedKeys());
        }
    }

    @Test
    public void aFirstLevelSiblingKeepsTheSide() {
        try (var d = open(MAP)) {
            d.select(key("L")).run(ADD_SIBLING);
            cancelInPlace(d);
            Matcher added = NEW_NODE.matcher(d.text());
            assertTrue(d.text(), added.find());
            assertEquals("left", added.group(5));
        }
    }

    @Test
    public void theRootHasNoSiblings() {
        try (var d = open(MAP)) {
            d.select(key("R"));
            assertFalse(d.presentation(ADD_SIBLING).isEnabled());
            assertEquals("the reason is stated", MindMapAction.ROOT_HAS_NO_SIBLINGS, d.presentation(ADD_SIBLING).getDescription());
            d.run(ADD_SIBLING);
            d.select(key("A"), key("B"));
            assertFalse("one node at a time", d.presentation(ADD_SIBLING).isEnabled());
            assertFalse("one node at a time", d.presentation(ADD_CHILD).isEnabled());
            d.run(ADD_SIBLING).run(ADD_CHILD);
            assertEquals(MAP, d.text());
            assertFalse(d.isModified());
            assertNull(d.inPlaceField());
        }
    }

    @Test
    public void aChildOfAFoldedBranchIsShownWithoutAnotherEdit() {
        String folded = MAP.replace("ID=\"A\" MODIFIED=\"1000\"", "FOLDED=\"true\" ID=\"A\" MODIFIED=\"1000\"");
        try (var d = open(folded)) {
            assertNull(d.viewOf(key("A1")));
            d.select(key("A")).run(ADD_CHILD);
            Matcher added = NEW_NODE.matcher(d.text());
            assertTrue(d.text(), added.find());
            assertNotNull("the new node is drawn", d.viewOf(key(added.group(2))));
            assertTrue("the file still records the branch as folded", d.text().contains("FOLDED=\"true\""));
            assertEquals("Undo Add Child Node", d.undoLabel());
            d.typeInPlace("Visible");
            assertEquals("Visible", d.viewOf(key(added.group(2))).text());
        }
    }

    @Test
    public void cancellingTheInPlaceEditorKeepsTheDefaultText() {
        try (var d = open(MAP)) {
            d.select(key("B")).run(ADD_CHILD);
            cancelInPlace(d);
            assertNull(d.inPlaceField());
            assertTrue(NEW_NODE.matcher(d.text()).find());
            assertEquals("Undo Add Child Node", d.undoLabel());
            d.undo();
            assertEquals(MAP, d.text());
            assertFalse(d.isModified());
        }
    }

    /** Press Escape in the open in-place editor, which closes it without a change. */
    static void cancelInPlace(ToolDriver d) {
        JTextField field = d.inPlaceField();
        assertNotNull("an in-place editor is open", field);
        KeyEvent escape = new KeyEvent(field, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : field.getKeyListeners()) {
            listener.keyPressed(escape);
        }
        d.settle();
    }
}
