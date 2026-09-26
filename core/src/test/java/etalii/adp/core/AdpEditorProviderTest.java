package etalii.adp.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static etalii.adp.core.TextChange.insert;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.ui.EditorNotificationPanel;

import etalii.adp.testing.DesignerDriver;
import etalii.adp.testing.Layout;

/** The provider and the designer base, driven through the format-agnostic {@link FakeFormat}. */
@RunWith(JUnit4.class)
public class AdpEditorProviderTest extends FileEditorManagerTestCase {

    private static final String TEXT = FakeFormat.HEADER + "alpha\nbeta\ngamma\n";

    private FakeFormat.Provider provider;

    @Override
    public void setUp() {
        super.setUp();
        provider = FakeFormat.register(getTestRootDisposable());
    }

    private DesignerDriver open(String text) {
        return DesignerDriver.openText(myFixture, "items.txt", text);
    }

    private static FakeFormat.Designer fake(DesignerDriver d) {
        return (FakeFormat.Designer) d.designer();
    }

    @Test
    public void theTextPageIsATextEditorOnTheSameDocument() {
        try (var d = open(TEXT)) {
            assertInstanceOf(d.editor(), TextEditorWithPreview.class);
            assertSame(d.designer().document(), d.composite().getTextEditor().getEditor().getDocument());
            assertEquals(FakeFormat.EDITOR_TYPE_ID, d.editorTypeIdUsed());
            assertEquals(Layout.DESIGNER, d.layout());
            assertEquals("beta", d.viewOf(1).text());
        }
    }

    @Test
    public void acceptsOnlyWithTheExtensionAndTheSniff() {
        try (var plain = DesignerDriver.openText(myFixture, "plain.txt", "alpha\n");
                var other = DesignerDriver.openText(myFixture, "items.md", TEXT)) {
            assertNull(plain.designer());
            assertFalse(plain.editorTypeIdsOffered().contains(FakeFormat.EDITOR_TYPE_ID));
            assertNull(other.designer());
            assertFalse(other.editorTypeIdsOffered().contains(FakeFormat.EDITOR_TYPE_ID));
        }
    }

    @Test
    public void theSniffReadsAtMostFourKilobytes() {
        var file = DesignerDriver.createFile(myFixture, "big.txt", (TEXT + "x".repeat(20_000)).getBytes(UTF_8));

        assertTrue(provider.accept(getProject(), file));
        assertEquals(AdpEditorProvider.SNIFF_LIMIT, provider.sniffedBytes);
    }

    @Test
    public void theDefaultTextEditorIsHidden() {
        try (var d = open(TEXT)) {
            assertEquals(FileEditorPolicy.HIDE_DEFAULT_EDITOR, provider.getPolicy());
            assertEquals(1, FileEditorManager.getInstance(getProject()).getAllEditors(d.file()).length);
        }
    }

    @Test
    public void saveDirtyAndRevertDelegateToTheTextEditor() {
        try (var d = open(TEXT)) {
            d.editText(t -> t.replace("beta", "BETA"));
            assertTrue(d.isModified());
            assertTrue(d.editor().isModified());

            assertEquals(FakeFormat.HEADER + "alpha\nBETA\ngamma\n", new String(d.savedBytes(), UTF_8));
            assertFalse(d.isModified());
            assertEquals("BETA", d.viewOf(1).text());
        }
    }

    @Test
    public void aDocumentChangeReparsesOncePerEventLoopTurn() {
        try (var d = open(TEXT)) {
            int before = fake(d).parseCount;

            WriteCommandAction.runWriteCommandAction(getProject(), () -> {
                d.document().insertString(FakeFormat.HEADER.length(), "one\n");
                d.document().insertString(FakeFormat.HEADER.length(), "two\n");
            });
            assertEquals(before, fake(d).parseCount);

            d.settle();
            assertEquals(before + 1, fake(d).parseCount);
            assertEquals("two", d.viewOf(0).text());
            assertEquals("one", d.viewOf(1).text());
        }
    }

    @Test
    public void aFormatProblemShowsLineAndColumnAndChangesNothing() {
        try (var d = open(TEXT)) {
            d.editText(t -> t.replace("beta", "!x"));

            assertTrue(d.problemShown());
            assertNull(d.designer().model());
            assertFalse(d.designer().isEditable());
            String message = d.designer().problemMessage();
            assertTrue(message, message.contains("Line 3, column 1"));
            assertEquals(FakeFormat.HEADER + "alpha\n!x\ngamma\n", d.text());

            d.designer().showText();
            assertEquals(Layout.TEXT, d.layout());
        }
    }

    @Test
    public void aMalformedFileOpensOnTheTextPageUnmodified() {
        try (var d = open(FakeFormat.HEADER + "alpha\n!\n")) {
            assertTrue(d.problemShown());
            assertEquals(Layout.TEXT, d.layout());
            assertFalse(d.isModified());
            assertEquals(FakeFormat.HEADER + "alpha\n!\n", d.text());
        }
    }

    @Test
    public void aReadOnlyInputShowsTheBannerAndCannotBeEdited() {
        try (var d = open(TEXT)) {
            assertFalse(d.readOnlyBannerShown());

            d.setReadOnly(true);
            assertTrue(d.readOnlyBannerShown());
            assertFalse(d.designer().isEditable());
            d.designer().execute("Insert Line", TextChanges.of(insert(FakeFormat.HEADER.length(), "zero\n")));
            assertEquals(TEXT, d.text());
            assertFalse(d.isModified());

            d.setReadOnly(false);
            assertFalse(d.readOnlyBannerShown());
            assertTrue(d.designer().isEditable());
        }
    }

    @Test
    public void executeLandsOneLabelledUndoEntry() {
        try (var d = open(TEXT)) {
            d.designer().execute("Insert Line", TextChanges.of(insert(FakeFormat.HEADER.length(), "zero\n")));

            assertEquals(FakeFormat.HEADER + "zero\nalpha\nbeta\ngamma\n", d.text());
            assertEquals("zero", d.viewOf(0).text());
            assertEquals("Undo Insert Line", d.undoLabel());
            assertTrue(d.isModified());

            d.undo();
            assertEquals(TEXT, d.text());
            assertFalse(d.isModified());

            d.redo();
            assertEquals(FakeFormat.HEADER + "zero\nalpha\nbeta\ngamma\n", d.text());
            assertTrue(d.isModified());
        }
    }

    @Test
    public void stateKeepsZoomAndSelection() {
        try (var d = open(TEXT)) {
            d.designer().zoomIn();
            d.select(2);
            var state = d.designer().getState(FileEditorStateLevel.FULL);

            d.designer().resetZoom();
            d.select(0);
            d.designer().setState(state);

            assertEquals(1.25, d.designer().viewState().zoom(), 1e-9);
            assertEquals(List.of(2), d.selectedKeys());
            assertEquals(TEXT, d.text());
        }
    }

    @Test
    public void aFormatProblemShowsABannerAboveTheText() {
        try (var d = open(FakeFormat.HEADER + "alpha\n!x\n")) {
            var banner = new AdpProblemNotifications().collectNotificationData(getProject(), d.file()).apply(d.composite());
            assertNotNull(banner);
            String text = ((EditorNotificationPanel) banner).getText();
            assertTrue(text, text.contains("Line 3, column 1"));
        }
        try (var d = DesignerDriver.openText(myFixture, "fine.txt", TEXT)) {
            assertNull(new AdpProblemNotifications().collectNotificationData(getProject(), d.file()).apply(d.composite()));
        }
    }
}
