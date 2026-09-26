package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import etalii.adp.core.TextChanges;
import etalii.adp.core.diagram.AddRequest;
import etalii.adp.core.diagram.DiagramRules;
import etalii.adp.core.diagram.Placement;
import etalii.adp.core.diagram.model.Connection;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;
import etalii.adp.core.diagram.model.End;
import etalii.adp.freemind.FreeMindAsserts;
import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.model.ArrowLink;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.parse.MindMapParser;
import etalii.adp.freemind.ui.FreeMindMapping.ArrowKey;
import etalii.adp.freemind.ui.FreeMindMapping.BranchKey;

/** T086: the diagram FreeMind maps are read as, and the {@link MindMapEdits} call each request becomes. */
class FreeMindMappingTest {

    private static final long NOW = 100;
    private static final long SEED = 7;

    static final String MAP = """
            <map version="1.0.1">
            <node CREATED="1" ID="R" MODIFIED="1" TEXT="Root">
            <node CREATED="2" ID="A" MODIFIED="2" POSITION="right" TEXT="A">
            <node CREATED="3" ID="A1" MODIFIED="3" TEXT="A1"/>
            </node>
            <node BACKGROUND_COLOR="#ffffcc" COLOR="#000080" CREATED="4" FOLDED="true" ID="B" LINK="#A" MODIFIED="4" POSITION="left" TEXT="B">
            <arrowlink DESTINATION="A1" ENDARROW="Default" ID="Arrow_ID_1" STARTARROW="None"/>
            <arrowlink DESTINATION="NOWHERE" ENDARROW="Default" ID="Arrow_ID_2" STARTARROW="None"/>
            <node CREATED="5" ID="B1" MODIFIED="5" TEXT="B1"/>
            </node>
            <node CREATED="6" ID="H" MODIFIED="6" POSITION="right" STYLE="bubble">
            <richcontent TYPE="NODE"><html><head></head><body><p>Hello <b>rich</b> world</p></body></html></richcontent>
            </node>
            </node>
            </map>
            """;

    private final FreeMindMapping mapping = new FreeMindMapping(() -> NOW, () -> new Random(SEED));

    @ParameterizedTest
    @MethodSource("etalii.adp.freemind.FreeMindAsserts#examples")
    void everyExampleIsReadAsTheNodeTreeWithItsBranchesAndLinks(Path example) throws Exception {
        String text = FreeMindAsserts.read(example);
        MindMap map = MindMapParser.parse(text);
        Diagram diagram = mapping.read(text);

        List<NodeKey> inOrder = new ArrayList<>();
        collect(map.root(), inOrder);
        assertEquals(inOrder, List.copyOf(diagram.elements().keySet()), "every node, in document order");
        assertEquals(FreeMindDefinition.ROOT, diagram.element(map.root().key()).type());
        for (MapNode node : map.nodesByKey().values()) {
            Element element = diagram.element(node.key());
            assertEquals(node.text() == null ? "" : node.text(), element.property(FreeMindMapping.TEXT), node.toString());
            assertEquals(node.id() == null ? "" : node.id(), element.property(FreeMindMapping.ID), node.toString());
            assertNull(element.bounds(), "laid out, never stored");
            if (node.parent() != null) {
                assertEquals(node.parent().key(), element.parent());
                Connection branch = diagram.connection(new BranchKey(node.key()));
                assertNotNull(branch, node.toString());
                assertEquals(FreeMindDefinition.BRANCH, branch.type());
                assertEquals(node.parent().key(), branch.source().elementKey());
                assertEquals(node.key(), branch.target().elementKey());
            }
        }
        long drawnLinks = map.arrowLinks().stream().filter(link -> map.nodeById(link.destinationId()) != null).count();
        long links = diagram.connections().values().stream().filter(c -> c.type().equals(FreeMindDefinition.ARROW_LINK)).count();
        assertEquals(drawnLinks, links, "a link to a missing node stays in the file and is not a connection");
        assertEquals(map.nodesByKey().size() - 1 + links, diagram.connections().size());
    }

