package etalii.adp.freemind.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import etalii.adp.core.xml.XmlScanner;
import etalii.adp.core.xml.XmlScanner.Attribute;
import etalii.adp.core.xml.XmlScanner.Token;

/** The shared scanner on every FreeMind example (moved here from the scanner's own test with the scanner, T017). */
class ExampleScanTest {

    @ParameterizedTest
    @MethodSource("etalii.adp.freemind.FreeMindAsserts#examples")
    void rescanningEveryExampleReproducesEveryRangesText(Path example) throws Exception {
        String text = etalii.adp.freemind.FreeMindAsserts.read(example);

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
