package etalii.adp.it;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.intellij.ide.starter.path.GlobalPaths;

/**
 * The IDEs the real-IDE tests download, and the folders the tests run in, live in the per-user
 * cache named by {@code adp.ideTests.home}, never in the repository (spec 006 FR-009). A sandbox
 * that opens the repository would otherwise index them.
 */
class IdeTestsHomeTest {

    private final Path repository = Path.of(System.getProperty("adp.repository", ".")).toAbsolutePath().normalize();
    private final Path home = Path.of(System.getProperty("adp.ideTests.home", "")).toAbsolutePath().normalize();

    @Test
    void everyStarterFolderIsInTheCacheAndNoneInTheRepository() {
        GlobalPaths paths = GlobalPaths.Companion.getInstance();
        for (Path folder : List.of(paths.getTestHomePath(), paths.getInstallersDirectory(), paths.getTestsDirectory(),
                paths.getLocalCacheDirectory())) {
            Path resolved = folder.toAbsolutePath().normalize();
            assertTrue(resolved.startsWith(home), resolved + " is below " + home);
            assertFalse(resolved.startsWith(repository), resolved + " is outside the repository " + repository);
        }
    }
}
