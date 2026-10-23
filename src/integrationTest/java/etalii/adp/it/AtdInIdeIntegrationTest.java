package etalii.adp.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.intellij.driver.client.Driver;
import com.intellij.driver.client.Remote;
import com.intellij.driver.model.LockSemantics;
import com.intellij.driver.model.OnDispatcher;
import com.intellij.driver.sdk.EditorsKt;
import com.intellij.driver.sdk.Project;
import com.intellij.driver.sdk.VirtualFile;
import com.intellij.ide.starter.driver.engine.BackgroundRun;
import com.intellij.ide.starter.ide.IDETestContext;
import com.intellij.ide.starter.models.IdeInfo;
import com.intellij.ide.starter.models.TestCase;
import com.intellij.ide.starter.project.LocalProjectInfo;
import com.intellij.ide.starter.runner.Starter;
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimateProductInitKt;

import kotlin.Unit;

/**
 * An {@code .atd} tool description in a real IntelliJ IDEA, which has the platform's language server support
 * (etalii.adp spec 035, T116): it opens as an ATD file, and opening it starts the {@code atd-lsp} the plug-in packs for
 * this platform. What the server answers is the walk's, through the same binary, in
 * {@code etalii.adp.atd.AtdLanguageIntegrationTest}. The description is the mind map example of the walk
 * ({@code ATD_RELEASE_DIR} or {@code -Dadp.atd.walk}); without it the test is skipped. {@code ADP_IT_PRODUCTS} without
 * "IntelliJ IDEA" skips it too.
 */
class AtdInIdeIntegrationTest {

    @Remote("com.intellij.openapi.vfs.VirtualFile")
    public interface FileRemote {
        FileTypeRemote getFileType();
    }

    @Remote("com.intellij.openapi.fileTypes.FileType")
    public interface FileTypeRemote {
        String getName();
    }

    private static Path walkFolder() {
        String folder = System.getProperty("adp.atd.walk");
        if (folder == null || folder.isBlank()) {
            String release = System.getenv("ATD_RELEASE_DIR");
            folder = release == null ? null : Path.of(release).resolve("atd-walk").toString();
        }
        return folder == null ? null : Path.of(folder);
    }

    private static boolean serverRuns() {
        return ProcessHandle.allProcesses()
                .anyMatch(p -> p.info().command().map(c -> c.endsWith("/atd-lsp") || c.endsWith("\\atd-lsp.exe")).orElse(false));
    }

    @Test
    void anAtdOpensAsAtdAndStartsTheServer(@TempDir Path project) throws Exception {
        String selected = System.getenv("ADP_IT_PRODUCTS");
        assumeTrue(selected == null || selected.isBlank() || Arrays.stream(selected.split(",")).map(String::strip).anyMatch("IntelliJ IDEA"::equals),
                "IntelliJ IDEA is not in ADP_IT_PRODUCTS");
        Path walk = walkFolder();
        assumeTrue(walk != null && Files.isRegularFile(walk.resolve("mindmap.atd")), "No walk: set adp.atd.walk or ATD_RELEASE_DIR");
        assertTrue(!serverRuns(), "no atd-lsp runs before the IDE opens one");
        Files.copy(walk.resolve("mindmap.atd"), project.resolve("mindmap.atd"));
        Files.copy(walk.resolve("mindmap.notation.atd"), project.resolve("mindmap.notation.atd"));
        Path zip = Path.of(System.getProperty("adp.plugin.zip"));

        IdeInfo info = OpenMapIntegrationTest.installedOrDownloaded(IdeaUltimateProductInitKt.getDefaultIdeaUltimate());
        TestCase<LocalProjectInfo> testCase = new TestCase<>(info, OpenMapIntegrationTest.localProject(project), List.of(), false, null);
        if (System.getenv("ADP_IDE_HOME_" + info.getProductCode()) == null) {
            testCase = testCase.withVersion("2026.2.3");
        }
        IDETestContext context = Starter.INSTANCE.newContext("Atd-IntelliJ-IDEA", testCase, false);
        context.getPluginConfigurator().installPluginFromPath(zip);
        BackgroundRun run = OpenMapIntegrationTest.runIdeWithDriver(context);
        Throwable failure = null;
        try {
            Driver driver = run.getDriver();
            Project opened = OpenMapIntegrationTest.waitForProject(driver);
            String[] type = new String[1];
            driver.withContext(OnDispatcher.EDT, LockSemantics.NO_LOCK, d -> {
                EditorsKt.openFile(d, "mindmap.atd", opened, false, false);
                VirtualFile file = EditorsKt.findOpenFile(d, "mindmap.atd", opened, false);
                assertNotNull(file, "the description is open");
                type[0] = d.cast(file, kotlin.jvm.JvmClassMappingKt.getKotlinClass(FileRemote.class)).getFileType().getName();
                return Unit.INSTANCE;
            });
            assertEquals("ATD", type[0], "an .atd opens as an ATD file");
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
            while (!serverRuns() && System.nanoTime() < deadline) {
                Thread.sleep(500);
            }
            assertTrue(serverRuns(), "opening the description starts the plug-in's atd-lsp");
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            OpenMapIntegrationTest.closeIdeAndWait(run, failure);
        }
    }
}
