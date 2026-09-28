package etalii.adp.it;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.intellij.driver.client.Driver;
import com.intellij.driver.client.Remote;
import com.intellij.driver.model.LockSemantics;
import com.intellij.driver.model.OnDispatcher;
import com.intellij.driver.model.RdTarget;
import com.intellij.driver.sdk.EditorsKt;
import com.intellij.driver.sdk.Project;
import com.intellij.driver.sdk.ProjectManagerKt;
import com.intellij.driver.sdk.VirtualFile;
import com.intellij.ide.starter.driver.engine.BackgroundRun;
import com.intellij.ide.starter.driver.engine.RunWithDriverKt;
import com.intellij.ide.starter.ide.IDETestContext;
import com.intellij.ide.starter.ide.installer.ExistingIdeInstaller;
import com.intellij.ide.starter.models.IdeInfo;
import com.intellij.ide.starter.models.TestCase;
import com.intellij.ide.starter.project.LocalProjectInfo;
import com.intellij.ide.starter.runner.Starter;
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimateProductInitKt;
import com.intellij.tools.ide.starter.product.pycharm.PyCharmProductInitKt;
import com.intellij.tools.ide.starter.product.rider.RiderProductInitKt;
import com.intellij.tools.ide.starter.product.webstorm.WebStormProductInitKt;

import kotlin.Unit;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.time.DurationKt;
import kotlin.time.DurationUnit;

/**
 * The proving test for installation (FR-001, SC-001 automated part, research R9 and R12): the built
 * plug-in zip is installed into real IntelliJ IDEA, Rider, WebStorm and PyCharm 2026.2 instances,
 * and in a project with one example map and one Objective-C++ {@code .mm} file, the map opens in the
 * FreeMind designer and the other file does not.
 * <p>
 * IntelliJ IDEA runs twice: as installed, and with an Ultimate licence when the environment names
 * one in {@code ADP_IDEA_LICENSE} (a licence key, or a path to one); without it that run is skipped.
 * A product found installed at {@code ADP_IDE_HOME_<CODE>} (for example {@code ADP_IDE_HOME_IU}) is
 * used as it is instead of being downloaded, and {@code ADP_IT_PRODUCTS} (a comma-separated list of
 * the names below) limits a run to some products.
 * <p>
 * The Starter framework is written in Kotlin, and a few of its entry points take Kotlin durations,
 * which Java cannot name; those are called reflectively.
 */
class OpenMapIntegrationTest {

    private static final String DESIGNER = "etalii.adp.freemind.editor";
    private static final String DESIGNER_NAME = "FreeMind Mind Map";
    private static final String MAP = "freemind-1.0.1-rich-notes.mm";
    private static final String OBJECTIVE_CPP = """
            #import <Foundation/Foundation.h>
            #include <vector>

            int main(int argc, const char *argv[]) {
                std::vector<int> map;
                return 0;
            }
            """;

    private final Path repository = Path.of(System.getProperty("adp.repository", "."));

