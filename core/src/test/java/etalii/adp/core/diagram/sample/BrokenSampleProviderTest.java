package etalii.adp.core.diagram.sample;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import etalii.adp.core.AdpEditorProvider;
import etalii.adp.core.settings.ToolInfo;
import etalii.adp.core.settings.FreshSettings;
import etalii.adp.testing.ToolDriver;

/**
 * T008 (FR-008, research R5): a provider built from an inconsistent definition does not throw; it
 * keeps every problem the definition check reports, reads as not loaded and refuses every file.
 */
@RunWith(JUnit4.class)
public class BrokenSampleProviderTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        FreshSettings.install(getTestRootDisposable());
    }

    @Test
    public void theProviderIsCreatedAndKeepsEveryProblem() {
        BrokenSampleProvider provider = new BrokenSampleProvider();
        assertEquals(List.of("toolbox: names undeclared type 'nothing'"), provider.problems());
        assertNull(provider.definition());
    }

    @Test
    public void itIsListedAsNotLoadedWithItsProblems() {
        ToolInfo info = BrokenSampleProvider.register(getTestRootDisposable()).toolInfo();
        assertEquals(ToolInfo.Status.NOT_LOADED, info.status());
        assertEquals(List.of("toolbox: names undeclared type 'nothing'"), info.problems());
        assertEquals("Broken Sample Diagram", info.name());
    }

    @Test
    public void itRefusesEveryFile() {
        BrokenSampleProvider provider = BrokenSampleProvider.register(getTestRootDisposable());
        VirtualFile file = ToolDriver.createFile(myFixture, "any.adpbroken", "<sample/>".getBytes(UTF_8));
        assertFalse(provider.accept(getProject(), file));
        assertFalse(AdpEditorProvider.acceptedByAny(file));
    }
}
