package etalii.adp.core;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.testing.DesignerDriver;

/** With "strip trailing spaces on save" on, a designer's file keeps its spaces; other files do not (research R5). */
@RunWith(JUnit4.class)
public class StripTrailingSpacesTest extends FileEditorManagerTestCase {

    private String stripBefore;
    private boolean keepOnCaretLineBefore;

    @Override
    public void setUp() {
        super.setUp();
        FakeFormat.register(getTestRootDisposable());
        EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
        stripBefore = settings.getStripTrailingSpaces();
        keepOnCaretLineBefore = settings.isKeepTrailingSpacesOnCaretLine();
        settings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
        settings.setKeepTrailingSpacesOnCaretLine(false);
    }

    @Override
    public void tearDown() throws Exception {
        try {
            EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
            settings.setStripTrailingSpaces(stripBefore);
            settings.setKeepTrailingSpacesOnCaretLine(keepOnCaretLineBefore);
        } finally {
            super.tearDown();
        }
    }

    @Test
    public void aDesignersFileKeepsItsTrailingSpaces() {
        try (var d = DesignerDriver.openText(myFixture, "items.txt", FakeFormat.HEADER + "alpha   \nbeta\n")) {
            d.editText(t -> t + "gamma\t \n");

            assertEquals(FakeFormat.HEADER + "alpha   \nbeta\ngamma\t \n", new String(d.savedBytes(), UTF_8));
        }
    }

    @Test
    public void anotherFileIsStrippedAsUsual() {
        try (var d = DesignerDriver.openText(myFixture, "plain.txt", "alpha   \nbeta\n")) {
            assertNull(d.designer());
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(),
                    () -> d.document().insertString(d.document().getTextLength(), "gamma \n"));

            assertEquals("alpha\nbeta\ngamma\n", new String(d.savedBytes(), UTF_8));
        }
    }
}
