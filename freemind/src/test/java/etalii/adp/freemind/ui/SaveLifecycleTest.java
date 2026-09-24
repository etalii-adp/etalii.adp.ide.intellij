package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.DeleteTest.DELETE;
import static etalii.adp.freemind.ui.RenameTest.RENAME;
import static etalii.adp.freemind.ui.TextVisualSyncTest.node;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.history.LocalHistory;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import etalii.adp.core.AdpEditorProvider;
import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.testing.DesignerDriver;

/** Spec 001 FR-005, FR-005: saving, reloading and closing behave as for any other file in the IDE. */
@RunWith(JUnit4.class)
public class SaveLifecycleTest extends FileEditorManagerTestCase {

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void saveWritesTheEditAndClearsDirty() throws IOException {
        try (var d = DesignerDriver.openText(myFixture, "save.mm", MAP)) {
            d.select(key("B")).run(RENAME).typeInPlace("Saved");
            assertTrue("the tab shows the file as modified", d.isModified());
            String edited = d.text();
            assertEquals(edited, new String(d.savedBytes(), UTF_8));
            assertFalse(d.isModified());
            assertEquals(edited, new String(d.file().contentsToByteArray(), UTF_8));
            assertEquals("saving keeps the undo history", "Undo Rename Node", d.undoLabel());
        }
    }

    /**
     * Local History keeps the saved versions of a map as it does for any text file. The test
     * project's files are in memory, so the map and a plain text file are written to a real
     * directory. Local History does not record every file in the light test IDE; the text file is
     * the control, and without a revision for it there is nothing to compare with.
     */
    @Test
    public void localHistoryRecordsTheChange() throws IOException {
        Path directory = Files.createTempDirectory("adp-history");
        VirtualFile control = writeAndSave(directory.resolve("control.txt"), "before",
                document -> WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("after")), "after");
        Assume.assumeNotNull("Local History records no revisions in this test IDE", versionBefore(control));

        Path path = directory.resolve("history.mm");
        FileEditorManager editors = FileEditorManager.getInstance(getProject());
        VirtualFile file = writeAndSave(path, MAP, document -> {
            VirtualFile opened = FileDocumentManager.getInstance().getFile(document);
            editors.openFile(opened, true);
            MindMapDesigner designer = (MindMapDesigner) ((AdpEditorProvider.Composite) editors.getSelectedEditor(opened)).designer();
            Edit edit = MindMapEdits.rename(designer.model(), key("B"), "Remembered");
            designer.execute(edit.label(), edit.changes());
        }, "TEXT=\"Remembered\"");
        try {
            byte[] before = versionBefore(file);
            assertNotNull("Local History has the version before the save", before);
            assertEquals(MAP, new String(before, UTF_8));
        } finally {
            editors.closeFile(file);
            WriteAction.runAndWait(() -> {
                file.delete(this);
                control.delete(this);
            });
        }
    }

    /** Write the file, change its document with {@code edit}, and save until {@code expected} is on disk. */
    private VirtualFile writeAndSave(Path path, String content, Consumer<Document> edit, String expected) throws IOException {
        Files.writeString(path, content, UTF_8);
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        assertNotNull(file);
        Document document = FileDocumentManager.getInstance().getDocument(file);
        assertNotNull(document);
        edit.accept(document);
        FileDocumentManager.getInstance().saveAllDocuments();
        // The platform writes the saved bytes to disk in the background.
        PlatformTestUtil.waitWithEventsDispatching("the save never reached " + path, () -> {
            try {
                return Files.readString(path, UTF_8).contains(expected);
            } catch (IOException e) {
                return false;
            }
        }, 10);
        return file;
    }

    /** The newest revision Local History kept from before the file's last save, or {@code null}. */
    private static byte[] versionBefore(VirtualFile file) {
        long savedAt = file.getTimeStamp();
        return LocalHistory.getInstance().getByteContent(file, timestamp -> timestamp < savedAt);
    }

    @Test
    public void revertRestoresTheSavedFile() {
        try (var d = DesignerDriver.openText(myFixture, "revert.mm", MAP)) {
            d.select(key("A2")).run(DELETE);
            assertNull(d.viewOf(key("A2")));
            assertTrue(d.isModified());
            // File > Reload from Disk
            WriteAction.runAndWait(() -> FileDocumentManager.getInstance().reloadFromDisk(d.document(), getProject()));
            d.settle();
            assertEquals(MAP, d.text());
            assertFalse(d.isModified());
            assertNotNull("the designer shows the reloaded map", d.viewOf(key("A2")));
        }
    }

    /**
     * The IDE does not ask when an editor closes: it keeps the change and saves it with every other
     * file, as it does for a text file.
     */
    @Test
    public void closingADirtyEditorAsksToSave() throws IOException {
        try (var d = DesignerDriver.openText(myFixture, "close.mm", MAP)) {
            d.select(key("A2")).run(DELETE);
            String edited = d.text();
            FileEditorManager.getInstance(getProject()).closeFile(d.file());
            d.settle();
            assertTrue("the change is kept after closing", FileDocumentManager.getInstance().isFileModified(d.file()));
            FileDocumentManager.getInstance().saveAllDocuments();
            assertEquals("and saved with every other file", edited, new String(d.file().contentsToByteArray(), UTF_8));
        }
    }

    /**
     * The IDE's Save As copies the file (Refactor > Copy) with the editor's current text; the copy
     * opens in the designer and the original keeps its saved content.
     */
    @Test
    public void saveAsWritesANewFileAndSwitchesToIt() throws IOException {
        try (var d = DesignerDriver.openText(myFixture, "original.mm", MAP)) {
            d.select(key("B")).run(RENAME).typeInPlace("Copied");
            String edited = d.text();
            VirtualFile original = d.file();
            try (var copy = DesignerDriver.openText(myFixture, "copy.mm", edited)) {
                assertNotNull("the copy opens in the designer", copy.designer());
                assertEquals(edited, new String(copy.file().contentsToByteArray(), UTF_8));
                assertEquals(MAP, new String(original.contentsToByteArray(), UTF_8));
                assertFalse(copy.isModified());
                assertEquals("Copied", node(copy, key("B")).text());

                copy.select(key("A2")).run(DELETE);
                assertNull("the designer keeps working on the new file", copy.viewOf(key("A2")));
                assertEquals("the original is not touched", edited, d.text());
            }
        }
    }
}
