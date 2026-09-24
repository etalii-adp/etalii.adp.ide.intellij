package etalii.adp.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.draw2d.Label;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.ui.editors.text.TextEditor;
import org.junit.jupiter.api.Test;

import etalii.adp.testing.DesignerDriver;
import etalii.adp.testing.Page;

/** Framework behaviour, driven through the format-agnostic {@link LineListEditor}. */
class AdpDesignerEditorTest {

    private static final String TEXT = "alpha\nbeta\ngamma\n";

    @Test
    void theTextPageIsATextEditorOnTheSameDocument() {
        try (var d = DesignerDriver.openText("lines.adptest", TEXT, LineListEditor.ID)) {
            var editor = d.editor();
            TextEditor text = assertInstanceOf(TextEditor.class, editor.textEditor());
            assertSame(editor.document(), text.getDocumentProvider().getDocument(text.getEditorInput()));
            assertEquals(TEXT, d.text());
            assertEquals("beta", label(d, 1));
            assertTrue(editor.isVisualPageActive());
        }
    }

    @Test
    void saveDirtyAndRevertDelegateToTheTextEditor() {
        try (var d = DesignerDriver.openText("lines.adptest", TEXT, LineListEditor.ID)) {
            d.editText(t -> t.replace("beta", "BETA"));
            assertTrue(d.isDirty());
            assertTrue(d.editor().textEditor().isDirty());

            assertArrayEquals("alpha\nBETA\ngamma\n".getBytes(UTF_8), d.savedBytes());
            assertFalse(d.isDirty());

            d.editText(t -> t + "delta\n");
            assertTrue(d.isDirty());
            d.editor().doRevertToSaved();
            d.settle();
            assertEquals("alpha\nBETA\ngamma\n", d.text());
            assertFalse(d.isDirty());
        }
    }

    @Test
    void aDocumentChangeReparsesOncePerEventLoopTurn() throws Exception {
        try (var d = DesignerDriver.openText("lines.adptest", TEXT, LineListEditor.ID)) {
            LineListEditor editor = (LineListEditor) d.editor();
            int before = editor.parseCount;
            IDocument document = editor.document();

            document.replace(0, 0, "one\n");
            document.replace(0, 0, "two\n");
            assertEquals(before, editor.parseCount);

            d.settle();
            assertEquals(before + 1, editor.parseCount);
            assertEquals("two", label(d, 0));
            assertEquals("one", label(d, 1));
        }
    }

    @Test
    void aFormatProblemShowsLineAndColumnAndChangesNothing() {
        try (var d = DesignerDriver.openText("lines.adptest", TEXT, LineListEditor.ID)) {
            d.editText(t -> t.replace("beta", "!"));
            d.showPage(Page.VISUAL);

            assertTrue(d.problemShown());
            assertNull(d.editor().model());
            assertFalse(d.editor().isEditable());
            String message = d.editor().problemMessage();
            assertTrue(message.contains("Line 2, column 1"), message);
            assertEquals("alpha\n!\ngamma\n", d.text());
        }
    }

    @Test
    void aMalformedFileOpensOnTheTextPageUnmodified() {
        try (var d = DesignerDriver.openText("bad.adptest", "alpha\n!\n", LineListEditor.ID)) {
            assertTrue(d.problemShown());
            assertFalse(d.editor().isVisualPageActive());
            assertFalse(d.isDirty());
            assertEquals("alpha\n!\n", d.text());
        }
    }

    @Test
    void aReadOnlyInputShowsTheBannerAndCannotBeEdited() {
        try (var d = DesignerDriver.openText("lines.adptest", TEXT, LineListEditor.ID)) {
            d.setReadOnly(true);

            assertNotNull(d.editor().readOnlyMessage());
            assertFalse(d.editor().isEditable());
            d.editor().execute("Insert Line", new InsertEdit(0, "zero\n"));
            d.settle();
            assertEquals(TEXT, d.text());
            assertFalse(d.isDirty());
        }
    }

    @Test
    void executeLandsOneLabelledUndoEntry() {
        try (var d = DesignerDriver.openText("lines.adptest", TEXT, LineListEditor.ID)) {
            d.editor().execute("Insert Line", new InsertEdit(0, "zero\n"));

            assertEquals("zero\n" + TEXT, d.text());
            assertEquals("zero", label(d, 0));
            assertEquals("Insert Line", d.undoLabel());
            assertTrue(d.isDirty());

            d.undo();
            assertEquals(TEXT, d.text());
            assertFalse(d.isDirty());

            d.redo();
            assertEquals("zero\n" + TEXT, d.text());
            assertTrue(d.isDirty());
        }
    }

    private static String label(DesignerDriver d, int line) {
        return ((Label) d.figureOf(line)).getText();
    }
}
