package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.example;
import static etalii.adp.freemind.FreeMindAsserts.examples;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.freemind.FreeMindFileType;
import etalii.adp.freemind.FreeMindFileTypeDetector;
import etalii.adp.freemind.FreeMindSniffer;
import etalii.adp.testing.ToolDriver;
import etalii.adp.testing.Layout;

/** FR-001 to FR-003, US1-AS1 and AS-3: which editor the IDE picks for a {@code .mm} file. */
@RunWith(JUnit4.class)
public class RegistrationTest extends FileEditorManagerTestCase {

    static final String OBJECTIVE_CPP = """
            #import <Foundation/Foundation.h>
            #include <vector>

            int main(int argc, const char *argv[]) {
                std::vector<int> map;
                return 0;
            }
            """;

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void aFreeMindMapOpensInTheToolByDefault() {
        for (Path map : examples()) {
            try (var d = ToolDriver.open(myFixture, map)) {
                String name = map.getFileName().toString();
                assertEquals(name, MindMapEditorProvider.EDITOR_TYPE_ID, d.editorTypeIdUsed());
                assertInstanceOf(d.tool(), MindMapFileEditor.class);
                assertEquals(name, Layout.TOOL, d.layout());
                assertNotNull(name + ": the map is drawn, not shown as a problem", d.tool().model());
                assertFalse(name, d.problemShown());
                assertFalse(name, d.isModified());
            }
        }
    }

    @Test
    public void openWithListsTheToolAndTheTextEditor() {
        try (var d = ToolDriver.open(myFixture, example("freemind-1.0.1-rich-notes.mm"))) {
            assertTrue(d.editorTypeIdsOffered().toString(), d.editorTypeIdsOffered().contains(MindMapEditorProvider.EDITOR_TYPE_ID));
            assertFalse("the map opens as text", d.file().getFileType().isBinary());
            assertEquals("Mind map", d.composite().getName());

            TextEditor text = assertInstanceOf(d.composite().getTextEditor(), TextEditor.class);
            assertSame("the text side edits the same document", d.document(), text.getEditor().getDocument());
            assertSame(d.document(), d.tool().document());

            d.showLayout(Layout.TEXT);
            assertEquals(Layout.TEXT, d.layout());
            assertEquals(d.text(), text.getEditor().getDocument().getText());
            d.showLayout(Layout.SPLIT);
            assertEquals(Layout.SPLIT, d.layout());
        }
    }

    /** The diagram is not offered, so the file opens as it would without the plug-in. */
    @Test
    public void anObjectiveCppFileIsNotClaimed() {
        try (var d = ToolDriver.openText(myFixture, "main.mm", OBJECTIVE_CPP)) {
            assertNull(d.tool());
            assertFalse(d.editorTypeIdsOffered().contains(MindMapEditorProvider.EDITOR_TYPE_ID));
            assertNotSame(FreeMindFileType.INSTANCE, d.file().getFileType());
            assertNotSame(MindMapEditorProvider.EDITOR_TYPE_ID, d.editorTypeIdUsed());
        }
    }

    /** The detector names FreeMind maps by content, and leaves every other file alone. */
    @Test
    public void theDetectorRecognisesMapsByContent() throws Exception {
        var detector = new FreeMindFileTypeDetector();
        var map = ToolDriver.createFile(myFixture, "detect.mm", Files.readAllBytes(example("freemind-1.0.1-rich-notes.mm")));
        var source = ToolDriver.createFile(myFixture, "detect-source.mm", OBJECTIVE_CPP.getBytes(UTF_8));
        var named = ToolDriver.createFile(myFixture, "detect.xml", Files.readAllBytes(example("freemind-1.0.1-rich-notes.mm")));
        assertSame(FreeMindFileType.INSTANCE, detector.detect(map, new ByteArraySequence(map.contentsToByteArray()), null));
        assertNull(detector.detect(source, new ByteArraySequence(source.contentsToByteArray()), null));
        assertNull("only .mm files", detector.detect(named, new ByteArraySequence(named.contentsToByteArray()), null));
        assertEquals(FreeMindSniffer.LIMIT, detector.getDesiredContentPrefixLength());
    }

    /** The file type is given by content only: {@code .mm} stays free for other file types (Objective-C++ in CLion). */
    @Test
    public void theFileTypeIsNotBoundToTheExtension() {
        var byExtension = FileTypeManager.getInstance().getFileTypeByExtension("mm");
        assertNotSame(FreeMindFileType.INSTANCE, byExtension);
        assertTrue(String.valueOf(byExtension), byExtension instanceof UnknownFileType || !byExtension.getName().equals(FreeMindFileType.INSTANCE.getName()));
        assertEquals("mm", FreeMindFileType.INSTANCE.getDefaultExtension());
        assertEquals("FreeMind Mind Map", FreeMindFileType.INSTANCE.getName());
    }

    /**
     * Showing the text alone is remembered for the next map, as the IDE's own layout switch does,
     * which makes the text editor the default; switching back restores the diagram.
     */
    @Test
    public void theTextEditorCanBeMadeTheDefault() {
        var map = example("freemind-1.0.1-rich-notes.mm");
        try {
            try (var d = ToolDriver.open(myFixture, map)) {
                d.composite().setLayout(TextEditorWithPreview.Layout.SHOW_EDITOR);
            }
            try (var d = ToolDriver.openText(myFixture, "second.mm", "<map version=\"1.0.1\">\n<node TEXT=\"Second\"/>\n</map>\n")) {
                assertEquals(MindMapEditorProvider.EDITOR_TYPE_ID, d.editorTypeIdUsed());
                assertEquals(Layout.TEXT, d.layout());
                d.composite().setLayout(TextEditorWithPreview.Layout.SHOW_PREVIEW);
            }
            try (var d = ToolDriver.openText(myFixture, "third.mm", "<map version=\"1.0.1\">\n<node TEXT=\"Third\"/>\n</map>\n")) {
                assertEquals(Layout.TOOL, d.layout());
            }
        } finally {
            ((FileEditorManagerEx) FileEditorManager.getInstance(getProject())).closeAllFiles();
            PropertiesComponent.getInstance().unsetValue("FreeMind Mind MapLayout");
        }
    }
}
