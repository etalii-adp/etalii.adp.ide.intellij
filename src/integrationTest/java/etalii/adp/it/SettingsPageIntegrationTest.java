package etalii.adp.it;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.intellij.driver.client.Driver;
import com.intellij.driver.client.Remote;
import com.intellij.driver.model.LockSemantics;
import com.intellij.driver.model.OnDispatcher;
import com.intellij.driver.model.RdTarget;
import com.intellij.driver.sdk.Project;
import com.intellij.driver.sdk.ProjectManagerKt;
import com.intellij.ide.starter.driver.engine.BackgroundRun;
import com.intellij.ide.starter.driver.engine.RunWithDriverKt;
import com.intellij.ide.starter.ide.IDETestContext;
import com.intellij.ide.starter.ide.installer.ExistingIdeInstaller;
import com.intellij.ide.starter.models.IdeInfo;
import com.intellij.ide.starter.models.TestCase;
import com.intellij.ide.starter.project.LocalProjectInfo;
import com.intellij.ide.starter.runner.Starter;
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimateProductInitKt;

import kotlin.Unit;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.time.DurationKt;
import kotlin.time.DurationUnit;

/**
 * Spec 004 T051 (SC-001, FR-001), in a real IntelliJ IDEA 2026.2 with the built plug-in zip
 * installed: the IDE's own settings search, as the Settings dialog runs it, finds the ADP page by
 * "ADP" and by the FreeMind designer's name. {@code ADP_IT_PRODUCTS} and
 * {@code ADP_IDE_HOME_<CODE>} work as in {@link OpenMapIntegrationTest}.
 */
class SettingsPageIntegrationTest {

    private static final String PRODUCT = "IntelliJ IDEA";
    private static final String PAGE = "etalii.adp.settings";

    @Test
    void theSettingsSearchFindsTheAdpPageByItsNameAndByADesignersName(@TempDir Path project) throws Exception {
        String selected = System.getenv("ADP_IT_PRODUCTS");
        assumeTrue(selected == null || selected.isBlank() || Arrays.stream(selected.split(",")).map(String::strip).anyMatch(PRODUCT::equals),
                PRODUCT + " is not in ADP_IT_PRODUCTS");
        Path zip = Path.of(System.getProperty("adp.plugin.zip"));
        assertTrue(Files.isRegularFile(zip), "No plug-in zip at " + zip);

        IdeInfo info = installedOrDownloaded(IdeaUltimateProductInitKt.getDefaultIdeaUltimate());
        TestCase<LocalProjectInfo> testCase = new TestCase<>(info, localProject(project), List.of(), false, null);
        if (System.getenv("ADP_IDE_HOME_" + info.getProductCode()) == null) {
            testCase = testCase.withVersion("2026.2.3");
        }
        IDETestContext context = Starter.INSTANCE.newContext("SettingsPage", testCase, false);
        context.getPluginConfigurator().installPluginFromPath(zip);

        BackgroundRun run = runIdeWithDriver(context);
        try {
            Driver driver = run.getDriver();
            Project opened = waitForProject(driver);
            buildSearchIndex(driver);

            for (String search : List.of("ADP", "FreeMind Mind Map")) {
                List<String> hits = pagesFound(driver, opened, search);
                assertTrue(hits.contains(PAGE), "searching \"" + search + "\" finds the ADP page, found " + hits);
            }
        } finally {
            closeIdeAndWait(run);
        }
    }

    /**
     * The index is built when the Settings dialog first opens; build it now. Building reads under a
     * cancellable read action, which the IDE's own start-up writes cancel, so it is tried again
     * until it holds.
     */
    private static void buildSearchIndex(Driver driver) throws InterruptedException {
        SearchableOptionsRegistrarRemote registrar = driver.service(JvmClassMappingKt.getKotlinClass(SearchableOptionsRegistrarRemote.class),
                RdTarget.DEFAULT);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(2);
        RuntimeException last = null;
        while (System.nanoTime() < deadline) {
            try {
                registrar.initializeBlocking();
                if (registrar.isInitialized()) {
                    return;
                }
            } catch (RuntimeException e) {
                last = e;
            }
            Thread.sleep(2_000);
        }
        throw new AssertionError("The settings search index was not built within 2 minutes", last);
    }

