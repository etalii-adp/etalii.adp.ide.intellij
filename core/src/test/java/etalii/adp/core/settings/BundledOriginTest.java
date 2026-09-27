package etalii.adp.core.settings;

import java.util.List;
import java.util.Locale;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.table.TableModel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;

import etalii.adp.core.diagram.sample.BundledSampleProvider;
import etalii.adp.core.diagram.sample.SampleProvider;

/**
 * T048 (FR-016, acceptance 5.1 to 5.3): a designer interpreted from a bundled DEDL definition
 * reads as that definition, its DEDL version and the etalii.adp revision it was copied from; one
 * the plug-in cannot interpret is listed as not loaded with each problem; a conflict with another
 * designer is shown; and the page offers no way to load a definition from elsewhere.
 */
@RunWith(JUnit4.class)
public class BundledOriginTest extends BasePlatformTestCase {

    private AdpConfigurable page;
    private JComponent component;
    private JBTable table;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        FreshSettings.install(getTestRootDisposable());
        SampleProvider.register(getTestRootDisposable());
        BundledSampleProvider.register(getTestRootDisposable());
        BundledSampleProvider.registerBroken(getTestRootDisposable());
        page = new AdpConfigurable();
        component = page.createComponent();
        page.reset();
        table = UIUtil.findComponentOfType(component, JBTable.class);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            page.disposeUIResources();
        } finally {
            super.tearDown();
        }
    }

    private int row(String name) {
        TableModel model = table.getModel();
        for (int i = 0; i < model.getRowCount(); i++) {
            if (name.equals(model.getValueAt(i, 1))) {
                return i;
            }
        }
        throw new AssertionError(name + " is not listed");
    }

    private String detail(String name) {
        int row = row(name);
        table.setRowSelectionInterval(row, row);
        return UIUtil.findComponentsOfType(component, JBTextArea.class).getFirst().getText();
    }

    @Test
    public void theOriginNamesTheDefinitionItsVersionAndTheRevisionItWasCopiedFrom() {
        assertEquals("DEDL definition sample-flow (DEDL 0.3), copied from etalii.adp at a1b2c3d", table.getModel().getValueAt(row("Bundled Sample Designer"), 4));
        assertEquals("Loaded", table.getModel().getValueAt(row("Bundled Sample Designer"), 5));
    }

    @Test
    public void aBundledDefinitionThePluginCannotInterpretIsNotLoadedWithEachProblem() {
        assertEquals("Not loaded", table.getModel().getValueAt(row("Broken Bundled Designer"), 5));
        String detail = detail("Broken Bundled Designer");
        assertTrue(detail, detail.contains("toolbox: names undeclared type 'nothing'"));
    }

    @Test
    public void aConflictWithAnotherDesignerIsShown() {
        String detail = detail("Bundled Sample Designer");
        assertTrue(detail, detail.contains("Shares file types with Sample Designer"));
    }

    @Test
    public void thePageOffersNoWayToLoadADefinitionFromElsewhere() {
        assertEquals(List.of(), UIUtil.findComponentsOfType(component, TextFieldWithBrowseButton.class));
        assertEquals(List.of(), UIUtil.findComponentsOfType(component, JFileChooser.class));
        for (AbstractButton button : UIUtil.findComponentsOfType(component, AbstractButton.class)) {
            String text = String.valueOf(button.getText()).toLowerCase(Locale.ROOT);
            assertFalse(text, text.contains("load") || text.contains("browse") || text.contains("import") || text.contains("folder"));
        }
    }
}
