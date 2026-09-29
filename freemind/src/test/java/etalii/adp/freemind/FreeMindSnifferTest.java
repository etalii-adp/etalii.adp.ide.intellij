package etalii.adp.freemind;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** FR-002, contracts/plugin-contributions.md: which {@code .mm} contents are FreeMind maps. */
class FreeMindSnifferTest {

    private static final String MAP = "<map version=\"1.0.1\">\n<node TEXT=\"Root\"/>\n</map>\n";

    static final String OBJECTIVE_CPP = """
            #import <Foundation/Foundation.h>
            #include <vector>

            int main(int argc, const char *argv[]) {
                std::vector<int> map;
                return 0;
            }
            """;

    private static boolean sniff(String text) {
        return FreeMindSniffer.isFreeMind(text.getBytes(UTF_8));
    }

    @ParameterizedTest
    @MethodSource("etalii.adp.freemind.FreeMindAsserts#examples")
    void everyExampleMapIsAccepted(Path map) throws IOException {
        byte[] bytes = Files.readAllBytes(map);
        assertTrue(FreeMindSniffer.isFreeMind(Arrays.copyOf(bytes, Math.min(bytes.length, FreeMindSniffer.LIMIT))), map.toString());
    }

    @Test
    void aPlainMapIsAccepted() {
        assertTrue(sniff(MAP));
        assertTrue(sniff("<map version='0.7.1'><node TEXT='x'/></map>"));
        assertTrue(sniff("<map\n  version=\"1.0.1\"\n>"));
    }

    @Test
    void aByteOrderMarkIsSkipped() {
        byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] map = MAP.getBytes(UTF_8);
        byte[] both = Arrays.copyOf(bom, bom.length + map.length);
        System.arraycopy(map, 0, both, bom.length, map.length);
        assertTrue(FreeMindSniffer.isFreeMind(both));
    }

    @Test
    void theXmlDeclarationAndCommentsAreSkipped() {
        assertTrue(sniff("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + MAP));
        assertTrue(sniff("<!-- a comment -->\n  \t\r\n<!--another <map> -->" + MAP));
        assertTrue(sniff("<?xml version=\"1.0\"?>\r\n<!-- To view this file, download FreeMind -->\r\n" + MAP));
    }

    /** A DOCTYPE is claimed so the diagram can explain why it refuses it (FR-009), rather than hiding the map. */
    @Test
    void aDoctypeBeforeTheMapIsClaimedForItsExplanation() {
        assertTrue(sniff("<!DOCTYPE map [ <!ENTITY e \"x\"> ]>\n" + MAP));
    }

    @Test
    void objectiveCppSourceIsRejected() {
        assertFalse(sniff(OBJECTIVE_CPP));
        assertFalse(sniff("// map version\n@implementation Map\n@end\n"));
    }

    @Test
    void emptyAndUnreadableContentIsRejected() {
        assertFalse(FreeMindSniffer.isFreeMind(new byte[0]));
        assertFalse(FreeMindSniffer.isFreeMind(null));
        assertFalse(sniff("   \n\t"));
        assertFalse(FreeMindSniffer.isFreeMind(new byte[] { 0, (byte) 0xFF, 0x13, 0x7F, (byte) 0x80 }));
        assertFalse(sniff("<!-- never closed <map version=\"1.0.1\">"));
        assertFalse(sniff("<?xml version=\"1.0\""));
    }

    @Test
    void aMapWithoutVersionIsRejected() {
        assertFalse(sniff("<map>\n<node TEXT=\"Root\"/>\n</map>\n"));
        assertFalse(sniff("<map xversion=\"1\">"));
        assertFalse(sniff("<mapping version=\"1.0\"/>"));
        assertFalse(sniff("<node TEXT=\"Root\"/>"));
        assertFalse(sniff("text before <map version=\"1.0.1\">"));
    }

    @Test
    void aRootBeyondTheFirstFourKilobytesIsRejected() {
        String padding = "<!--" + "x".repeat(FreeMindSniffer.LIMIT) + "-->\n";
        assertFalse(sniff(padding + MAP));
        String within = "<!--" + "x".repeat(FreeMindSniffer.LIMIT - 100) + "-->\n";
        assertTrue(sniff(within + MAP));
    }

    @Test
    void theSnifferNeverThrows() {
        for (int length = 0; length < 64; length++) {
            byte[] bytes = new byte[length];
            for (int i = 0; i < length; i++) {
                bytes[i] = (byte) (i * 37 + length);
            }
            FreeMindSniffer.isFreeMind(bytes);
        }
        FreeMindSniffer.isFreeMind("<".getBytes(UTF_8));
        FreeMindSniffer.isFreeMind("<map".getBytes(UTF_8));
        FreeMindSniffer.isFreeMind("<map version".getBytes(UTF_8));
    }
}
