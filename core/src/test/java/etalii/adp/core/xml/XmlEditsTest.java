package etalii.adp.core.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import etalii.adp.core.FormatProblem;
import etalii.adp.core.TextChange;
import etalii.adp.core.TextChanges;

class XmlEditsTest {

    private static String apply(String text, TextChange change) {
        return TextChanges.of(change).applyTo(text);
    }

    private static XmlElement root(String text) throws FormatProblem {
        return XmlTree.of(text).root();
    }

    @Test
    void setAttributeReplacesAndKeepsTheQuoteStyle() throws FormatProblem {
        String text = "<a x='1' y=\"2\"/>";
        assertEquals("<a x='a&apos;b' y=\"2\"/>", apply(text, XmlEdits.setAttribute(root(text), "x", "a'b")));
        assertEquals("<a x='1' y=\"&lt;&quot;&amp;\"/>", apply(text, XmlEdits.setAttribute(root(text), "y", "<\"&")));
    }

    @Test
    void setAttributeInsertsAfterTheLastAttributeWithTheElementsQuote() throws FormatProblem {
        String text = "<a x='1'>t</a>";
        assertEquals("<a x='1' z='new'>t</a>", apply(text, XmlEdits.setAttribute(root(text), "z", "new")));
        String bare = "<a/>";
        assertEquals("<a z=\"line&#10;two\"/>", apply(bare, XmlEdits.setAttribute(root(bare), "z", "line\ntwo")));
    }

    @Test
    void removeAttributeTakesItsLeadingWhitespace() throws FormatProblem {
        String text = "<a x=\"1\"  y=\"2\"/>";
        assertEquals("<a x=\"1\"/>", apply(text, XmlEdits.removeAttribute(root(text), "y")));
        assertNull(XmlEdits.removeAttribute(root(text), "missing"));
    }

    @Test
    void insertChildUsesTheSiblingIndentationAndLineSeparator() throws FormatProblem {
        String text = "<r>\n  <a/>\n  <b/>\n</r>\n";
        assertEquals("<r>\n  <a/>\n  <b/>\n  <c/>\n</r>\n", apply(text, XmlEdits.insertChild(text, root(text), 2, "<c/>")));
        assertEquals("<r>\n  <c/>\n  <a/>\n  <b/>\n</r>\n", apply(text, XmlEdits.insertChild(text, root(text), 0, "<c/>")));
        assertEquals("<r>\n  <a/>\n  <c/>\n  <b/>\n</r>\n", apply(text, XmlEdits.insertChild(text, root(text), 1, "<c/>")));

        String crlf = "<r>\r\n\t<a/>\r\n</r>";
        assertEquals("<r>\r\n\t<a/>\r\n\t<c/>\r\n</r>", apply(crlf, XmlEdits.insertChild(crlf, root(crlf), 1, "<c/>")));
    }

    @Test
    void insertChildIntoAnEmptyOrSelfClosingParent() throws FormatProblem {
        String empty = "<r>\n</r>";
        assertEquals("<r>\n  <c/>\n</r>", apply(empty, XmlEdits.insertChild(empty, root(empty), 0, "<c/>")));
        String selfClosing = "<r x=\"1\" />";
        assertEquals("<r x=\"1\">\n  <c/>\n</r>", apply(selfClosing, XmlEdits.insertChild(selfClosing, root(selfClosing), 0, "<c/>")));
        String nested = "<r>\n  <p/>\n</r>";
        XmlElement p = root(nested).children().get(0);
        assertEquals("<r>\n  <p>\n    <c/>\n  </p>\n</r>", apply(nested, XmlEdits.insertChild(nested, p, 0, "<c/>")));
    }

