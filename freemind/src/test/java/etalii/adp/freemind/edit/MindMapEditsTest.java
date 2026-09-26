package etalii.adp.freemind.edit;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.edit.FreeMindConventionsTest.sequence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import etalii.adp.freemind.FreeMindAsserts;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.edit.MindMapEdits.Placement;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.core.xml.Range;
import etalii.adp.freemind.parse.MindMapParser;

class MindMapEditsTest {

    private static final long NOW = 100;

    /** Written as FreeMind 1.0.1 writes a map: no indentation, one element per line. */
    static final String MAP = """
            <map version="1.0.1">
            <node CREATED="1" ID="ID_1" MODIFIED="1" TEXT="Root">
            <node CREATED="2" ID="ID_2" MODIFIED="2" POSITION="right" TEXT="A">
            <node CREATED="3" ID="ID_3" MODIFIED="3" TEXT="A1"/>
            <node CREATED="4" ID="ID_4" MODIFIED="4" TEXT="A2"/>
            </node>
            <node CREATED="5" ID="ID_5" MODIFIED="5" POSITION="left" TEXT="B">
            <arrowlink DESTINATION="ID_3" ENDARROW="Default" ID="Arrow_ID_1" STARTARROW="None"/>
            <richcontent TYPE="NOTE"><html><head></head><body><p>note</p></body></html></richcontent>
            </node>
            <node CREATED="6" ID="ID_6" MODIFIED="6" POSITION="right" TEXT="C"/>
            </node>
            </map>
            """;

    private static final String NEW_NODE = "<node CREATED=\"100\" ID=\"ID_77\" MODIFIED=\"100\" TEXT=\"new\"/>\n";

    @Test
    void addChildExpandsASelfClosingParent() throws Exception {
        Edit edit = MindMapEdits.addChild(parse(MAP), key("ID_3"), "new", NOW, sequence(77));

        assertEquals("Add Child Node", edit.label());
        assertEquals(key("ID_77"), edit.created());
        assertEquals(MAP.replace("<node CREATED=\"3\" ID=\"ID_3\" MODIFIED=\"3\" TEXT=\"A1\"/>\n",
                "<node CREATED=\"3\" ID=\"ID_3\" MODIFIED=\"100\" TEXT=\"A1\">\n" + NEW_NODE + "</node>\n"), apply(MAP, edit));
    }

    @Test
    void addChildGoesLastAndRefreshesTheParentsModified() throws Exception {
        Edit edit = MindMapEdits.addChild(parse(MAP), key("ID_2"), "new", NOW, sequence(77));

        assertEquals(MAP
                .replace("MODIFIED=\"2\"", "MODIFIED=\"100\"")
                .replace("TEXT=\"A2\"/>\n", "TEXT=\"A2\"/>\n" + NEW_NODE), apply(MAP, edit));
    }

    @Test
    void addChildToTheRootGetsAPositionOnTheLighterSide() throws Exception {
        Edit edit = MindMapEdits.addChild(parse(MAP), key("ID_1"), "new", NOW, sequence(77));

        assertEquals(MAP
                .replace("MODIFIED=\"1\"", "MODIFIED=\"100\"")
                .replace("TEXT=\"C\"/>\n", "TEXT=\"C\"/>\n<node CREATED=\"100\" ID=\"ID_77\" MODIFIED=\"100\" POSITION=\"left\" TEXT=\"new\"/>\n"),
                apply(MAP, edit));
    }

    @Test
    void addSiblingGoesRightAfterTheNode() throws Exception {
        Edit edit = MindMapEdits.addSibling(parse(MAP), key("ID_3"), "new", NOW, sequence(77));

        assertEquals("Add Sibling Node", edit.label());
        assertEquals(MAP.replace("TEXT=\"A1\"/>\n", "TEXT=\"A1\"/>\n" + NEW_NODE), apply(MAP, edit));
    }

    @Test
    void addSiblingOnTheFirstLevelTakesTheSameSide() throws Exception {
        Edit edit = MindMapEdits.addSibling(parse(MAP), key("ID_6"), "new", NOW, sequence(77));

        assertEquals(MAP.replace("TEXT=\"C\"/>\n",
                "TEXT=\"C\"/>\n<node CREATED=\"100\" ID=\"ID_77\" MODIFIED=\"100\" POSITION=\"right\" TEXT=\"new\"/>\n"), apply(MAP, edit));
    }

