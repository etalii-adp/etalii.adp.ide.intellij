package etalii.adp.freemind.parse;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.FreeMindAsserts.keyPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import etalii.adp.core.FormatProblem;
import etalii.adp.core.Rgb;
import etalii.adp.freemind.FreeMindAsserts;
import etalii.adp.freemind.model.ArrowLink;
import etalii.adp.freemind.model.FontSpec;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.Side;

class MindMapParserTest {

    static final String MAP = """
            <map version="1.0.1">
            <!-- To view this file, download free mind mapping software FreeMind from http://freemind.sourceforge.net -->
            <node CREATED="1000" ID="ID_1" MODIFIED="2000" TEXT="Root">
            <node BACKGROUND_COLOR="#00ff00" COLOR="#ff0000" CREATED="1001" FOLDED="true" HGAP="10" ID="ID_2" LINK="http://example.org" MODIFIED="2001" POSITION="left" TEXT="Left &amp; &#xe9;" VGAP="5" VSHIFT="-3">
            <font BOLD="true" ITALIC="true" NAME="Serif" SIZE="14"/>
            <icon BUILTIN="idea"/>
            <icon BUILTIN="button_ok"/>
            <arrowlink DESTINATION="ID_3" ENDARROW="Default" ID="Arrow_ID_1" STARTARROW="None"/>
            <node CREATED="1002" ID="ID_4" MODIFIED="2002" TEXT="Child"/>
            <attribute NAME="k" VALUE="v"/>
            <cloud/>
            </node>
            <node CREATED="1003" ID="ID_3" MODIFIED="2003" POSITION="right">
            <richcontent TYPE="NODE"><html><head></head><body><p>Rich <b>text</b></p></body></html></richcontent>
            <richcontent TYPE="NOTE"><html><head></head><body><p>A note</p></body></html></richcontent>
            </node>
            <node TEXT="No id"/>
            </node>
            </map>
            """;

    @Test
    void readsTheFieldsOfTheDataModel() throws Exception {
        MindMap map = MindMapParser.parse(MAP);

        assertEquals("1.0.1", map.version());
        assertEquals("\n", map.lineSeparator());
        assertEquals("", map.indentUnit());
        assertEquals(5, map.nodesByKey().size());

        MapNode root = map.root();
        assertEquals(key("ID_1"), root.key());
        assertEquals("Root", root.text());
        assertNull(root.parent());
        assertEquals(3, root.children().size());
        assertEquals(1000L, root.created());
        assertEquals(2000L, root.modified());

        MapNode left = map.node(key("ID_2"));
        assertSame(root, left.parent());
        assertEquals("Left & \u00e9", left.text());
        assertFalse(left.rich());
        assertEquals(Side.LEFT, left.side());
        assertTrue(left.folded());
        assertEquals(List.of("idea", "button_ok"), left.icons());
        assertEquals(new Rgb(255, 0, 0), left.color());
        assertEquals(new Rgb(0, 255, 0), left.backgroundColor());
        assertEquals(new FontSpec("Serif", 14, true, true), left.font());
        assertEquals("http://example.org", left.link());
        assertNull(left.note());
        assertEquals(10, left.hgap());
        assertEquals(5, left.vgap());
        assertEquals(-3, left.vshift());
        assertEquals(List.of(key("ID_4")), left.children().stream().map(MapNode::key).toList());

        MapNode rich = map.node(key("ID_3"));
        assertTrue(rich.rich());
        assertEquals("Rich text", rich.text());
        assertEquals("A note", rich.note());
        assertEquals(Side.RIGHT, rich.side());
        assertFalse(rich.folded());
        assertNotNull(rich.ranges().richNode());
        assertTrue(rich.ranges().richNode().of(MAP).startsWith("<richcontent TYPE=\"NODE\">"));

        MapNode plain = map.node(keyPath(0, 2));
        assertEquals("No id", plain.text());
        assertNull(plain.id());
        assertNull(plain.side());
        assertNull(plain.created());
    }

    @Test
    void readsArrowLinks() throws Exception {
        List<ArrowLink> links = MindMapParser.parse(MAP).arrowLinks();

        assertEquals(1, links.size());
        ArrowLink link = links.get(0);
        assertEquals(key("ID_2"), link.source());
        assertEquals("ID_3", link.destinationId());
        assertEquals("None", link.startArrow());
        assertEquals("Default", link.endArrow());
        assertEquals("<arrowlink DESTINATION=\"ID_3\" ENDARROW=\"Default\" ID=\"Arrow_ID_1\" STARTARROW=\"None\"/>\n", link.range().of(MAP));
    }