    @Test
    void removeTakesItsOwnLineWhenAloneOnIt() throws FormatProblem {
        String text = "<r>\n  <a/>\n  <b/>\n</r>";
        XmlElement a = root(text).children().get(0);
        assertEquals("<r>\n  <b/>\n</r>", apply(text, XmlEdits.remove(text, a)));
        String crlf = "<r>\r\n  <a/>\r\n  <b/>\r\n</r>";
        assertEquals("<r>\r\n  <b/>\r\n</r>", apply(crlf, XmlEdits.remove(crlf, root(crlf).children().get(0))));
        String inline = "<r><a/><b/></r>";
        assertEquals("<r><b/></r>", apply(inline, XmlEdits.remove(inline, root(inline).children().get(0))));
    }

    @Test
    void setContentEscapes() throws FormatProblem {
        String text = "<r><a>old</a><b/></r>";
        XmlElement a = root(text).children().get(0);
        XmlElement b = root(text).children().get(1);
        assertEquals("<r><a>x &lt; y &amp; z</a><b/></r>", apply(text, XmlEdits.setContent(a, "x < y & z")));
        assertEquals("<r><a>old</a><b>new</b></r>", apply(text, XmlEdits.setContent(b, "new")));
        assertEquals("a &gt; b", XmlEdits.escape("a > b"));
    }

    @Test
    void updateSetsRemovesOrDoesNothing() throws FormatProblem {
        String text = "<a x=\"1\" y=\"2\"/>";
        assertNull(XmlEdits.update(root(text), "x", "1"));
        assertEquals("<a x=\"3\" y=\"2\"/>", apply(text, XmlEdits.update(root(text), "x", "3")));
        assertEquals("<a x=\"1\"/>", apply(text, XmlEdits.update(root(text), "y", "")));
        assertEquals("<a x=\"1\"/>", apply(text, XmlEdits.update(root(text), "y", null)));
        assertNull(XmlEdits.update(root(text), "z", null));
        assertEquals("<a x=\"1\" y=\"2\" z=\"n\"/>", apply(text, XmlEdits.update(root(text), "z", "n")));
    }

    @Test
    void elementsIdsAndNumbers() throws FormatProblem {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("id", "a\"1");
        attributes.put("skip", null);
        attributes.put("x", "2");
        assertEquals("<box id=\"a&quot;1\" x=\"2\"/>", XmlEdits.element("box", attributes, null));
        assertEquals("<box id=\"a&quot;1\" x=\"2\">1 &lt; 2</box>", XmlEdits.element("box", attributes, "1 < 2"));
        assertEquals("n3", XmlEdits.uniqueId(root("<r id=\"n1\"><a><b id=\"n2\"/></a></r>"), "n"));
        assertEquals("40", XmlEdits.number(40.0));
        assertEquals("400.5", XmlEdits.number(400.5));
        assertEquals("-0.25", XmlEdits.number(-0.25));
    }

    @Test
    void numbersRereadAndChildren() throws FormatProblem {
        String text = "<a x=\"10\"><g w=\"1.5\"/></a>";
        XmlElement a = XmlTree.reread(text).root();
        assertNull(XmlEdits.updateNumber(a, "x", 10.001));
        assertEquals("<a x=\"12.35\"><g w=\"1.5\"/></a>", apply(text, XmlEdits.updateNumber(a, "x", 12.346)));
        assertNull(XmlEdits.updateNumber(a, "y", 0));
        String precise = "<a x=\"270.3945578231293\"/>";
        assertNull(XmlEdits.updateNumber(XmlTree.reread(precise).root(), "x", 270.3945578231293));
        assertEquals("<a x=\"10\" y=\"4\"><g w=\"1.5\"/></a>", apply(text, XmlEdits.updateNumber(a, "y", 4)));
        assertEquals("1.5", a.child("g").attribute("w"));
        assertNull(a.child("missing"));
        assertEquals(List.of("id", "x"), List.copyOf(XmlEdits.attributes("id", "n", "x", null).keySet()));
        assertThrows(IllegalStateException.class, () -> XmlTree.reread("<a>"));
    }
}
