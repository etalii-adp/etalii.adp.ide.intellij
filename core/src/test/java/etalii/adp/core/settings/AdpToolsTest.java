package etalii.adp.core.settings;

import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import etalii.adp.core.AdpEditorProvider;
import etalii.adp.core.diagram.view.DiagramEditorProvider;
import etalii.adp.core.diagram.sample.SampleDefinition;
import etalii.adp.core.diagram.sample.SampleMapping;
import etalii.adp.core.diagram.sample.SampleProvider;

/**
 * T007 (FR-007, FR-017): the registry lists every registered ADP provider and nothing else, and
 * each tool's name, sorted file types, version, origin, conflicts and unfollowed options.
 */
@RunWith(JUnit4.class)
public class AdpToolsTest extends BasePlatformTestCase {

    /** A second tool for sample files, and for {@code .adprival}, fixing the grid off. */
    static final class Rival extends DiagramEditorProvider {

        static final String ID = "etalii.adp.rival";

        Rival() {
            super(SampleDefinition.builder().view(v -> v.fix(CanvasOption.SHOW_GRID, false)), SampleMapping::new);
        }

        @Override
        protected Set<String> extensions() {
            return Set.of("adprival", "adpsample");
        }

        @Override
        protected boolean sniff(byte[] head) {
            return true;
        }

        @Override
        protected String toolName() {
            return "Rival Tool";
        }

        @Override
        public String getEditorTypeId() {
            return ID;
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        FreshSettings.install(getTestRootDisposable());
    }

    @Test
    public void listsEveryRegisteredAdpProviderAndNothingElse() {
        SampleProvider.register(getTestRootDisposable());
        FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new Rival(), getTestRootDisposable());

        List<String> ids = AdpTools.providers().stream().map(AdpEditorProvider::getEditorTypeId).toList();
        assertEquals(List.of(SampleProvider.EDITOR_TYPE_ID, Rival.ID), ids);
        assertEquals(ids, AdpTools.all().stream().map(ToolInfo::id).toList());
    }

    @Test
    public void eachToolIsDescribed() {
        SampleProvider sample = SampleProvider.register(getTestRootDisposable());
        ToolInfo info = sample.toolInfo();
        assertEquals(SampleProvider.EDITOR_TYPE_ID, info.id());
        assertEquals("Sample Diagram", info.name());
        assertEquals(List.of("adpsample"), info.fileTypes());
        assertEquals("registered by the test, not by a plug-in descriptor", "", info.version());
        assertEquals(new ToolOrigin.Module(AdpEditorProvider.ADP_PLUGIN_ID), info.origin());
        assertEquals("Built into ADP", info.origin().describe());
        assertEquals(ToolInfo.Status.LOADED, info.status());
        assertEquals(List.of(), info.problems());
        assertTrue(info.on());
        assertEquals(List.of(), info.conflictsWith());
        assertEquals(Set.of(), info.unfollowed());
    }

    @Test
    public void theVersionAndOriginAreThoseOfThePluginThatRegisteredTheTool() {
        PluginDescriptor platform = PluginManagerCore.getPlugin(PluginId.getId(PluginManagerCore.CORE_PLUGIN_ID));
        Rival rival = new Rival();
        rival.setPluginDescriptor(platform);
        ToolInfo info = rival.toolInfo();
        assertEquals(platform.getVersion(), info.version());
        assertEquals(new ToolOrigin.OtherPlugin(PluginManagerCore.CORE_PLUGIN_ID, platform.getName()), info.origin());
        assertEquals("From plug-in " + platform.getName(), info.origin().describe());
    }

    @Test
    public void fileTypesAreSorted() {
        Rival rival = new Rival();
        assertEquals(List.of("adprival", "adpsample"), rival.toolInfo().fileTypes());
    }

    @Test
    public void overlappingFileTypesAreAConflictOnBothTools() {
        SampleProvider sample = SampleProvider.register(getTestRootDisposable());
        Rival rival = new Rival();
        FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(rival, getTestRootDisposable());

        assertEquals(List.of(Rival.ID), sample.toolInfo().conflictsWith());
        assertEquals(List.of(SampleProvider.EDITOR_TYPE_ID), rival.toolInfo().conflictsWith());
    }

    @Test
    public void theFixedCanvasOptionsAreReported() {
        assertEquals(Set.of(CanvasOption.SHOW_GRID), new Rival().toolInfo().unfollowed());
    }

    @Test
    public void aToolThatIsOffIsReportedOff() {
        SampleProvider sample = SampleProvider.register(getTestRootDisposable());
        AdpSettings.getInstance().setOff(SampleProvider.EDITOR_TYPE_ID, true);
        assertFalse(sample.toolInfo().on());
    }
}
