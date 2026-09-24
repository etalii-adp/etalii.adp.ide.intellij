package etalii.adp.freemind.ui;

import static etalii.adp.freemind.ui.AddNodeTest.ADD_CHILD;
import static etalii.adp.freemind.ui.AddNodeTest.cancelInPlace;
import static etalii.adp.freemind.ui.FoldTest.TOGGLE_FOLD;
import static etalii.adp.freemind.ui.RenameTest.RENAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.model.NodeRanges;
import etalii.adp.freemind.model.Range;
import etalii.adp.freemind.parse.MindMapParser;
import etalii.adp.testing.DesignerDriver;

/**
 * FR-010, SC-004, US2-AS6: after a mixed sequence of edits and a save, everything the designer does
 * not display (notes, icons, attributes, clouds, edge styles, unknown elements) is unchanged, and
 * only the edited nodes' start tags differ.
 */
class PreservationTest {

    @ParameterizedTest
    @MethodSource("etalii.adp.freemind.MindMapAsserts#examples")
    void mixedEditsChangeOnlyTheEditedNodes(Path example) throws Exception {
        try (var d = DesignerDriver.open(example, MindMapEditor.ID)) {
            MindMapEditor editor = (MindMapEditor) d.editor();
            MindMap before = editor.model();
            assertNotNull(before, example.toString());
            List<NodeKey> visible = editor.viewState().visibleKeys();
            MapNode root = before.root();

            // Add a child to the root: the root's MODIFIED changes, the new node is its last child.
            d.select(root.key()).run(ADD_CHILD);
            cancelInPlace(d);

            // Rename a plain node, and fold or unfold a branch that is not the root.
            MapNode renamed = visible.stream().map(before::node).filter(n -> n != root && !n.rich()).findFirst().orElseThrow();
            d.select(renamed.key()).run(RENAME).typeInPlace("Renamed \u00e9");
            MapNode folded = visible.stream().map(before::node).filter(n -> n != root && n != renamed && !n.children().isEmpty())
                    .findFirst().orElse(null);
            if (folded != null) {
                d.select(folded.key()).run(TOGGLE_FOLD);
            }

            String saved = new String(d.savedBytes(), UTF_8);
            MindMap after = MindMapParser.parse(saved);
            String original = before.text();

            Set<NodeKey> startTagEdited = new HashSet<>(Set.of(root.key(), renamed.key()));
            if (folded != null) {
                startTagEdited.add(folded.key());
            }
            assertEquals(prologue(original, before), prologue(saved, after), "text before the root");
            assertEquals(epilogue(original, before), epilogue(saved, after), "text after the root");
            for (MapNode node : before.nodesByKey().values()) {
                MapNode now = after.node(node.key());
                assertNotNull(now, node.key() + " is still there");
                assertEquals(ownContent(original, node), ownContent(saved, now), node.key() + ": notes, icons, attributes, clouds, edges");
                if (startTagEdited.contains(node.key())) {
                    assertEquals(strip(node.ranges().startTag().of(original)), strip(now.ranges().startTag().of(saved)), node.key().toString());
                } else {
                    assertEquals(node.ranges().startTag().of(original), now.ranges().startTag().of(saved), node.key().toString());
                }
            }
            assertEquals(before.nodesByKey().size() + 1, after.nodesByKey().size(), "one node was added");
            assertEquals("Renamed \u00e9", after.node(renamed.key()).text());
            assertTrue(saved.contains("Renamed &#xe9;"), "new text is written as FreeMind writes it");
            assertEquals(before.arrowLinks().size(), after.arrowLinks().size());
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
        return content.toString();
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
