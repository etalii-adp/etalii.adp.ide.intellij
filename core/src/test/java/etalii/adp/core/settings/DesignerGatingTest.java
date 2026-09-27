package etalii.adp.core.settings;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import etalii.adp.core.AdpEditorProvider;
import etalii.adp.core.diagram.sample.BrokenSampleProvider;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.testing.DesignerDriver;

/**
 * T009 (FR-009, research R7): a designer that is off, or has problems, accepts no file and
 * {@link AdpEditorProvider#acceptedByAny} agrees; turned on again it accepts as before.
 */
@RunWith(JUnit4.class)
public class DesignerGatingTest extends BasePlatformTestCase {

    private AdpSettings settings;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        settings = FreshSettings.install(getTestRootDisposable());
    }

    private VirtualFile sampleFile() {
        return DesignerDriver.createFile(myFixture, "gate.adpsample", "<sample/>".getBytes(UTF_8));
    }

    @Test
    public void aDesignerThatIsOffAcceptsNoFileAndOnAgainAcceptsAsBefore() {
        SampleProvider provider = SampleProvider.register(getTestRootDisposable());
        VirtualFile file = sampleFile();
        assertTrue(provider.accept(getProject(), file));

        settings.setOff(SampleProvider.EDITOR_TYPE_ID, true);
        assertFalse(provider.accept(getProject(), file));
        assertFalse(provider.accepts(file));
        assertFalse(AdpEditorProvider.acceptedByAny(file));

        settings.setOff(SampleProvider.EDITOR_TYPE_ID, false);
        assertTrue(provider.accept(getProject(), file));
        assertTrue(AdpEditorProvider.acceptedByAny(file));
    }

    @Test
    public void turningOneDesignerOffLeavesTheOthers() {
        SampleProvider provider = SampleProvider.register(getTestRootDisposable());
        settings.setOff("etalii.adp.other", true);
        assertTrue(provider.accepts(sampleFile()));
    }

    @Test
    public void aDesignerWithProblemsAcceptsNoFile() {
        BrokenSampleProvider broken = BrokenSampleProvider.register(getTestRootDisposable());
        VirtualFile file = DesignerDriver.createFile(myFixture, "gate.adpbroken", "<sample/>".getBytes(UTF_8));
        assertFalse(broken.accepts(file));
        assertFalse(AdpEditorProvider.acceptedByAny(file));
    }
}
