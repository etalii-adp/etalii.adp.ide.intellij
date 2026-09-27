package etalii.adp.core.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import etalii.adp.core.diagram.sample.SampleDefinition;
import etalii.adp.core.diagram.sample.SampleMapping;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.diagram.sample.SettingSampleProvider;
import etalii.adp.core.diagram.view.DiagramEditorProvider;

/**
 * T050 (SC-006): with 20 designers installed, the ADP page adds no noticeable delay to opening the
 * Settings dialog. Creating the page, filling it and building its child pages takes under 100 ms,
 * the median of five runs after a warm-up.
 */
@RunWith(JUnit4.class)
public class SettingsPagePerformanceTest extends BasePlatformTestCase {

    private static final long BUDGET_MS = 100;

    /** As in the diagram performance tests: shared CI machines are slower, so the assertion allows three times the budget. */
    private static final int CI_HEADROOM = 3;

    /** A test-only designer that only fills the list. */
    static final class Filler extends DiagramEditorProvider {

        private final int number;

        Filler(int number) {
            super(SampleDefinition.builder(), SampleMapping::new);
            this.number = number;
        }

        @Override
        protected Set<String> extensions() {
            return Set.of("adpfiller" + number);
        }

        @Override
        protected boolean sniff(byte[] head) {
            return true;
        }

        @Override
        protected String editorName() {
            return "Filler Designer " + number;
        }

        @Override
        public String getEditorTypeId() {
            return "etalii.adp.filler." + number;
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        FreshSettings.install(getTestRootDisposable());
        SampleProvider.register(getTestRootDisposable());
        SettingSampleProvider.register(getTestRootDisposable());
        for (int i = 0; i < 18; i++) {
            FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new Filler(i), getTestRootDisposable());
        }
    }

    private static void openAndClose() {
        AdpConfigurable page = new AdpConfigurable();
        page.createComponent();
        page.reset();
        assertEquals(1, page.getConfigurables().length);
        page.disposeUIResources();
    }

    @Test
    public void thePageOpensWithinBudgetWithTwentyDesigners() {
        assertEquals(20, AdpDesigners.providers().size());
        openAndClose();

        List<Long> times = new ArrayList<>();
        for (int run = 0; run < 5; run++) {
            long start = System.nanoTime();
            openAndClose();
            times.add((System.nanoTime() - start) / 1_000_000);
        }
        long median = times.stream().sorted().toList().get(2);
        System.out.printf("SC-006 ADP page with 20 designers %s: median %d ms (budget %d ms, CI limit %d ms)%n", times, median, BUDGET_MS,
                BUDGET_MS * CI_HEADROOM);
        assertTrue("the page took " + median + " ms", median <= BUDGET_MS * CI_HEADROOM);
    }
}
