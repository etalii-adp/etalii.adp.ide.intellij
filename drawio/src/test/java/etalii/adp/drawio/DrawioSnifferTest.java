package etalii.adp.drawio;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.intellij.testFramework.BinaryLightVirtualFile;

import etalii.adp.core.AdpEditorProvider;

/**
 * T100: the provider claims {@code .drawio} files by their first element and leaves every other
 * file alone. The sniff itself is core's ({@code DiagramEditorProvider} over {@code XmlTree.rootName},
 * reading at most {@code AdpEditorProvider.SNIFF_LIMIT} bytes); this checks it as draw.io configures it.
 */
class DrawioSnifferTest {

    private static final DrawioEditorProvider PROVIDER = new DrawioEditorProvider();

    private static boolean sniff(String text) {
        return sniff(text.getBytes(UTF_8));
    }

    private static boolean sniff(byte[] bytes) {
        return sniff("diagram.drawio", bytes);
    }

    private static boolean sniff(String name, byte[] bytes) {
        return PROVIDER.accepts(new BinaryLightVirtualFile(name, bytes));
    }

    @ParameterizedTest
    @ValueSource(strings = { "flowchart_1", "cross_functional_flowchart_1", "data_flow_1", "workflow_1", "activity_diagram_1", "uml_1", "compressed" })
    void claimsEveryExample(String name) throws IOException {
        assertTrue(sniff(Files.readAllBytes(DrawioMappingTest.example(name))), name);
    }

    @Test
    void claimsMxfileAndMxGraphModelAfterTheUsualPrologue() {
        assertTrue(sniff("<mxfile host=\"app.diagrams.net\"><diagram/></mxfile>"));
        assertTrue(sniff("<mxGraphModel><root/></mxGraphModel>"));
        assertTrue(sniff("﻿<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<!-- made by hand -->\n  \t<mxfile>"));
        assertTrue(sniff("<?xml version=\"1.0\"?><!DOCTYPE mxfile><mxGraphModel dx=\"1\">"));
    }

    @Test
    void rejectsOtherXml() {
        assertFalse(sniff("<svg xmlns=\"http://www.w3.org/2000/svg\"><mxfile/></svg>"));
        assertFalse(sniff("<map version=\"1.0.1\"><node TEXT=\"mxfile\"/></map>"));
        assertFalse(sniff("<mxfiles/>"));
        assertFalse(sniff("<mxfile-backup/>"));
        assertFalse(sniff("mxfile"));
        assertFalse(sniff("<!-- <mxfile> -->"));
    }

    @Test
    void rejectsBinariesAndEmptyFiles() {
        assertFalse(sniff(new byte[0]));
        assertFalse(sniff(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13 }));
        assertFalse(sniff(new byte[] { (byte) 0xFF, (byte) 0xFE, '<', 0, 'm', 0, 'x', 0 }));
        byte[] random = new byte[5000];
        new java.util.Random(7).nextBytes(random);
        assertFalse(sniff(random));
    }

    @Test
    void neverLooksPastTheLimit() {
        assertEquals(4096, AdpEditorProvider.SNIFF_LIMIT);
        String late = " ".repeat(AdpEditorProvider.SNIFF_LIMIT) + "<mxfile/>";
        assertFalse(sniff(late), "the root element starts after the first 4 KB");
        String longComment = "<!--" + "x".repeat(AdpEditorProvider.SNIFF_LIMIT) + "--><mxfile/>";
        assertFalse(sniff(longComment));
    }

    @Test
    void onlyTheDrawioExtensionIsClaimed() {
        byte[] drawio = "<mxfile/>".getBytes(UTF_8);
        assertTrue(sniff("a.DRAWIO", drawio));
        assertFalse(sniff("a.xml", drawio));
        assertFalse(sniff("a.drawio.svg", drawio));
        assertFalse(sniff("a.drawio.png", drawio));
    }

    @Test
    void neverThrows() {
        for (String odd : new String[] { "<", "<?", "<!--", "<!", "<?xml", "﻿", "< mxfile", "<>" }) {
            assertFalse(sniff(odd), odd);
        }
    }
}