    @Test
    void propertiesStylesAndLinksAreRead() throws Exception {
        Diagram diagram = mapping.read(MAP);

        Element b = diagram.element(key("B"));
        assertEquals(FreeMindDefinition.NODE, b.type());
        assertEquals("#000080", b.property(FreeMindMapping.COLOR));
        assertEquals("#ffffcc", b.property(FreeMindMapping.BACKGROUND_COLOR));
        assertEquals("true", b.property(FreeMindMapping.FOLDED));
        assertEquals("#A", b.property(FreeMindMapping.LINK));
        assertEquals("false", diagram.element(key("A")).property(FreeMindMapping.FOLDED));
        assertEquals(FreeMindDefinition.BUBBLE, diagram.element(key("H")).type(), "STYLE=\"bubble\"");
        assertNotNull(b.style());
        assertEquals(0x000080, b.style().text().getRGB() & 0xFFFFFF);
        assertEquals(0xffffcc, b.style().fill().getRGB() & 0xFFFFFF);

        Connection link = diagram.connection(new ArrowKey(key("B"), 0));
        assertNotNull(link);
        assertEquals(FreeMindDefinition.ARROW_LINK, link.type());
        assertEquals(new End(key("B"), null), link.source());
        assertEquals(new End(key("A1"), null), link.target());
        assertNull(diagram.connection(new ArrowKey(key("B"), 1)), "its destination does not exist");
        assertEquals(new End(key("R"), FreeMindDefinition.LEFT), diagram.connection(new BranchKey(key("B"))).source(), "a left branch leaves on the left");
        assertEquals(new End(key("B"), FreeMindDefinition.RIGHT), diagram.connection(new BranchKey(key("B"))).target());
    }

    @Test
    void aRichNodesTextIsReadOnly() throws Exception {
        Diagram diagram = mapping.read(MAP);
        DiagramRules rules = FreeMindDefinition.DEFINITION.rules();

        assertEquals("Hello rich world", diagram.element(key("H")).property(FreeMindMapping.TEXT));
        assertFalse(rules.canSetProperty(diagram, key("H"), FreeMindMapping.TEXT).allowed());
        assertTrue(rules.canSetProperty(diagram, key("H"), FreeMindMapping.COLOR).allowed());
        assertTrue(rules.canSetProperty(diagram, key("A"), FreeMindMapping.TEXT).allowed());
    }

    @Test
    void anAddOntoANodeIsAddChild() throws Exception {
        Diagram diagram = mapping.read(MAP);
        TextChanges changes = mapping.add(MAP, diagram,
                new AddRequest(FreeMindDefinition.NODE, null, key("A1"), null, Map.of(FreeMindMapping.TEXT, "New Node")));

        assertEquals(expected(m -> MindMapEdits.addChild(m, key("A1"), "New Node", NOW, new Random(SEED))), changes.applyTo(MAP));
        assertEquals(MindMapEdits.ADD_CHILD, mapping.labelOf(changes));
        assertTrue(mapping.add(MAP, diagram, new AddRequest(FreeMindDefinition.NODE, null, null, null, Map.of())).isEmpty(), "a node needs a parent");
    }

    @Test
    void removeIsDeleteAndLinksAreRemovedOnTheirOwn() throws Exception {
        Diagram diagram = mapping.read(MAP);
        MindMap map = MindMapParser.parse(MAP);

        TextChanges node = mapping.remove(MAP, diagram, Set.of(key("A"), new BranchKey(key("A")), new BranchKey(key("A1")), new ArrowKey(key("B"), 0)));
        assertEquals(MindMapEdits.delete(map, List.of(key("A"))).changes().applyTo(MAP), node.applyTo(MAP), "a link into the deleted branch goes with it");
        assertEquals(MindMapEdits.DELETE_NODE, mapping.labelOf(node));

        TextChanges link = mapping.remove(MAP, diagram, Set.of(new ArrowKey(key("B"), 0)));
        assertEquals(MindMapEdits.removeArrowLink(map, map.arrowLinks().get(0)).changes().applyTo(MAP), link.applyTo(MAP));
    }

