package etalii.adp.core.settings;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.UIUtil;

import etalii.adp.core.diagram.sample.SampleProvider;

/**
 * T035 (FR-011, FR-012, acceptance 3.2 and 3.3): the canvas section's three options with their
 * defaults, the opening zoom choices of the contract, "Not followed by" under an option a tool
 * fixes, and "Reset to defaults" resetting the page with nothing stored until Apply.
 */
@RunWith(JUnit4.class)
public class CanvasSectionTest extends BasePlatformTestCase {

    private AdpSettings settings;
    private CanvasSection section;
    private JComponent component;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        settings = FreshSettings.install(getTestRootDisposable());
        SampleProvider.register(getTestRootDisposable());
        FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new AdpToolsTest.Rival(), getTestRootDisposable());
        section = new CanvasSection();
        component = section.createComponent();
        section.reset();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            section.disposeUIResources();
        } finally {
            super.tearDown();
        }
    }

    private JBCheckBox box(String text) {
        return UIUtil.findComponentsOfType(component, JBCheckBox.class).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private ComboBox<Integer> zoom() {
        return UIUtil.findComponentOfType(component, ComboBox.class);
    }

    private List<String> shownLabels() {
        return UIUtil.findComponentsOfType(component, JLabel.class).stream().filter(JLabel::isVisible).map(JLabel::getText).toList();
    }

    @Test
    public void theThreeOptionsShowTheirDefaults() {
        assertFalse(box("Show grid").isSelected());
        assertTrue(box("Snap to grid").isSelected());
        assertEquals(Integer.valueOf(100), zoom().getSelectedItem());
        assertTrue(shownLabels().contains("Opening zoom:"));
        assertFalse(section.isModified());
    }

    @Test
    public void theOpeningZoomChoicesAreThoseOfTheContract() {
        List<Integer> choices = new ArrayList<>();
        for (int i = 0; i < zoom().getItemCount(); i++) {
            choices.add(zoom().getItemAt(i));
        }
        assertEquals(List.of(50, 75, 100, 125, 150, 200), choices);
    }

    @Test
    public void anOptionAToolFixesSaysWhichToolsDoNotFollowIt() {
        List<String> shown = shownLabels();
        assertTrue(shown.toString(), shown.contains("Not followed by: Rival Tool"));
        assertEquals(shown.toString(), 1, shown.stream().filter(text -> text.startsWith("Not followed by")).count());
    }

    @Test
    public void changesAreStoredOnlyOnApply() {
        box("Show grid").setSelected(true);
        box("Snap to grid").setSelected(false);
        zoom().setSelectedItem(150);
        assertTrue(section.isModified());
        assertEquals(CanvasOptions.DEFAULTS, settings.canvas());

        section.apply();
        assertEquals(new CanvasOptions(true, false, 1.5), settings.canvas());
        assertFalse(section.isModified());
    }

    @Test
    public void resetToDefaultsResetsThePageAndStoresNothingUntilApply() {
        settings.setCanvas(new CanvasOptions(true, false, 2.0));
        section.reset();
        assertTrue(box("Show grid").isSelected());

        ActionLink link = UIUtil.findComponentsOfType(component, ActionLink.class).stream().filter(l -> "Reset to defaults".equals(l.getText()))
                .findFirst().orElseThrow();
        link.doClick();

        assertFalse(box("Show grid").isSelected());
        assertTrue(box("Snap to grid").isSelected());
        assertEquals(Integer.valueOf(100), zoom().getSelectedItem());
        assertTrue(section.isModified());
        assertEquals(new CanvasOptions(true, false, 2.0), settings.canvas());

        section.apply();
        assertEquals(CanvasOptions.DEFAULTS, settings.canvas());
    }

    @Test
    public void theLabelsAreSearchable() {
        assertTrue(section.searchableLabels().containsAll(List.of("Canvas", "Show grid", "Snap to grid", "Opening zoom", "Reset to defaults")));
    }
}