    /** The ids of the pages the Settings dialog's search finds for {@code search}, over every settings group. */
    private static List<String> pagesFound(Driver driver, Project project, String search) {
        List<String> ids = new ArrayList<>();
        driver.withContext(OnDispatcher.EDT, LockSemantics.NO_LOCK, d -> {
            ConfigurableGroupRemote[] groups = d.utility(JvmClassMappingKt.getKotlinClass(ShowSettingsUtilImplRemote.class), RdTarget.DEFAULT)
                    .getConfigurableGroups(project, true);
            ConfigurableHitRemote hit = d.service(JvmClassMappingKt.getKotlinClass(SearchableOptionsRegistrarRemote.class), RdTarget.DEFAULT)
                    .getConfigurables(Arrays.asList(groups), null, null, search, project);
            for (SearchableConfigurableRemote page : hit.getAll()) {
                ids.add(page.getId());
            }
            return Unit.INSTANCE;
        });
        return ids;
    }

    /** The project the IDE opens on start, once it is open. */
    private static Project waitForProject(Driver driver) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
        while (System.nanoTime() < deadline) {
            List<Project> projects = ProjectManagerKt.getOpenProjects(driver, RdTarget.DEFAULT);
            if (!projects.isEmpty() && projects.get(0).isInitialized()) {
                return projects.get(0);
            }
            Thread.sleep(1_000);
        }
        throw new AssertionError("The IDE opened no project within 5 minutes");
    }

    private static IdeInfo installedOrDownloaded(IdeInfo info) {
        String home = System.getenv("ADP_IDE_HOME_" + info.getProductCode());
        if (home == null || home.isBlank()) {
            return info;
        }
        Path installed = Path.of(home);
        return info.copy(info.getProductCode(), info.getPlatformPrefix(), info.getExecutableFileName(), info.getBaseIdePlatformPrefixForFrontend(),
                info.getBuildType(), info.getAdditionalModules(), info.getBuildNumber(), info.getVersion(), info.getTag(), info.getDownloadURI(),
                info.getQodanaProductCode(), info.getFullName(), ignored -> new ExistingIdeInstaller(installed));
    }

    // The IDE's settings search, as the Driver sees it.

    /** The platform's implementation, which alone can build the index on demand. */
    @Remote(value = "com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl", serviceInterface = "com.intellij.ide.ui.search.SearchableOptionsRegistrar")
    public interface SearchableOptionsRegistrarRemote {
        void initializeBlocking();

        boolean isInitialized();

        ConfigurableHitRemote getConfigurables(List<ConfigurableGroupRemote> groups, Object type, Set<Object> configurables, String option,
                Project project);
    }

    @Remote("com.intellij.ide.actions.ShowSettingsUtilImpl")
    public interface ShowSettingsUtilImplRemote {
        ConfigurableGroupRemote[] getConfigurableGroups(Project project, boolean withIdeSettings);
    }

    @Remote("com.intellij.openapi.options.ConfigurableGroup")
    public interface ConfigurableGroupRemote {
    }

    @Remote("com.intellij.ide.ui.search.ConfigurableHit")
    public interface ConfigurableHitRemote {
        // a Set in the IDE; the Driver hands collections back as lists
        List<SearchableConfigurableRemote> getAll();
    }

    @Remote("com.intellij.openapi.options.SearchableConfigurable")
    public interface SearchableConfigurableRemote {
        String getId();
    }

    // Starter entry points with Kotlin duration parameters, which Java cannot call by name.

    private static LocalProjectInfo localProject(Path directory) throws ReflectiveOperationException {
        for (Constructor<?> constructor : LocalProjectInfo.class.getDeclaredConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length == 7 && types[5] == int.class && types[6] == DefaultConstructorMarker.class) {
                return (LocalProjectInfo) constructor.newInstance(directory, false, 0L, null, null, 0b11110, null);
            }
        }
        throw new NoSuchMethodException("LocalProjectInfo's constructor with defaults");
    }

    private static BackgroundRun runIdeWithDriver(IDETestContext context) throws ReflectiveOperationException {
        Method method = Arrays.stream(RunWithDriverKt.class.getMethods())
                .filter(m -> m.getName().startsWith("runIdeWithDriver") && m.getName().endsWith("$default")).findFirst()
                .orElseThrow(() -> new NoSuchMethodException("runIdeWithDriver"));
        Object[] arguments = new Object[method.getParameterCount()];
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = defaultValue(types[i]);
        }
        arguments[0] = context;
        arguments[arguments.length - 2] = (1 << (arguments.length - 3)) - 1;
        return (BackgroundRun) method.invoke(null, arguments);
    }

    private static void closeIdeAndWait(BackgroundRun run) throws ReflectiveOperationException {
        Method close = Arrays.stream(BackgroundRun.class.getMethods())
                .filter(m -> m.getName().startsWith("closeIdeAndWait") && !m.getName().endsWith("$default") && m.getParameterCount() == 2)
                .findFirst().orElseThrow(() -> new NoSuchMethodException("closeIdeAndWait"));
        close.invoke(run, DurationKt.toDuration(2, DurationUnit.MINUTES), false);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == boolean.class) {
            return false;
        }
        return null;
    }
}
