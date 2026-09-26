package etalii.adp.core.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import etalii.adp.core.FormatProblem;

class XmlTreeTest {

    private static final String TEXT = """
            <?xml version="1.0"?>
            <!-- a comment -->
            <root a="1" b='two &amp; more'>
              <child id="c1"/>
              <!-- skipped -->
              <child id="c2">Hello &lt;world&gt;<![CDATA[ & raw]]></child>
            </root>
            """;

    @Test
    void elementsCarryTheirExactRanges() throws FormatProblem {
        XmlElement root = XmlTree.of(TEXT).root();
        assertEquals("root", root.name());
        assertEquals(TEXT.indexOf("<root"), root.range().offset());
        assertEquals(TEXT.indexOf("</root>") + "</root>".length(), root.range().end());
        assertEquals("<root a=\"1\" b='two &amp; more'>", root.startTag().of(TEXT));
        XmlElement first = root.children().get(0);
        assertEquals("<child id=\"c1\"/>", first.range().of(TEXT));
        assertTrue(first.selfClosing());
        assertEquals(first.range(), first.startTag());
    }

    @Test
    void attributesKeepTheirOffsetsAndQuotes() throws FormatProblem {
        XmlElement root = XmlTree.of(TEXT).root();
        assertEquals(List.of("a", "b"), List.copyOf(root.attributes().keySet()));
        XmlAttribute b = root.attributes().get("b");
        assertEquals('\'', b.quote());
        assertEquals("two & more", b.value());
        assertEquals("two &amp; more", b.valueRange().of(TEXT));
        assertEquals(" b='two &amp; more'", TEXT.substring(b.start(), b.end()));
        assertEquals("two & more", root.attribute("b"));
        assertNull(root.attribute("missing"));
    }

    @Test
    void insertPointsAndIndentation() throws FormatProblem {
        XmlElement root = XmlTree.of(TEXT).root();
        assertEquals(TEXT.indexOf("more'") + "more'".length(), root.attributeInsertPoint());
        XmlElement last = root.children().get(1);
        assertEquals(last.range().end(), root.childInsertPoint());
        assertEquals("", root.indent());
        assertEquals("  ", last.indent());
        XmlElement first = root.children().get(0);
        assertEquals(TEXT.indexOf("\"c1\"") + 4, first.attributeInsertPoint());
    }

    @Test
    void textContentSkipsCommentsAndChildrenSkipCdataAndComments() throws FormatProblem {
        XmlElement root = XmlTree.of(TEXT).root();
        assertEquals(2, root.children().size());
        XmlElement second = root.children().get(1);
        assertEquals("Hello <world> & raw", second.text());
        assertEquals("Hello &lt;world&gt;<![CDATA[ & raw]]>", second.content().of(TEXT));
        assertEquals("", root.children().get(0).text());
    }

    @Test
    void malformedInputRaisesAProblemWithItsOffset() {
        String mismatched = "<a>\n  <b></c>\n</a>";
        FormatProblem problem = assertThrows(FormatProblem.class, () -> XmlTree.of(mismatched));
        assertEquals(mismatched.indexOf("</c>"), problem.getOffset());

        String unclosed = "<a>\n  <b>";
        assertEquals(unclosed.indexOf("<b>"), assertThrows(FormatProblem.class, () -> XmlTree.of(unclosed)).getOffset());

        String unterminatedTag = "<a b=\"1\"";
        assertEquals(0, assertThrows(FormatProblem.class, () -> XmlTree.of(unterminatedTag)).getOffset());

        assertThrows(FormatProblem.class, () -> XmlTree.of("<a/><b/>"));
        assertThrows(FormatProblem.class, () -> XmlTree.of("   "));
    }

    @Test
    void attributesWithFallbacksAndDescendants() throws FormatProblem {
        XmlElement root = XmlTree.of("<r n=\"2.5\" bad=\"x\"><a id=\"1\"><b id=\"2\"/></a><c/></r>").root();
        assertEquals(2.5, root.number("n", 0));
        assertEquals(7, root.number("bad", 7));
        assertEquals(7, root.number("missing", 7));
        assertEquals("d", root.attribute("missing", "d"));
        assertEquals(List.of("r", "a", "b", "c"), root.descendants().stream().map(XmlElement::name).toList());
    }

    @Test
    void rootNameSkipsTheProlog() {
        assertEquals("mxfile", XmlTree.rootName("﻿<?xml version=\"1.0\"?>\n<!-- c -->\n<!DOCTYPE x>\n  <mxfile host=\"a\">".getBytes()));
        assertEquals("sample", XmlTree.rootName("<sample/>".getBytes()));
        assertNull(XmlTree.rootName("<!-- not closed".getBytes()));
        assertNull(XmlTree.rootName("plain text".getBytes()));
        assertNull(XmlTree.rootName(new byte[0]));
        assertNull(XmlTree.rootName(new byte[] { 0, (byte) 0xFF, 3, '<' }));
    }
}
