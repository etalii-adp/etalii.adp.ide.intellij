package etalii.adp.core.settings;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.table.TableModel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.options.Configurable;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;

import etalii.adp.core.diagram.sample.BrokenSampleProvider;
import etalii.adp.core.diagram.sample.SampleProvider;

/**
 * T030 (FR-004, FR-007, FR-008, FR-017, SC-004, acceptance 1.2 to 1.4): the tool list shows
 * every tool with its file types, version, origin and status; selecting one shows its problems,
 * conflicts and the options it does not follow; the File Types link goes to the platform's own
 * page, and nothing on the page repeats a default-editor choice.
 */
@RunWith(JUnit4.class)
public class ToolsSectionTest extends BasePlatformTestCase {

    private AdpSettings settings;
    private ToolsSection section;
    private JComponent component;
    private JBTable table;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        settings = FreshSettings.install(getTestRootDisposable());
        SampleProvider.register(getTestRootDisposable());
        BrokenSampleProvider.register(getTestRootDisposable());
        FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new AdpToolsTest.Rival(), getTestRootDisposable());
        section = new ToolsSection();
        component = section.createComponent();
        section.reset();
        table = UIUtil.findComponentOfType(component, JBTable.class);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            section.disposeUIResources();
        } finally {
            super.tearDown();
        }
    }

    private int row(String name) {
        for (int i = 0; i < table.getRowCount(); i++) {
            if (name.equals(table.getValueAt(i, 1))) {
                return i;
            }
        }
        throw new AssertionError(name + " is not listed");
    }

    private String select(String name) {
        int row = row(name);
        table.setRowSelectionInterval(row, row);
        return section.detail();
    }

    @Test
    public void theColumnsAreThoseOfTheContract() {
        List<String> columns = new ArrayList<>();
        TableModel model = table.getModel();
        for (int i = 0; i < model.getColumnCount(); i++) {
            columns.add(model.getColumnName(i));
        }
        assertEquals(List.of("On", "Name", "File types", "Version", "Origin", "Status"), columns);
        assertEquals(Boolean.class, model.getColumnClass(0));
        assertTrue(model.isCellEditable(0, 0));
        assertFalse(model.isCellEditable(0, 1));
    }

    @Test
    public void everyToolIsListedWithItsFileTypesOriginAndStatus() {
        assertEquals(3, table.getRowCount());
        int sample = row("Sample Diagram");
        assertEquals(Boolean.TRUE, table.getValueAt(sample, 0));
        assertEquals(".adpsample", table.getValueAt(sample, 2));
        String version = AdpTools.all().stream().filter(d -> d.id().equals(SampleProvider.EDITOR_TYPE_ID)).findFirst().orElseThrow().version();
        assertEquals(version, table.getValueAt(sample, 3));
        assertEquals("Built into ADP", table.getValueAt(sample, 4));
        assertEquals("Loaded", table.getValueAt(sample, 5));
        assertEquals(".adprival, .adpsample", table.getValueAt(row("Rival Tool"), 2));
        assertEquals("Not loaded", table.getValueAt(row("Broken Sample Diagram"), 5));
    }

    @Test
    public void selectingABrokenToolShowsEachProblem() {
        String detail = select("Broken Sample Diagram");
        assertTrue(detail, detail.contains("Not loaded"));
        assertTrue(detail, detail.contains("toolbox: names undeclared type 'nothing'"));
    }

    @Test
    public void aConflictIsShownOnBothToolsWithWhichIsUsedAndHowToChange() {
        String sample = select("Sample Diagram");
        assertTrue(sample, sample.contains("Shares file types with Rival Tool"));
        assertTrue(sample, sample.contains("Sample Diagram is used"));
        assertTrue(sample, sample.contains("turn"));
        String rival = select("Rival Tool");
        assertTrue(rival, rival.contains("Shares file types with Sample Diagram"));
        assertTrue(rival, rival.contains("Sample Diagram is used"));
    }

    @Test
    public void theOptionsAToolDoesNotFollowAreShown() {
        String rival = select("Rival Tool");
        assertTrue(rival, rival.contains("Does not follow: Show grid"));
        assertFalse(select("Sample Diagram").contains("Does not follow"));
    }

    @Test
    public void theFileTypesLinkGoesToThePlatformsOwnPage() {
        List<String> links = UIUtil.findComponentsOfType(component, ActionLink.class).stream().map(ActionLink::getText).toList();
        assertTrue(links.toString(), links.contains("File types and default editors…"));
        assertTrue("the platform has the page the link opens",
                Configurable.APPLICATION_CONFIGURABLE.getExtensionList().stream().anyMatch(ep -> ToolsSection.FILE_TYPES_ID.equals(ep.id)));
    }

    @Test
    public void nothingOnThePageRepeatsADefaultEditorChoice() {
        assertEquals(List.of(), UIUtil.findComponentsOfType(component, JComboBox.class));
    }

    @Test
    public void turningAToolOffIsBufferedUntilApply() {
        int sample = row("Sample Diagram");
        table.getModel().setValueAt(false, sample, 0);
        assertTrue(section.isModified());
        assertFalse(settings.isOff(SampleProvider.EDITOR_TYPE_ID));

        section.apply();
        assertTrue(settings.isOff(SampleProvider.EDITOR_TYPE_ID));
        assertFalse(section.isModified());

        table.getModel().setValueAt(true, sample, 0);
        section.reset();
        assertEquals(Boolean.FALSE, table.getValueAt(row("Sample Diagram"), 0));
        assertFalse(section.isModified());
    }

    @Test
    public void theLabelsAreSearchable() {
        assertTrue(section.searchableLabels().containsAll(List.of("Tools", "File types and default editors…")));
    }
}
