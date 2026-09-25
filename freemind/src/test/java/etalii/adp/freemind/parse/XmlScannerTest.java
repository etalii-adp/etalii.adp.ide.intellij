package etalii.adp.freemind.parse;

import static etalii.adp.freemind.parse.XmlScanner.Kind.CDATA;
import static etalii.adp.freemind.parse.XmlScanner.Kind.COMMENT;
import static etalii.adp.freemind.parse.XmlScanner.Kind.END_TAG;
import static etalii.adp.freemind.parse.XmlScanner.Kind.PI;
import static etalii.adp.freemind.parse.XmlScanner.Kind.START_TAG;
import static etalii.adp.freemind.parse.XmlScanner.Kind.TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import etalii.adp.freemind.FreeMindAsserts;
import etalii.adp.freemind.parse.XmlScanner.Attribute;
import etalii.adp.freemind.parse.XmlScanner.Token;

class XmlScannerTest {

    @Test
    void aStartTagReportsExactAttributeRangesInBothQuoteStyles() throws Exception {
        String text = "<node ID=\"ID_1\" TEXT='a &amp; b'/>";

        List<Token> tokens = XmlScanner.scan(text);

        assertEquals(1, tokens.size());
        Token tag = tokens.get(0);
        assertEquals(START_TAG, tag.kind());
        assertEquals("node", tag.name());
        assertTrue(tag.selfClosing());
        assertEquals(0, tag.offset());
        assertEquals(text.length(), tag.length());

        Attribute id = tag.attribute("ID");
        assertEquals("ID_1", id.value().of(text));
        assertEquals('"', id.quote());

        Attribute label = tag.attribute("TEXT");
        assertEquals("TEXT", text.substring(label.nameOffset(), label.nameOffset() + 4));
        assertEquals("a &amp; b", label.value().of(text));
        assertEquals("a & b", label.decodedValue(text));
        assertEquals('\'', label.quote());
        assertEquals(" TEXT='a &amp; b'", text.substring(label.start(), label.end()));
    }

    @Test
    void elementEndsCommentsCdataAndProcessingInstructionsHaveExactRanges() throws Exception {
        String text = "<?xml version=\"1.0\"?>\n<map><!-- note --><node><![CDATA[x<y]]></node></map>";

        List<Token> tokens = XmlScanner.scan(text);

        assertEquals(List.of(PI, TEXT, START_TAG, COMMENT, START_TAG, CDATA, END_TAG, END_TAG),
                tokens.stream().map(Token::kind).toList());
        assertEquals("<?xml version=\"1.0\"?>", tokens.get(0).range().of(text));
        assertEquals("<!-- note -->", tokens.get(3).range().of(text));
        assertEquals("<![CDATA[x<y]]>", tokens.get(5).range().of(text));
        assertEquals("</node>", tokens.get(6).range().of(text));
        assertEquals("node", tokens.get(6).name());
        assertEquals("</map>", tokens.get(7).range().of(text));
        assertFalse(tokens.get(2).selfClosing());
    }

    @Test
    void decodesPredefinedAndNumericReferences() {
        assertEquals("<\u00e9\u00e9\"'&\n", XmlScanner.decode("&lt;&#233;&#xE9;&quot;&apos;&amp;&#xa;"));
    }

    @Test
    void attributeValuesNormaliseLiteralWhitespace() throws Exception {
        String text = "<node TEXT=\"a\r\nb\tc\"/>";

        assertEquals("a b c", XmlScanner.scan(text).get(0).attribute("TEXT").decodedValue(text));
    }

    @ParameterizedTest
    @MethodSource("etalii.adp.freemind.FreeMindAsserts#examples")
    void rescanningEveryExampleReproducesEveryRangesText(Path example) throws Exception {
        String text = FreeMindAsserts.read(example);

        List<Token> tokens = XmlScanner.scan(text);

        int expectedOffset = 0;
        for (Token token : tokens) {
            assertEquals(expectedOffset, token.offset(), "tokens must tile the text");
            String range = token.range().of(text);
            switch (token.kind()) {
            case START_TAG -> {
                assertTrue(range.startsWith("<" + token.name()), range);
                assertTrue(range.endsWith(token.selfClosing() ? "/>" : ">"), range);
                for (Attribute attribute : token.attributes()) {
                    assertTrue(text.startsWith(attribute.name(), attribute.nameOffset()), range);
                    assertEquals(attribute.quote(), text.charAt(attribute.value().offset() - 1), range);
                    assertEquals(attribute.quote(), text.charAt(attribute.value().end()), range);
                }
            }
            case END_TAG -> {
                assertTrue(range.startsWith("</" + token.name()), range);
                assertTrue(range.endsWith(">"), range);
            }
            case COMMENT -> assertTrue(range.startsWith("<!--") && range.endsWith("-->"), range);
            case CDATA -> assertTrue(range.startsWith("<![CDATA[") && range.endsWith("]]>"), range);
            case PI -> assertTrue(range.startsWith("<?") && range.endsWith("?>"), range);
            case TEXT -> assertFalse(range.contains("<"), range);
            case DOCTYPE -> assertTrue(range.startsWith("<!DOCTYPE"), range);
            }
            expectedOffset = token.end();
        }
        assertEquals(text.length(), expectedOffset);
    }
}
