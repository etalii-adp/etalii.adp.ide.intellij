package etalii.adp.core.settings;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * T010 (FR-001, FR-002): the ADP page's dialog contract over its sections: modified when any
 * section is, applying the changed sections and publishing the topic once, nothing on reset or
 * Cancel, and its child pages in section order.
 */
@RunWith(JUnit4.class)
public class AdpConfigurableTest extends BasePlatformTestCase {

    /** A section that records what the page asks of it. */
    static final class FakeSection implements SettingsSection {

        final int order;
        final List<Configurable> children;
        boolean modified;
        int applied;
        int reset;
        int disposed;

        FakeSection(int order, Configurable... children) {
            this.order = order;
            this.children = List.of(children);
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public JComponent createComponent() {
            return new JLabel("section " + order);
        }

        @Override
        public boolean isModified() {
            return modified;
        }

        @Override
        public void apply() {
            applied++;
            modified = false;
        }

        @Override
        public void reset() {
            reset++;
            modified = false;
        }

        @Override
        public List<String> searchableLabels() {
            return List.of("Label " + order);
        }

        @Override
        public List<Configurable> children() {
            return children;
        }

        @Override
        public void disposeUIResources() {
            disposed++;
        }
    }

    /** A child page with a name only. */
    record Child(String name) implements Configurable {

        @Override
        public String getDisplayName() {
            return name;
        }

        @Override
        public JComponent createComponent() {
            return new JLabel(name);
        }

        @Override
        public boolean isModified() {
            return false;
        }

        @Override
        public void apply() {
        }
    }

    private FakeSection first;
    private FakeSection second;
    private int published;
    private AdpSettings settings;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        settings = FreshSettings.install(getTestRootDisposable());
        first = new FakeSection(10, new Child("First's child"));
        second = new FakeSection(5, new Child("Second's child"));
        ExtensionTestUtil.maskExtensions(SettingsSection.EP_NAME, List.of(first, second), getTestRootDisposable());
        ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(AdpSettingsListener.TOPIC,
                () -> published++);
    }

    @Test
    public void theIdAndNameAreThePages() {
        AdpConfigurable page = new AdpConfigurable();
        assertInstanceOf(page, SearchableConfigurable.class);
        assertEquals("etalii.adp.settings", page.getId());
        assertEquals("ADP", page.getDisplayName());
        assertNotNull(page.createComponent());
    }

    @Test
    public void modifiedIsAnySections() {
        AdpConfigurable page = new AdpConfigurable();
        page.createComponent();
        assertFalse(page.isModified());
        second.modified = true;
        assertTrue(page.isModified());
    }

    @Test
    public void applyAppliesTheChangedSectionsAndPublishesOnce() throws ConfigurationException {
        AdpConfigurable page = new AdpConfigurable();
        page.createComponent();
        first.modified = true;
        second.modified = true;
        page.apply();
        assertEquals(1, first.applied);
        assertEquals(1, second.applied);
        assertEquals(1, published);
    }

    @Test
    public void applyWithNothingChangedPublishesNothing() throws ConfigurationException {
        AdpConfigurable page = new AdpConfigurable();
        page.createComponent();
        page.apply();
        assertEquals(0, first.applied);
        assertEquals(0, second.applied);
        assertEquals(0, published);
    }

    @Test
    public void resetAndCancelLeaveTheSettingsUntouched() {
        String before = JDOMUtil.write(settings.getState());
        AdpConfigurable page = new AdpConfigurable();
        page.createComponent();
        first.modified = true;
        page.reset();
        assertEquals(1, first.reset);
        assertEquals(1, second.reset);
        page.disposeUIResources();
        assertEquals(1, first.disposed);
        assertEquals(before, JDOMUtil.write(settings.getState()));
        assertEquals(0, published);
    }

    @Test
    public void theChildPagesComeFromTheSectionsInOrder() {
        List<String> names = new ArrayList<>();
        for (Configurable child : new AdpConfigurable().getConfigurables()) {
            names.add(child.getDisplayName());
        }
        assertEquals("second has order 5, first 10", List.of("Second's child", "First's child"), names);
    }
}
