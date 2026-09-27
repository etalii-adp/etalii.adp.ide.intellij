package etalii.adp.core.settings;

import javax.swing.JComponent;
import javax.swing.table.TableModel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;

import etalii.adp.core.AdpEditorProvider;
import etalii.adp.core.diagram.sample.SampleFiles;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.testing.DesignerDriver;

/**
 * T031 (FR-009, FR-010, SC-002, acceptance 2.1 to 2.3), end to end through the ADP page: untick
 * the sample designer and apply, and a sample file opens in the text editor with the designer not
 * offered, while one already open stays open, unmodified, in its designer; tick it and apply, and a
 * newly opened file gets the designer again.
 */
@RunWith(JUnit4.class)
public class TurnDesignerOffTest extends FileEditorManagerTestCase {

    private AdpConfigurable page;
    private JComponent component;

    @Override
    public void setUp() {
        super.setUp();
        FreshSettings.install(getTestRootDisposable());
        SampleProvider.register(getTestRootDisposable());
    }

    @Override
    public void tearDown() throws Exception {
        try {
            if (page != null) {
                page.disposeUIResources();
            }
        } finally {
            super.tearDown();
        }
    }

    private DesignerDriver open(String name) {
        return DesignerDriver.openText(myFixture, name, SampleFiles.read("lanes.adpsample"));
    }

    /** Open the ADP page as the Settings dialog does, set the sample designer's check box, and apply. */
    private void setSampleOn(boolean on) {
        page = new AdpConfigurable();
        component = page.createComponent();
        page.reset();
        JBTable table = UIUtil.findComponentOfType(component, JBTable.class);
        TableModel model = table.getModel();
        for (int row = 0; row < model.getRowCount(); row++) {
            if ("Sample Designer".equals(model.getValueAt(row, 1))) {
                model.setValueAt(on, row, 0);
            }
        }
        assertTrue(page.isModified());
        page.apply();
        page.disposeUIResources();
        page = null;
    }

    @Test
    public void offOpensFilesWithoutTheDesignerAndOnAgainRestoresIt() {
        try (var open = open("open.adpsample")) {
            assertEquals(SampleProvider.EDITOR_TYPE_ID, open.editorTypeIdUsed());
            String before = open.text();

            setSampleOn(false);

            try (var later = open("later.adpsample")) {
                assertNull("no designer", later.designer());
                assertFalse(later.editorTypeIdsOffered().contains(SampleProvider.EDITOR_TYPE_ID));
                assertFalse(AdpEditorProvider.acceptedByAny(later.file()));
            }
            assertInstanceOf(FileEditorManager.getInstance(getProject()).getSelectedEditor(open.file()), AdpEditorProvider.Composite.class);
            assertNotNull("the open designer stays", open.designer());
            assertFalse(open.isModified());
            assertEquals(before, open.text());

            setSampleOn(true);

            try (var again = open("again.adpsample")) {
                assertEquals(SampleProvider.EDITOR_TYPE_ID, again.editorTypeIdUsed());
                assertTrue(again.editorTypeIdsOffered().contains(SampleProvider.EDITOR_TYPE_ID));
            }
        }
    }
}
