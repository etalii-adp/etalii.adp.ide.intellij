package etalii.adp.freemind.ui;

import static etalii.adp.freemind.ui.AddNodeTest.ADD_CHILD;
import static etalii.adp.freemind.ui.AddNodeTest.cancelInPlace;
import static etalii.adp.freemind.ui.FoldTest.TOGGLE_FOLD;
import static etalii.adp.freemind.ui.MoveNodeTest.MOVE_DOWN;
import static etalii.adp.freemind.ui.RenameTest.RENAME;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.freemind.FreeMindAsserts;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.model.NodeRanges;
import etalii.adp.core.xml.Range;
import etalii.adp.freemind.parse.MindMapParser;
import etalii.adp.testing.DesignerDriver;

/**
 * Spec 001 FR-010, SC-004; FR-007: after a mix of edits and a save, everything the designer does
 * not display (notes, icons, attributes, clouds, edges, hooks, unknown elements) is unchanged, and
 * only the edited nodes' start tags and the moved nodes' places differ.
 */
@RunWith(JUnit4.class)
public class PreservationTest extends FileEditorManagerTestCase {

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void mixedEditsChangeOnlyTheEditedNodes() throws Exception {
        for (Path example : FreeMindAsserts.examples()) {
            try (var d = DesignerDriver.open(myFixture, example)) {
                MindMapDesigner designer = LayoutTest.designer(d);
                MindMap before = designer.model();
                assertNotNull(example.toString(), before);
                List<NodeKey> drawn = designer.canvas().mapLayout().views().keySet().stream().toList();
                MapNode root = before.root();

                // Add a child to the root: the root's MODIFIED changes, the new node is its last child.
                d.select(root.key()).run(ADD_CHILD);
                cancelInPlace(d);

                // Rename a plain node, and fold or unfold a branch that is not the root.
                MapNode renamed = drawn.stream().map(before::node).filter(n -> n != root && !n.rich() && n.id() != null).findFirst().orElseThrow();
                d.select(renamed.key()).run(RENAME).typeInPlace("Renamed é");
                MapNode folded = drawn.stream().map(before::node).filter(n -> n != root && n != renamed && !n.children().isEmpty() && n.id() != null)
                        .findFirst().orElse(null);
                if (folded != null) {
                    d.select(folded.key()).run(TOGGLE_FOLD);
                }
                // Move a node with an ID down among its siblings: its text moves verbatim.
                MapNode moved = before.nodesByKey().values().stream()
                        .filter(n -> n.id() != null && n.parent() != null && n.parent() != root && n != renamed && n != folded
                                && n.parent().children().indexOf(n) < n.parent().children().size() - 1)
                        .findFirst().orElse(null);
                if (moved != null) {
                    d.select(moved.key()).run(MOVE_DOWN);
                }

                String saved = d.text();
                assertEquals(example.toString(), saved, new String(d.savedBytes(), UTF_8).replace("\r\n", "\n").replace('\r', '\n'));
                MindMap after = MindMapParser.parse(saved);
                String original = before.text();

                Set<NodeKey> startTagEdited = new HashSet<>(Set.of(root.key(), renamed.key()));
                if (folded != null) {
                    startTagEdited.add(folded.key());
                }
                assertEquals("text before the root", prologue(original, before), prologue(saved, after));
                assertEquals("text after the root", epilogue(original, before), epilogue(saved, after));
                for (MapNode node : before.nodesByKey().values()) {
                    if (node.id() == null) {
                        continue; // a path key names another node once a sibling before it moved
                    }
                    MapNode now = after.node(node.key());
                    assertNotNull(node.key() + " is still there", now);
                    assertEquals(example + " " + node.key() + ": notes, icons, attributes, clouds, edges, hooks", ownContent(original, node),
                            ownContent(saved, now));
                    if (startTagEdited.contains(node.key())) {
                        assertEquals(node.key().toString(), strip(node.ranges().startTag().of(original)), strip(now.ranges().startTag().of(saved)));
                    } else {
                        assertEquals(example + " " + node.key(), node.ranges().startTag().of(original), now.ranges().startTag().of(saved));
                    }
                }
                assertEquals("one node was added", before.nodesByKey().size() + 1, after.nodesByKey().size());
                assertEquals("Renamed é", after.node(renamed.key()).text());
                assertTrue("new text is written as FreeMind writes it", saved.contains("Renamed &#xe9;"));
                assertEquals(before.arrowLinks().size(), after.arrowLinks().size());
            }
        }
    }

    /** The node's element without its start tag and its child nodes: what the designer does not edit. */
    private static String ownContent(String text, MapNode node) {
        NodeRanges ranges = node.ranges();
        StringBuilder content = new StringBuilder();
        int from = ranges.startTag().end();
        for (MapNode child : node.children()) {
            Range element = child.ranges().element();
            content.append(text, from, element.offset());
            from = element.end();
        }
        content.append(text, Math.min(from, ranges.element().end()), ranges.element().end());
        return content.toString().replaceAll("\\s+", " ");
    }

    /** A start tag without the attributes an edit sets. */
    private static String strip(String startTag) {
        return startTag.replaceAll(" (MODIFIED|TEXT|FOLDED)=\"[^\"]*\"", "");
    }

    private static String prologue(String text, MindMap map) {
        return text.substring(0, map.root().ranges().element().offset());
    }

    private static String epilogue(String text, MindMap map) {
        return text.substring(map.root().ranges().element().end());
    }
}