    @Test
    void addSiblingOfTheRootIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> MindMapEdits.addSibling(parse(MAP), key("ID_1"), "new", NOW, sequence(77)));
    }

    @Test
    void renameEscapesTheTextAndRefreshesModified() throws Exception {
        Edit edit = MindMapEdits.rename(parse(MAP), key("ID_3"), "x & <y> \u00e9", NOW);

        assertEquals("Rename Node", edit.label());
        assertEquals(MAP.replace("<node CREATED=\"3\" ID=\"ID_3\" MODIFIED=\"3\" TEXT=\"A1\"/>",
                "<node CREATED=\"3\" ID=\"ID_3\" MODIFIED=\"100\" TEXT=\"x &amp; &lt;y&gt; &#xe9;\"/>"), apply(MAP, edit));
    }

    @Test
    void renamingARichNodeReplacesItsRichContentWithText() throws Exception {
        String text = """
                <map version="1.0.1">
                <node CREATED="7" ID="ID_7" MODIFIED="7">
                <richcontent TYPE="NODE"><html><head></head><body><p>Rich</p></body></html></richcontent>
                <richcontent TYPE="NOTE"><html><head></head><body><p>kept</p></body></html></richcontent>
                </node>
                </map>
                """;

        Edit edit = MindMapEdits.rename(parse(text), key("ID_7"), "plain", NOW);

        assertEquals("""
                <map version="1.0.1">
                <node CREATED="7" ID="ID_7" MODIFIED="100" TEXT="plain">
                <richcontent TYPE="NOTE"><html><head></head><body><p>kept</p></body></html></richcontent>
                </node>
                </map>
                """, apply(text, edit));
    }

    @Test
    void deleteRemovesTheNodeAndArrowLinksTargetingIt() throws Exception {
        Edit edit = MindMapEdits.delete(parse(MAP), List.of(key("ID_3")));

        assertEquals("Delete Node", edit.label());
        assertEquals(MAP
                .replace("<node CREATED=\"3\" ID=\"ID_3\" MODIFIED=\"3\" TEXT=\"A1\"/>\n", "")
                .replace("<arrowlink DESTINATION=\"ID_3\" ENDARROW=\"Default\" ID=\"Arrow_ID_1\" STARTARROW=\"None\"/>\n", ""),
                apply(MAP, edit));
    }

    @Test
    void deleteRemovesWholeSubtreesAndLinksIntoThem() throws Exception {
        Edit edit = MindMapEdits.delete(parse(MAP), List.of(key("ID_2"), key("ID_4"), key("ID_6")));

        assertEquals("Delete Nodes", edit.label());
        assertEquals("""
                <map version="1.0.1">
                <node CREATED="1" ID="ID_1" MODIFIED="1" TEXT="Root">
                <node CREATED="5" ID="ID_5" MODIFIED="5" POSITION="left" TEXT="B">
                <richcontent TYPE="NOTE"><html><head></head><body><p>note</p></body></html></richcontent>
                </node>
                </node>
                </map>
                """, apply(MAP, edit));
    }

    @Test
    void deletingTheRootIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> MindMapEdits.delete(parse(MAP), List.of(key("ID_1"))));
    }

    @Test
    void moveUpAndDownSwapSiblings() throws Exception {
        String swapped = MAP.replace(
                "<node CREATED=\"3\" ID=\"ID_3\" MODIFIED=\"3\" TEXT=\"A1\"/>\n<node CREATED=\"4\" ID=\"ID_4\" MODIFIED=\"4\" TEXT=\"A2\"/>\n",
                "<node CREATED=\"4\" ID=\"ID_4\" MODIFIED=\"4\" TEXT=\"A2\"/>\n<node CREATED=\"3\" ID=\"ID_3\" MODIFIED=\"3\" TEXT=\"A1\"/>\n");

        Edit up = MindMapEdits.move(parse(MAP), key("ID_4"), key("ID_3"), Placement.BEFORE);
        Edit down = MindMapEdits.move(parse(MAP), key("ID_3"), key("ID_4"), Placement.AFTER);

        assertEquals("Move Node", up.label());
        assertEquals(swapped, apply(MAP, up));
        assertEquals(swapped, apply(MAP, down));
    }

    @Test
    void moveIntoASelfClosingNodeExpandsIt() throws Exception {
        Edit edit = MindMapEdits.move(parse(MAP), key("ID_3"), key("ID_6"), Placement.INTO);

        assertEquals(MAP
                .replace("<node CREATED=\"3\" ID=\"ID_3\" MODIFIED=\"3\" TEXT=\"A1\"/>\n", "")
                .replace("TEXT=\"C\"/>\n", "TEXT=\"C\">\n<node CREATED=\"3\" ID=\"ID_3\" MODIFIED=\"3\" TEXT=\"A1\"/>\n</node>\n"),
                apply(MAP, edit));
    }

    @Test
    void movingToTheFirstLevelAddsPosition() throws Exception {
        Edit edit = MindMapEdits.move(parse(MAP), key("ID_3"), key("ID_2"), Placement.AFTER);

        assertEquals(MAP
                .replace("<node CREATED=\"3\" ID=\"ID_3\" MODIFIED=\"3\" TEXT=\"A1\"/>\n", "")
                .replace("TEXT=\"A2\"/>\n</node>\n", "TEXT=\"A2\"/>\n</node>\n<node CREATED=\"3\" ID=\"ID_3\" MODIFIED=\"3\" POSITION=\"right\" TEXT=\"A1\"/>\n"),
                apply(MAP, edit));
    }

    @Test
    void movingAwayFromTheFirstLevelRemovesPosition() throws Exception {
        Edit edit = MindMapEdits.move(parse(MAP), key("ID_6"), key("ID_2"), Placement.INTO);

        assertEquals(MAP
                .replace("<node CREATED=\"6\" ID=\"ID_6\" MODIFIED=\"6\" POSITION=\"right\" TEXT=\"C\"/>\n", "")
                .replace("TEXT=\"A2\"/>\n", "TEXT=\"A2\"/>\n<node CREATED=\"6\" ID=\"ID_6\" MODIFIED=\"6\" TEXT=\"C\"/>\n"),
                apply(MAP, edit));
    }

    @Test
    void movesIntoTheOwnSubtreeOrOfTheRootAreRefused() throws Exception {
        MindMap map = parse(MAP);

        assertNull(MindMapEdits.move(map, key("ID_2"), key("ID_3"), Placement.INTO));
        assertNull(MindMapEdits.move(map, key("ID_2"), key("ID_2"), Placement.INTO));
        assertNull(MindMapEdits.move(map, key("ID_1"), key("ID_5"), Placement.INTO));
        assertNull(MindMapEdits.move(map, key("ID_5"), key("ID_1"), Placement.AFTER));
    }

    @Test
    void foldInsertsAndUnfoldRemovesFolded() throws Exception {
        Edit fold = MindMapEdits.setFolded(parse(MAP), key("ID_2"), true);

        assertEquals("Fold Branch", fold.label());
        String folded = apply(MAP, fold);
        assertEquals(MAP.replace("<node CREATED=\"2\" ID=\"ID_2\"", "<node CREATED=\"2\" FOLDED=\"true\" ID=\"ID_2\""), folded);

        Edit unfold = MindMapEdits.setFolded(parse(folded), key("ID_2"), false);
        assertEquals("Unfold Branch", unfold.label());
        assertEquals(MAP, apply(folded, unfold));

        assertNull(MindMapEdits.setFolded(parse(MAP), key("ID_3"), true), "a leaf has nothing to fold");
        assertNull(MindMapEdits.setFolded(parse(MAP), key("ID_2"), false), "already unfolded");
    }

    @ParameterizedTest
    @MethodSource("etalii.adp.freemind.FreeMindAsserts#examples")
    void editsOnRealMapsChangeOnlyTheEditedRange(Path example) throws Exception {
        String text = FreeMindAsserts.read(example);
        MindMap map = parse(text);
        MapNode root = map.root();

        Edit rename = MindMapEdits.rename(map, root.key(), "Renamed", NOW);
        Range changed = changedRange(text, apply(text, rename));
        Range allowed = root.ranges().richNode() == null ? root.ranges().startTag() : span(root.ranges().startTag(), root.ranges().richNode());
        assertTrue(allowed.offset() <= changed.offset() && changed.end() <= allowed.end(), example + ": " + changed + " outside " + allowed);

        MapNode branch = map.nodesByKey().values().stream().filter(n -> !n.children().isEmpty() && n != root).findFirst().orElse(null);
        if (branch != null) {
            Edit fold = MindMapEdits.setFolded(map, branch.key(), !branch.folded());
            Range folded = changedRange(text, apply(text, fold));
            Range tag = branch.ranges().startTag();
            assertTrue(tag.offset() <= folded.offset() && folded.end() <= tag.end(), example + ": " + folded + " outside " + tag);
        }
    }

    @ParameterizedTest
    @MethodSource("etalii.adp.freemind.FreeMindAsserts#examples")
    void writtenFormsMatchTheFreeMind101Examples(Path example) throws Exception {
        String text = FreeMindAsserts.read(example);
        MindMap map = parse(text);
        if (!map.version().equals("1.0.1")) {
            return;
        }
        Matcher tags = Pattern.compile("<node( [A-Z_]+=\"[^\"]*\")*").matcher(text);
        while (tags.find()) {
            List<String> names = Pattern.compile(" ([A-Z_]+)=").matcher(tags.group()).results().map(r -> r.group(1)).toList();
            assertEquals(names.stream().sorted().toList(), names, "FreeMind 1.0.1 writes attributes in alphabetical order");
        }

        String added = apply(text, MindMapEdits.addChild(map, map.root().key(), "new", NOW, sequence(77)));
        assertTrue(Pattern.compile("<node CREATED=\"100\" ID=\"ID_77\" MODIFIED=\"100\" POSITION=\"(left|right)\" TEXT=\"new\"/>")
                .matcher(added).find(), added);
    }

    private static final String C_TAG = "<node CREATED=\"6\" ID=\"ID_6\" MODIFIED=\"6\" POSITION=\"right\" TEXT=\"C\"/>";

    @Test
    void setAttributeInsertsWhereAlphabeticalOrderPutsIt() throws Exception {
        MindMap map = parse(MAP);
        Edit colour = MindMapEdits.setAttribute(map, key("ID_6"), "COLOR", "#ff0000");
        Edit link = MindMapEdits.setAttribute(map, key("ID_6"), "LINK", "a & b.mm");

        assertEquals(MindMapEdits.SET_ATTRIBUTE, colour.label());
        assertEquals(MAP.replace(C_TAG, C_TAG.replace("<node CREATED", "<node COLOR=\"#ff0000\" CREATED")), apply(MAP, colour));
        assertEquals(MAP.replace(C_TAG, C_TAG.replace("MODIFIED=", "LINK=\"a &amp; b.mm\" MODIFIED=")), apply(MAP, link));
        assertOnlyInside(MAP, apply(MAP, colour), map.node(key("ID_6")).ranges().startTag());
    }

    @Test
    void setAttributeReplacesOnlyTheValue() throws Exception {
        String coloured = MAP.replace(C_TAG, C_TAG.replace("<node CREATED", "<node COLOR=\"#ff0000\" CREATED"));
        MindMap map = parse(coloured);
        Edit edit = MindMapEdits.setAttribute(map, key("ID_6"), "COLOR", "#00ff00");

        assertEquals(coloured.replace("#ff0000", "#00ff00"), apply(coloured, edit));
        assertOnlyInside(coloured, apply(coloured, edit), map.node(key("ID_6")).ranges().attribute("COLOR").value());
        assertNull(MindMapEdits.setAttribute(map, key("ID_6"), "COLOR", "#ff0000"), "the value is already set");
    }

    @Test
    void setAttributeToNothingRemovesIt() throws Exception {
        String coloured = MAP.replace(C_TAG, C_TAG.replace("<node CREATED", "<node COLOR=\"#ff0000\" CREATED"));
        MindMap map = parse(coloured);

        assertEquals(MAP, apply(coloured, MindMapEdits.setAttribute(map, key("ID_6"), "COLOR", null)));
        assertEquals(MAP, apply(coloured, MindMapEdits.setAttribute(map, key("ID_6"), "COLOR", "")));
        assertNull(MindMapEdits.setAttribute(parse(MAP), key("ID_6"), "COLOR", ""), "nothing to remove");
    }

    @Test
    void addArrowLinkExpandsASelfClosingSource() throws Exception {
        MindMap map = parse(MAP);
        Edit edit = MindMapEdits.addArrowLink(map, key("ID_6"), key("ID_4"), sequence(77));

        assertEquals(MindMapEdits.ADD_ARROW_LINK, edit.label());
        assertEquals(MAP.replace(C_TAG + "\n", C_TAG.replace("/>", ">") + "\n"
                + "<arrowlink DESTINATION=\"ID_4\" ENDARROW=\"Default\" ID=\"Arrow_ID_77\" STARTARROW=\"None\"/>\n</node>\n"), apply(MAP, edit));
        MindMap after = parse(apply(MAP, edit));
        assertEquals(2, after.arrowLinks().size());
        assertEquals(key("ID_6"), after.arrowLinks().get(1).source());
        assertEquals("ID_4", after.arrowLinks().get(1).destinationId());
    }

    @Test
    void addArrowLinkGoesAfterTheSourcesLinksOrBeforeItsChildren() throws Exception {
        MindMap map = parse(MAP);
        String link = "<arrowlink DESTINATION=\"ID_6\" ENDARROW=\"Default\" ID=\"Arrow_ID_77\" STARTARROW=\"None\"/>\n";

        String afterLinks = apply(MAP, MindMapEdits.addArrowLink(map, key("ID_5"), key("ID_6"), sequence(77)));
        assertEquals(MAP.replace("STARTARROW=\"None\"/>\n", "STARTARROW=\"None\"/>\n" + link), afterLinks);

        String beforeChildren = apply(MAP, MindMapEdits.addArrowLink(map, key("ID_2"), key("ID_6"), sequence(77)));
        assertEquals(MAP.replace("<node CREATED=\"3\"", link + "<node CREATED=\"3\""), beforeChildren);
        assertEquals(key("ID_2"), parse(beforeChildren).arrowLinks().get(0).source());
    }

    @Test
    void addArrowLinkNeedsADestinationWithAnId() throws Exception {
        String noId = MAP.replace("CREATED=\"4\" ID=\"ID_4\" ", "CREATED=\"4\" ");
        MindMap map = parse(noId);
        MapNode a2 = map.node(key("ID_2")).children().get(1);

        assertNull(MindMapEdits.addArrowLink(map, key("ID_6"), a2.key(), sequence(77)));
    }

    @Test
    void removeArrowLinkTakesItsLine() throws Exception {
        MindMap map = parse(MAP);
        Edit edit = MindMapEdits.removeArrowLink(map, map.arrowLinks().get(0));

        assertEquals(MindMapEdits.REMOVE_ARROW_LINK, edit.label());
        assertEquals(MAP.replace("<arrowlink DESTINATION=\"ID_3\" ENDARROW=\"Default\" ID=\"Arrow_ID_1\" STARTARROW=\"None\"/>\n", ""), apply(MAP, edit));
        Range line = map.arrowLinks().get(0).range();
        assertEquals(MAP.substring(0, line.offset()) + MAP.substring(line.end()), apply(MAP, edit), "exactly the link's range goes");
    }

    /** The text changed only within {@code allowed}. */
    private static void assertOnlyInside(String before, String after, Range allowed) {
        Range changed = changedRange(before, after);
        assertTrue(allowed.offset() <= changed.offset() && changed.end() <= allowed.end(), changed + " outside " + allowed);
    }

    static MindMap parse(String text) throws Exception {
        return MindMapParser.parse(text);
    }

    static String apply(String text, Edit edit) throws Exception {
        assertNotNull(edit);
        return edit.changes().applyTo(text);
    }

    private static Range changedRange(String before, String after) {
        int prefix = 0;
        while (prefix < before.length() && prefix < after.length() && before.charAt(prefix) == after.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < before.length() - prefix && suffix < after.length() - prefix
                && before.charAt(before.length() - 1 - suffix) == after.charAt(after.length() - 1 - suffix)) {
            suffix++;
        }
        return new Range(prefix, before.length() - suffix - prefix);
    }

    private static Range span(Range a, Range b) {
        int start = Math.min(a.offset(), b.offset());
        return new Range(start, Math.max(a.end(), b.end()) - start);
    }
}
