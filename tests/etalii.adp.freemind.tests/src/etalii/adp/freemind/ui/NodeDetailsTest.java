package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.draw2d.Label;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.junit.jupiter.api.Test;

import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.ui.figures.NodeFigure;
import etalii.adp.testing.DesignerDriver;

/** FR-014, FR-018: the details each node figure shows. */
class NodeDetailsTest {

    static final String MAP = """
            <map version="1.0.1">
            <node ID="R" TEXT="Root">
            <node ID="PLAIN" POSITION="right" TEXT="Plain &amp; simple"/>
            <node ID="RICH" POSITION="right">
            <richcontent TYPE="NODE"><html><head></head><body><p>Hello <b>rich</b> world</p></body></html></richcontent>
            </node>
            <node BACKGROUND_COLOR="#00ff00" COLOR="#ff0000" ID="STYLED" POSITION="left" TEXT="Styled">
            <font BOLD="true" ITALIC="true" NAME="Arial" SIZE="16"/>
            <icon BUILTIN="idea"/>
            <icon BUILTIN="my-custom"/>
            </node>
            <node ID="NOTED" LINK="other.mm" POSITION="left" TEXT="Noted">
            <richcontent TYPE="NOTE"><html><head></head><body><p>The note text</p></body></html></richcontent>
            </node>
            <node ID="WEB" LINK="https://example.com/page" POSITION="left" TEXT="Web"/>
            </node>
            </map>
            """;

    static final String OTHER = """
            <map version="1.0.1">
            <node ID="O" TEXT="Other"/>
            </map>
            """;

    @Test
    void plainAndRichTextAreReadable() {
        try (var d = DesignerDriver.openText("details.mm", MAP, MindMapEditor.ID)) {
            assertEquals("Plain & simple", figure(d, "PLAIN").text());
            MindMap model = (MindMap) d.editor().model();
            String rich = figure(d, "RICH").text();
            assertEquals(model.node(key("RICH")).text(), rich);
            assertTrue(rich.contains("Hello rich world"), rich);
            assertFalse(rich.contains("<"), rich);
        }
    }

    @Test
    void iconsAreGlyphsOrBadges() {
        try (var d = DesignerDriver.openText("details.mm", MAP, MindMapEditor.ID)) {
            assertEquals(List.of("\uD83D\uDCA1", "my-custom"), figure(d, "STYLED").iconTexts());
            assertEquals("\uD83D\uDCA1", FreeMindIcons.glyph("idea"));
            assertNull(FreeMindIcons.glyph("my-custom"));
            assertEquals(List.of(), figure(d, "PLAIN").iconTexts());
        }
    }

    @Test
    void coloursAndFontAreShown() {
        try (var d = DesignerDriver.openText("details.mm", MAP, MindMapEditor.ID)) {
            NodeFigure styled = figure(d, "STYLED");
            assertEquals(new RGB(255, 0, 0), styled.textColor().getRGB());
            assertEquals(new RGB(0, 255, 0), styled.fillColor().getRGB());
            FontData font = styled.textFont().getFontData()[0];
            assertEquals("Arial", font.getName());
            assertEquals(16, font.getHeight());
            assertEquals(SWT.BOLD | SWT.ITALIC, font.getStyle());

            NodeFigure plain = figure(d, "PLAIN");
            assertNull(plain.fillColor());
            assertEquals(SWT.NORMAL, plain.textFont().getFontData()[0].getStyle());
        }
    }

    @Test
    void aNoteIsAnIndicatorWithItsTextAsTooltip() {
        try (var d = DesignerDriver.openText("details.mm", MAP, MindMapEditor.ID)) {
            NodeFigure noted = figure(d, "NOTED");
            assertNotNull(noted.noteIndicator());
            Label tooltip = assertInstanceOf(Label.class, noted.noteIndicator().getToolTip());
            assertEquals("The note text", tooltip.getText().strip());
            assertNull(figure(d, "PLAIN").noteIndicator());
        }
    }

    @Test
    void aLinkIndicatorOpensAMapRelativeFile() throws Exception {
        try (var d = DesignerDriver.openText("details.mm", MAP, MindMapEditor.ID)) {
            IFile other = d.file().getParent().getFile(new org.eclipse.core.runtime.Path("other.mm"));
            other.create(new ByteArrayInputStream(OTHER.getBytes(UTF_8)), IResource.FORCE, null);
            assertNull(figure(d, "PLAIN").linkIndicator());
            assertNotNull(figure(d, "WEB").linkIndicator());

            figure(d, "NOTED").linkIndicator().doClick();
            d.settle();

            IWorkbenchPage page = d.editor().getSite().getPage();
            IEditorPart opened = page.getActiveEditor();
            try {
                IFileEditorInput input = assertInstanceOf(IFileEditorInput.class, opened.getEditorInput());
                assertEquals(other, input.getFile());
            } finally {
                page.closeEditor(opened, false);
                d.settle();
            }
            assertFalse(d.isDirty());
        }
    }

    @Test
    void urlsAreToldApartFromPaths() {
        assertNotNull(LinkOpener.asUrl("https://example.com/page"));
        assertNotNull(LinkOpener.asUrl("mailto:someone@example.com"));
        assertNull(LinkOpener.asUrl("other.mm"));
        assertNull(LinkOpener.asUrl("docs/other map.mm"));
        assertNull(LinkOpener.asUrl("C:\\maps\\other.mm"));
        assertNull(LinkOpener.asUrl("#ID_1"));
    }

    static NodeFigure figure(DesignerDriver d, String id) {
        return assertInstanceOf(NodeFigure.class, d.figureOf(key(id)));
    }
}
