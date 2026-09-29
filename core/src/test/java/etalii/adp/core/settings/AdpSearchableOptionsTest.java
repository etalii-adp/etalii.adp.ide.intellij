package etalii.adp.core.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.JComponent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.ide.ui.search.SearchableOptionProcessor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import etalii.adp.core.diagram.sample.SampleDefinition;
import etalii.adp.core.diagram.sample.SampleMapping;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.diagram.view.DiagramEditorProvider;

/**
 * T011 (FR-001, research R4): the settings search finds the ADP page by "ADP", by every installed
 * tool's name and by every section's labels, and each tool's own page by its labels.
 */
@RunWith(JUnit4.class)
public class AdpSearchableOptionsTest extends BasePlatformTestCase {

    /** One option contributed to the search index. */
    record Option(String text, String configurableId, String displayName) {
    }

    /** A tool with one setting of its own. */
    static final class Configured extends DiagramEditorProvider {

        Configured() {
            super(SampleDefinition.builder(), SampleMapping::new);
        }

        @Override
        protected Set<String> extensions() {
            return Set.of("adpconfigured");
        }

        @Override
        protected boolean sniff(byte[] head) {
            return true;
        }

        @Override
        protected String toolName() {
            return "Configured Tool";
        }

        @Override
        public String getEditorTypeId() {
            return "etalii.adp.configured";
        }

        @Override
        public List<ToolSetting> settings() {
            return List.of(ToolSetting.yesNo("compact", "Compact rows", false));
        }
    }

    /** A section with labels and no component. */
    record LabelledSection(List<String> searchableLabels) implements SettingsSection {

        @Override
        public int order() {
            return 1;
        }

        @Override
        public JComponent createComponent() {
            return null;
        }

        @Override
        public boolean isModified() {
            return false;
        }

        @Override
        public void apply() {
        }

        @Override
        public void reset() {
        }
    }

    private List<Option> contributed() {
        List<Option> options = new ArrayList<>();
        new AdpSearchableOptions().processOptions(new SearchableOptionProcessor() {
            @Override
            public void addOptions(String text, String path, String hit, String configurableId, String configurableDisplayName, boolean applyStemming) {
                options.add(new Option(text, configurableId, configurableDisplayName));
            }
        });
        return options;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        FreshSettings.install(getTestRootDisposable());
        ExtensionTestUtil.maskExtensions(SettingsSection.EP_NAME, List.of(new LabelledSection(List.of("Show grid", "Opening zoom"))),
                getTestRootDisposable());
    }

    @Test
    public void thePageIsFoundByItsNameEveryToolAndEveryLabel() {
        SampleProvider.register(getTestRootDisposable());
        List<Option> options = contributed();
        for (String text : List.of("ADP", "Sample Diagram", "Show grid", "Opening zoom")) {
            assertTrue(text + " in " + options, options.contains(new Option(text, "etalii.adp.settings", "ADP")));
        }
    }

    @Test
    public void aToolsOwnPageIsFoundByItsLabels() {
        FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new Configured(), getTestRootDisposable());
        List<Option> options = contributed();
        assertTrue(options.toString(), options.contains(new Option("Compact rows", "etalii.adp.settings.etalii.adp.configured", "Configured Tool")));
        assertFalse(options.toString(), options.contains(new Option("Compact rows", "etalii.adp.settings", "ADP")));
    }
}