    @Test
    void unknownContentIsNotAFieldButStaysInsideTheNodesRange() throws Exception {
        MapNode left = MindMapParser.parse(MAP).node(key("ID_2"));

        String element = left.ranges().element().of(MAP);
        assertTrue(element.startsWith("<node BACKGROUND_COLOR"), element);
        assertTrue(element.contains("<attribute NAME=\"k\" VALUE=\"v\"/>"), element);
        assertTrue(element.contains("<cloud/>"), element);
        assertTrue(element.endsWith("</node>\n"), element);
        assertEquals("Left &amp; &#xe9;", left.ranges().attribute("TEXT").value().of(MAP));
    }

    @Test
    void wholeLineRangesIncludeIndentAndSeparator() throws Exception {
        String text = "<map version=\"1.0.1\">\r\n  <node TEXT=\"r\">\r\n    <node TEXT=\"c\"/>\r\n  </node>\r\n</map>\r\n";

        MindMap map = MindMapParser.parse(text);

        assertEquals("\r\n", map.lineSeparator());
        assertEquals("  ", map.indentUnit());
        MapNode child = map.node(keyPath(0, 0));
        assertEquals("    <node TEXT=\"c\"/>\r\n", child.ranges().element().of(text));
        assertEquals("    ", child.ranges().indent());
        assertTrue(child.ranges().ownLine());
        assertTrue(child.ranges().selfClosing());
    }

    @Test
    void theKeyIsTheIdOrTheIndexPath() throws Exception {
        MindMap map = MindMapParser.parse("<map><node><node/><node ID=\"X\"><node/></node></node></map>");

        assertEquals(keyPath(0), map.root().key());
        assertEquals(List.of(keyPath(0, 0), key("X")), map.root().children().stream().map(MapNode::key).toList());
        assertEquals(keyPath(0, 1, 0), map.node(key("X")).children().get(0).key());
    }

    @Test
    void malformedXmlIsAFormatProblemWithItsPosition() {
        String text = "<map>\n<node TEXT=\"a\">\n</map>\n";

        FormatProblem problem = assertThrows(FormatProblem.class, () -> MindMapParser.parse(text));

        assertTrue(problem.getOffset() > 0 && problem.getOffset() <= text.length(), "offset " + problem.getOffset());
    }

    @Test
    void aDoctypeIsRefused() {
        FormatProblem problem = assertThrows(FormatProblem.class,
                () -> MindMapParser.parse("<!DOCTYPE map [<!ENTITY x \"y\">]>\n<map><node TEXT=\"&x;\"/></map>"));

        assertTrue(problem.getMessage().contains("DOCTYPE"), problem.getMessage());
    }

    @Test
    void aRootOtherThanMapIsRefused() {
        FormatProblem problem = assertThrows(FormatProblem.class, () -> MindMapParser.parse("<mindmap><node/></mindmap>"));

        assertEquals(0, problem.getOffset());
        assertTrue(problem.getMessage().contains("<map>"), problem.getMessage());
    }

    @Test
    void aMapNeedsExactlyOneTopLevelNode() {
        assertThrows(FormatProblem.class, () -> MindMapParser.parse("<map version=\"1.0.1\"></map>"));
        assertThrows(FormatProblem.class, () -> MindMapParser.parse("<map/>"));

        String several = "<map><node TEXT=\"a\"/><node TEXT=\"b\"/></map>";
        FormatProblem problem = assertThrows(FormatProblem.class, () -> MindMapParser.parse(several));
        assertEquals(several.indexOf("<node TEXT=\"b\""), problem.getOffset());
    }

    @ParameterizedTest
    @MethodSource("etalii.adp.freemind.FreeMindAsserts#examples")
    void everyExampleParses(Path example) throws Exception {
        String text = FreeMindAsserts.read(example);

        MindMap map = MindMapParser.parse(text);

        assertNotNull(map.root());
        assertFalse(map.root().text() == null);
        for (MapNode node : map.nodesByKey().values()) {
            String element = node.ranges().element().of(text).strip();
            assertTrue(element.startsWith("<node"), element);
            assertSame(node, map.node(node.key()));
        }
    }

    /** The platform's document holds text with {@code \n} separators, so edits always write {@code \n}. */
    @Test
    void theLineSeparatorIsTheDocumentsNewline() throws Exception {
        assertEquals("\n", MindMapParser.parse(MAP).lineSeparator());
    }
}