    /** Each product at its latest 2026.2 release; Rider numbers its releases on its own. */
    static Stream<Arguments> products() {
        return Stream.of(
                Arguments.of("IntelliJ IDEA", (Supplier<IdeInfo>) IdeaUltimateProductInitKt::getDefaultIdeaUltimate, "2026.2.3", false),
                Arguments.of("IntelliJ IDEA with an Ultimate licence", (Supplier<IdeInfo>) IdeaUltimateProductInitKt::getDefaultIdeaUltimate, "2026.2.3", true),
                Arguments.of("Rider", (Supplier<IdeInfo>) RiderProductInitKt::getDefaultRider, "2026.2.2", false),
                Arguments.of("WebStorm", (Supplier<IdeInfo>) WebStormProductInitKt::getDefaultWebStorm, "2026.2.3", false),
                Arguments.of("PyCharm", (Supplier<IdeInfo>) PyCharmProductInitKt::getDefaultPyCharm, "2026.2.3", false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("products")
    void aMapOpensInTheDesignerAndAnotherMmFileDoesNot(String product, Supplier<IdeInfo> ide, String version, boolean licensed, @TempDir Path project)
            throws Exception {
        String selected = System.getenv("ADP_IT_PRODUCTS");
        assumeTrue(selected == null || selected.isBlank() || Arrays.stream(selected.split(",")).map(String::strip).anyMatch(product::equals),
                product + " is not in ADP_IT_PRODUCTS");
        String licence = System.getenv("ADP_IDEA_LICENSE");
        if (licensed) {
            assumeTrue(licence != null && !licence.isBlank(), "No Ultimate licence in ADP_IDEA_LICENSE, so the licensed run is skipped");
        }
        Path zip = Path.of(System.getProperty("adp.plugin.zip"));
        assertTrue(Files.isRegularFile(zip), "No plug-in zip at " + zip);
        Files.copy(repository.resolve("freemind/testdata/examples").resolve(MAP), project.resolve("map.mm"));
        Files.writeString(project.resolve("main.mm"), OBJECTIVE_CPP, UTF_8);

        IdeInfo info = installedOrDownloaded(ide.get());
        TestCase<LocalProjectInfo> testCase = new TestCase<>(info, localProject(project), List.of(), false, null);
        if (System.getenv("ADP_IDE_HOME_" + info.getProductCode()) == null) {
            // a version replaces the installer, so it is set only for a download
            testCase = testCase.withVersion(version);
        }
        IDETestContext context = Starter.INSTANCE.newContext("OpenMap-" + product.replace(' ', '-'), testCase, false);
        context.getPluginConfigurator().installPluginFromPath(zip);
        if (licensed) {
            Path file = Path.of(licence);
            if (Files.isRegularFile(file)) {
                context.setLicense(file);
            } else {
                context.setLicense(licence);
            }
        }

        BackgroundRun run = runIdeWithDriver(context);
        try {
            Driver driver = run.getDriver();
            Project opened = waitForProject(driver);

            // remote references stay valid only inside a context
            driver.withContext(OnDispatcher.EDT, LockSemantics.NO_LOCK, d -> {
                EditorsKt.openFile(d, "map.mm", opened, false, false);
                VirtualFile map = EditorsKt.findOpenFile(d, "map.mm", opened, false);
                assertNotNull(map, product + ": the map is open");
                assertTrue(editorTypes(d, opened, map).contains(DESIGNER), product + ": the designer is offered for the map");
                assertEquals(DESIGNER_NAME, selectedEditorName(d, opened, map), product + ": the map opens in the designer");

                EditorsKt.openFile(d, "main.mm", opened, false, false);
                VirtualFile source = EditorsKt.findOpenFile(d, "main.mm", opened, false);
                assertNotNull(source, product + ": the Objective-C++ file is open");
                assertFalse(editorTypes(d, opened, source).contains(DESIGNER), product + ": the designer is not offered for other .mm files");
                assertNotEquals(DESIGNER_NAME, selectedEditorName(d, opened, source), product + ": the other file opens as without the plug-in");
                return Unit.INSTANCE;
            });
        } finally {
            closeIdeAndWait(run);
        }
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

    /** The product as installed at {@code ADP_IDE_HOME_<code>}, when there is one, else as the Starter downloads it. */
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

    // The FileEditorManager and FileEditorProviderManager of the IDE under test, as the Driver sees them.

    @Remote("com.intellij.openapi.fileEditor.FileEditorManager")
    public interface FileEditorManagerRemote {
        FileEditorRemote getSelectedEditor(VirtualFile file);
    }

    @Remote("com.intellij.openapi.fileEditor.FileEditor")
    public interface FileEditorRemote {
        String getName();
    }

    @Remote("com.intellij.openapi.fileEditor.ex.FileEditorProviderManager")
    public interface FileEditorProviderManagerRemote {
        FileEditorProviderRemote[] getProviders(Project project, VirtualFile file);
    }

    @Remote("com.intellij.openapi.fileEditor.FileEditorProvider")
    public interface FileEditorProviderRemote {
        String getEditorTypeId();
    }

    private static String selectedEditorName(Driver driver, Project project, VirtualFile file) {
        FileEditorManagerRemote editors = driver.service(JvmClassMappingKt.getKotlinClass(FileEditorManagerRemote.class), project, RdTarget.DEFAULT);
        FileEditorRemote editor = editors.getSelectedEditor(file);
        return editor == null ? null : editor.getName();
    }

    private static List<String> editorTypes(Driver driver, Project project, VirtualFile file) {
        FileEditorProviderManagerRemote providers = driver.service(JvmClassMappingKt.getKotlinClass(FileEditorProviderManagerRemote.class),
                RdTarget.DEFAULT);
        List<String> types = new ArrayList<>();
        for (FileEditorProviderRemote provider : providers.getProviders(project, file)) {
            types.add(provider.getEditorTypeId());
        }
        return types;
    }

    // Starter entry points with Kotlin duration parameters, which Java cannot call by name.

    private static LocalProjectInfo localProject(Path directory) throws ReflectiveOperationException {
        for (Constructor<?> constructor : LocalProjectInfo.class.getDeclaredConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length == 7 && types[5] == int.class && types[6] == DefaultConstructorMarker.class) {
                // every parameter after the directory takes its default
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
        // the mask marks every value parameter as defaulted (the context is the receiver, which has no bit); the marker is unused
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
