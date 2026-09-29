package etalii.adp.core.settings;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.UIUtil;

import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.diagram.sample.SettingSampleProvider;

/**
 * T044 (FR-014, FR-015, acceptance 4.1 to 4.3): a tool that declares settings gets a page
 * named after it under ADP, with one editor of the right kind per setting; a tool without
 * settings gets none; values are stored under the tool's id, kept while it is off and after
 * it is uninstalled, and read back when it returns; a declaration that breaks a rule is a problem
 * and gets no page.
 */
@RunWith(JUnit4.class)
public class ToolSettingsConfigurableTest extends BasePlatformTestCase {

    private AdpSettings settings;
    private int published;
    private final List<Configurable> opened = new ArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        settings = FreshSettings.install(getTestRootDisposable());
        SampleProvider.register(getTestRootDisposable());
        ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(AdpSettingsListener.TOPIC,
                () -> published++);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            opened.forEach(Configurable::disposeUIResources);
        } finally {
            super.tearDown();
        }
    }

    private List<Configurable> pages() {
        return List.of(new AdpConfigurable().getConfigurables());
    }

    private Configurable page(String name) {
        Configurable page = pages().stream().filter(c -> name.equals(c.getDisplayName())).findFirst().orElseThrow();
        opened.add(page);
        return page;
    }

    @Test
    public void aToolWithSettingsGetsAPageNamedAfterIt() {
        SettingSampleProvider.register(getTestRootDisposable());
        Configurable page = page("Settings Sample Diagram");
        assertInstanceOf(page, SearchableConfigurable.class);
        assertEquals("etalii.adp.settings.etalii.adp.sample.settings", ((SearchableConfigurable) page).getId());

        JComponent component = page.createComponent();
        page.reset();
        JBCheckBox compact = UIUtil.findComponentOfType(component, JBCheckBox.class);
        assertEquals("Compact rows", compact.getText());
        assertFalse(compact.isSelected());
        JBIntSpinner depth = UIUtil.findComponentOfType(component, JBIntSpinner.class);
        assertEquals(3, depth.getNumber());
        assertEquals(1, depth.getMin());
        assertEquals(9, depth.getMax());
        @SuppressWarnings("unchecked")
        ComboBox<String> direction = UIUtil.findComponentOfType(component, ComboBox.class);
        assertEquals("right", direction.getSelectedItem());
        assertEquals(2, direction.getItemCount());
    }

    @Test
    public void aToolWithoutSettingsGetsNoPage() {
        assertEquals(List.of(), pages().stream().map(Configurable::getDisplayName).toList());
    }

    @Test
    public void valuesAreStoredUnderTheToolsIdOnApply() throws Exception {
        SettingSampleProvider.register(getTestRootDisposable());
        Configurable page = page("Settings Sample Diagram");
        JComponent component = page.createComponent();
        page.reset();
        UIUtil.findComponentOfType(component, JBCheckBox.class).setSelected(true);
        UIUtil.findComponentOfType(component, JBIntSpinner.class).setNumber(7);
        assertTrue(page.isModified());
        assertFalse(settings.yesNo(SettingSampleProvider.EDITOR_TYPE_ID, SettingSampleProvider.COMPACT));

        page.apply();
        assertTrue(settings.yesNo(SettingSampleProvider.EDITOR_TYPE_ID, SettingSampleProvider.COMPACT));
        assertEquals(7, settings.number(SettingSampleProvider.EDITOR_TYPE_ID, SettingSampleProvider.DEPTH));
        assertFalse(page.isModified());
        assertEquals(1, published);
    }

    @Test
    public void theValuesAreKeptWhileOffAndAfterUninstallingAndReadBackOnReturn() {
        settings.setValue(SettingSampleProvider.EDITOR_TYPE_ID, SettingSampleProvider.DIRECTION, "left");
        settings.setOff(SettingSampleProvider.EDITOR_TYPE_ID, true);

        Disposable installed = Disposer.newDisposable(getTestRootDisposable(), "installed");
        SettingSampleProvider.register(installed);
        Configurable page = page("Settings Sample Diagram");
        JComponent component = page.createComponent();
        page.reset();
        @SuppressWarnings("unchecked")
        ComboBox<String> direction = UIUtil.findComponentOfType(component, ComboBox.class);
        assertEquals("while off, the page is shown and editable", "left", direction.getSelectedItem());
        assertTrue(direction.isEnabled());

        Disposer.dispose(installed);
        assertEquals(List.of(), pages());
        assertEquals("kept after uninstalling", "left", settings.choice(SettingSampleProvider.EDITOR_TYPE_ID, SettingSampleProvider.DIRECTION));

        SettingSampleProvider.register(getTestRootDisposable());
        assertEquals("read back on return", "left", settings.choice(SettingSampleProvider.EDITOR_TYPE_ID, SettingSampleProvider.DIRECTION));
        assertEquals(1, pages().size());
    }

    @Test
    public void aDeclarationThatBreaksARuleIsAProblemAndGetsNoPage() {
        SettingSampleProvider broken = SettingSampleProvider.register(getTestRootDisposable(),
                List.of(ToolSetting.number("depth", "Depth", 12, 1, 9)));
        assertEquals(List.of("setting 'depth': the default 12 is outside 1 to 9"), broken.problems());
        assertEquals(ToolInfo.Status.NOT_LOADED, broken.toolInfo().status());
        assertEquals(List.of(), pages());
    }
}