    @Test
    void propertiesAreRenameFoldAndAttributes() throws Exception {
        Diagram diagram = mapping.read(MAP);

        TextChanges rename = mapping.setProperty(MAP, diagram, Set.of(key("A1")), FreeMindMapping.TEXT, "x & y");
        assertEquals(expected(m -> MindMapEdits.rename(m, key("A1"), "x & y", NOW)), rename.applyTo(MAP));
        assertEquals(MindMapEdits.RENAME, mapping.labelOf(rename));

        TextChanges fold = mapping.setProperty(MAP, diagram, Set.of(key("A")), FreeMindMapping.FOLDED, "true");
        assertEquals(expected(m -> MindMapEdits.setFolded(m, key("A"), true)), fold.applyTo(MAP));
        assertEquals(MindMapEdits.FOLD, mapping.labelOf(fold));
        TextChanges unfold = mapping.setProperty(MAP, diagram, Set.of(key("B")), FreeMindMapping.FOLDED, "false");
        assertEquals(expected(m -> MindMapEdits.setFolded(m, key("B"), false)), unfold.applyTo(MAP));

        assertEquals(expected(m -> MindMapEdits.setAttribute(m, key("A"), "COLOR", "#ff0000")),
                mapping.setProperty(MAP, diagram, Set.of(key("A")), FreeMindMapping.COLOR, "#FF0000").applyTo(MAP), "written as FreeMind writes colours");
        assertEquals(expected(m -> MindMapEdits.setAttribute(m, key("B"), "BACKGROUND_COLOR", null)),
                mapping.setProperty(MAP, diagram, Set.of(key("B")), FreeMindMapping.BACKGROUND_COLOR, "").applyTo(MAP));
        assertEquals(expected(m -> MindMapEdits.setAttribute(m, key("A"), "LINK", "https://example.com")),
                mapping.setProperty(MAP, diagram, Set.of(key("A")), FreeMindMapping.LINK, "https://example.com").applyTo(MAP));
    }

    @Test
    void aDropIsAMoveWithItsPlacement() throws Exception {
        Diagram diagram = mapping.read(MAP);

        TextChanges into = mapping.drop(MAP, diagram, Set.of(key("A1")), key("B"), Placement.INTO);
        assertEquals(expected(m -> MindMapEdits.move(m, key("A1"), key("B"), MindMapEdits.Placement.INTO)), into.applyTo(MAP));
        assertEquals(MindMapEdits.MOVE, mapping.labelOf(into));
        assertEquals(key("B"), mapping.unfoldFor(into), "a folded target opens for display");

        TextChanges before = mapping.drop(MAP, diagram, Set.of(key("B1")), key("A1"), Placement.BEFORE);
        assertEquals(expected(m -> MindMapEdits.move(m, key("B1"), key("A1"), MindMapEdits.Placement.BEFORE)), before.applyTo(MAP));
        assertTrue(mapping.drop(MAP, diagram, Set.of(key("A")), key("A1"), Placement.INTO).isEmpty(), "not into its own branch");
    }

    @Test
    void aConnectionIsANewArrowLinkAndBoundsAreNeverWritten() throws Exception {
        Diagram diagram = mapping.read(MAP);

        TextChanges connect = mapping.connect(MAP, diagram, FreeMindDefinition.ARROW_LINK, new End(key("A1"), FreeMindDefinition.RIGHT),
                new End(key("B1"), FreeMindDefinition.LEFT));
        assertEquals(expected(m -> MindMapEdits.addArrowLink(m, key("A1"), key("B1"), new Random(SEED))), connect.applyTo(MAP));
        assertTrue(mapping.setBounds(MAP, diagram, List.of()).isEmpty());
    }

    @Test
    void theArrowKeyOfALinkIsItsPlaceAmongItsSourcesLinks() throws Exception {
        MindMap map = MindMapParser.parse(MAP);
        List<ArrowLink> links = map.arrowLinks();

        assertEquals(new ArrowKey(key("B"), 0), FreeMindMapping.keyOf(map, links.get(0)));
        assertEquals(new ArrowKey(key("B"), 1), FreeMindMapping.keyOf(map, links.get(1)));
    }

    private static String expected(java.util.function.Function<MindMap, Edit> edit) throws Exception {
        return edit.apply(MindMapParser.parse(MAP)).changes().applyTo(MAP);
    }

    private static void collect(MapNode node, List<NodeKey> keys) {
        keys.add(node.key());
        node.children().forEach(child -> collect(child, keys));
    }
}
