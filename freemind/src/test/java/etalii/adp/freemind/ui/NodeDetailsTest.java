package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.ServiceContainerUtil;

import etalii.adp.core.NodeView;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.testing.DesignerDriver;

/** Spec 001 FR-014, FR-018: the details each node shows, and following its link. */
@RunWith(JUnit4.class)
public class NodeDetailsTest extends FileEditorManagerTestCase {

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
            <node ID="JUMP" LINK="#PLAIN" POSITION="right" TEXT="Jump"/>
            </node>
            </map>
            """;

    static final String OTHER = """
            <map version="1.0.1">
            <node ID="O" TEXT="Other"/>
            </map>
            """;

    private final List<String> browsed = new ArrayList<>();

    @Override
    public void setUp() {
        super.setUp();
        ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), BrowserLauncher.class, new RecordingBrowser(),
                getTestRootDisposable());
    }

    private DesignerDriver open() {
        return DesignerDriver.openText(myFixture, "details.mm", MAP);
    }

    @Test
    public void plainAndRichTextAreReadable() {
        try (var d = open()) {
            assertEquals("Plain & simple", view(d, "PLAIN").text());
            MindMap model = LayoutTest.designer(d).model();
            String rich = view(d, "RICH").text();
            assertEquals(model.node(key("RICH")).text(), rich);
            assertTrue(rich, rich.contains("Hello rich world"));
            assertFalse(rich, rich.contains("<"));
        }
    }

    @Test
    public void iconsAreGlyphsOrBadges() {
        try (var d = open()) {
            assertEquals(List.of("\uD83D\uDCA1", "my-custom"), view(d, "STYLED").icons());
            assertEquals("\uD83D\uDCA1", FreeMindIcons.glyph("idea"));
            assertNull(FreeMindIcons.glyph("my-custom"));
            assertEquals(List.of(), view(d, "PLAIN").icons());
        }
    }

    @Test
    public void coloursAndFontAreShown() {
        try (var d = open()) {
            NodeView styled = view(d, "STYLED");
            assertEquals(new Color(255, 0, 0), new Color(styled.foreground().getRGB()));
            assertEquals(new Color(0, 255, 0), new Color(styled.background().getRGB()));
            Font font = styled.font();
            assertEquals("Arial", font.getName());
            assertEquals(16, font.getSize());
            assertEquals(Font.BOLD | Font.ITALIC, font.getStyle());

            NodeView plain = view(d, "PLAIN");
            assertNull(plain.background());
            assertEquals(Font.PLAIN, plain.font().getStyle());
            assertEquals(new Color(NodePainter.TEXT.getRGB()), new Color(plain.foreground().getRGB()));
        }
    }

    @Test
    public void aNoteIsAnIndicatorWithItsTextAsTooltip() {
        try (var d = open()) {
            MindMapCanvas canvas = LayoutTest.designer(d).canvas();
            assertTrue(view(d, "NOTED").hasNote());
            assertFalse(view(d, "PLAIN").hasNote());
            Rectangle note = canvas.indicatorBounds(key("NOTED"), NodePainter.Indicator.NOTE);
            assertNotNull(note);
            assertEquals("The note text", canvas.getToolTipText(mouse(canvas, MouseEvent.MOUSE_MOVED, centre(note), 0)).strip());
            assertNull(canvas.indicatorBounds(key("PLAIN"), NodePainter.Indicator.NOTE));
        }
    }

    @Test
    public void aLinkIndicatorOpensAMapRelativeFile() {
        try (var d = open()) {
            var other = DesignerDriver.createFile(myFixture, "other.mm", OTHER.getBytes(UTF_8));
            assertFalse(view(d, "PLAIN").hasLink());
            assertTrue(view(d, "WEB").hasLink());

            clickIndicator(d, "NOTED");

            FileEditorManager manager = FileEditorManager.getInstance(getProject());
            assertTrue("the linked map is open", manager.isFileOpen(other));
            manager.closeFile(other);
            d.settle();
            assertFalse(d.isModified());
            assertEquals(List.of(), browsed);
        }
    }

    @Test
    public void aLinkIndicatorOpensAUrlInTheBrowser() {
        try (var d = open()) {
            clickIndicator(d, "WEB");
            assertEquals(List.of("https://example.com/page"), browsed);
        }
    }

    @Test
    public void aLinkToANodeRevealsIt() {
        try (var d = open()) {
            clickIndicator(d, "JUMP");
            assertEquals(List.of(key("PLAIN")), d.selectedKeys());
            assertEquals(List.of(), browsed);
        }
    }

    @Test
    public void urlsAreToldApartFromPaths() {
        assertNotNull(LinkOpener.asUrl("https://example.com/page"));
        assertNotNull(LinkOpener.asUrl("mailto:someone@example.com"));
        assertNull(LinkOpener.asUrl("other.mm"));
        assertNull(LinkOpener.asUrl("docs/other map.mm"));
        assertNull(LinkOpener.asUrl("C:\\maps\\other.mm"));
        assertNull(LinkOpener.asUrl("#ID_1"));
        assertNull(LinkOpener.asUrl("file:/tmp/other.mm"));
    }

    private void clickIndicator(DesignerDriver d, String id) {
        MindMapCanvas canvas = LayoutTest.designer(d).canvas();
        Rectangle link = canvas.indicatorBounds(key(id), NodePainter.Indicator.LINK);
        assertNotNull(id + " has a link indicator", link);
        assertEquals(canvas.linkOf(key(id)), canvas.getToolTipText(mouse(canvas, MouseEvent.MOUSE_MOVED, centre(link), 0)));
        Point at = centre(link);
        canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_PRESSED, at, 1));
        canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_RELEASED, at, 1));
        canvas.dispatchEvent(mouse(canvas, MouseEvent.MOUSE_CLICKED, at, 1));
        d.settle();
    }

    static NodeView view(DesignerDriver d, String id) {
        NodeView view = d.viewOf(key(id));
        assertNotNull(id + " is drawn", view);
        return view;
    }

    static Point centre(Rectangle box) {
        return new Point(box.x + box.width / 2, box.y + box.height / 2);
    }

    static MouseEvent mouse(MindMapCanvas canvas, int id, Point at, int clickCount) {
        return new MouseEvent(canvas, id, System.currentTimeMillis(), 0, at.x, at.y, clickCount, false,
                id == MouseEvent.MOUSE_MOVED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1);
    }

    /** Records what would open in the browser; the tests never open one. */
    private final class RecordingBrowser extends BrowserLauncher {

        @Override
        public void open(@NotNull String url) {
            browsed.add(url);
        }

        @Override
        public void browse(@NotNull File file) {
            browsed.add(file.toString());
        }

        @Override
        public void browse(@NotNull Path path) {
            browsed.add(path.toString());
        }

        @Override
        public void browse(@NotNull String url, @Nullable WebBrowser browser, @Nullable Project project) {
            browsed.add(url);
        }
    }
}
