package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.AddNodeTest.node;
import static etalii.adp.freemind.ui.DeleteTest.DELETE;
import static etalii.adp.freemind.ui.RenameTest.RENAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IFileEditorInput;
import org.junit.jupiter.api.Test;

import etalii.adp.testing.DesignerDriver;

/** FR-005, US2-AS5: Save, Save As, Revert and save-on-close behave as the text editor's. */
class SaveLifecycleTest {

    @Test
    void saveWritesTheEditAndClearsDirty() throws IOException {
        try (var d = DesignerDriver.openText("save.mm", MAP, MindMapEditor.ID)) {
            d.select(key("B")).run(RENAME).typeInPlace("Saved");
            assertTrue(d.isDirty());
            String edited = d.text();
            assertEquals(edited, new String(d.savedBytes(), UTF_8));
            assertFalse(d.isDirty());
            assertEquals(edited, Files.readString(onDisk(d.file())));
            assertEquals("Rename Node", d.undoLabel(), "saving keeps the undo history");
        }
    }

    @Test
    void revertRestoresTheSavedFile() {
        try (var d = DesignerDriver.openText("revert.mm", MAP, MindMapEditor.ID)) {
            d.select(key("A2")).run(DELETE);
            assertNull(d.figureOf(key("A2")));
            assertTrue(d.isDirty());
            d.run("org.eclipse.ui.file.revert");
            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());
            assertNotNull(d.figureOf(key("A2")), "the visual page shows the reverted map");
        }
    }

    @Test
    void closingADirtyEditorAsksToSave() throws IOException {
        try (var d = DesignerDriver.openText("close.mm", MAP, MindMapEditor.ID)) {
            assertFalse(d.editor().isSaveOnCloseNeeded());
            d.select(key("A2")).run(DELETE);
            assertTrue(d.editor().isSaveOnCloseNeeded(), "the platform prompts before closing");
            d.savedBytes();
            assertFalse(d.editor().isSaveOnCloseNeeded());
            d.select(key("B")).run(DELETE);
            d.setReadOnly(false); // closes without saving, then reopens
            assertTrue(Files.readString(onDisk(d.file())).contains("ID=\"B\""), "closing without saving leaves the file");
            assertFalse(d.text().contains("ID=\"A2\""), "the saved change is kept");
            assertFalse(d.isDirty());
        }
    }

    @Test
    void saveAsWritesANewFileAndSwitchesToIt() throws IOException {
        try (var d = DesignerDriver.openText("original.mm", MAP, MindMapEditor.ID)) {
            assertTrue(d.editor().isSaveAsAllowed());
            d.select(key("B")).run(RENAME).typeInPlace("Copied");
            String edited = d.text();
            List<String> problems = new ArrayList<>();
            answerSaveAsDialog("original.mm", "copy.mm", problems);
            d.editor().doSaveAs();
            d.settle();
            assertTrue(problems.isEmpty(), problems.toString());

            IFile copy = d.file().getParent().getFile(org.eclipse.core.runtime.Path.fromPortableString("copy.mm"));
            assertTrue(copy.exists());
            assertEquals(edited, Files.readString(onDisk(copy)));
            assertEquals(MAP, Files.readString(onDisk(d.file())), "the original file is unchanged");
            assertEquals(copy, ((IFileEditorInput) d.editor().getEditorInput()).getFile());
            assertEquals("copy.mm", d.editor().getPartName());
            assertFalse(d.isDirty());
            assertEquals("Copied", node(d, key("B")).text());

            d.select(key("A2")).run(DELETE);
            assertNull(d.figureOf(key("A2")), "the designer keeps working on the new file");
        }
    }

    /**
     * Fill in the platform's Save As dialog once it is open: replace the file name and press OK. If
     * it cannot be answered, it is cancelled so the run never blocks.
     */
    private static void answerSaveAsDialog(String originalName, String newName, List<String> problems) {
        Display display = Display.getCurrent();
        int[] attempts = { 0 };
        Runnable answer = new Runnable() {
            @Override
            public void run() {
                Shell dialog = display.getActiveShell();
                Text name = dialog == null ? null : findText(dialog, originalName);
                Button ok = dialog == null ? null : findButton(dialog, IDialogConstants.OK_ID);
                if (name == null || ok == null) {
                    if (++attempts[0] < 50) {
                        display.timerExec(100, this);
                    } else {
                        problems.add("the Save As dialog did not show");
                        for (Shell shell : display.getShells()) {
                            Button cancel = findButton(shell, IDialogConstants.CANCEL_ID);
                            if (cancel != null) {
                                cancel.notifyListeners(SWT.Selection, null);
                            }
                        }
                    }
                    return;
                }
                name.setText(newName);
                ok.notifyListeners(SWT.Selection, null);
            }
        };
        display.timerExec(100, answer);
    }

    private static Text findText(Control control, String content) {
        if (control instanceof Text text && content.equals(text.getText())) {
            return text;
        }
        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                Text found = findText(child, content);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Button findButton(Control control, int id) {
        if (control instanceof Button button && Integer.valueOf(id).equals(button.getData())) {
            return button;
        }
        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                Button found = findButton(child, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Path onDisk(IFile file) {
        return file.getLocation().toFile().toPath();
    }
}
