package etalii.adp.it;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
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
import com.intellij.driver.sdk.ActionManagerKt;
import com.intellij.driver.sdk.Document;
import com.intellij.driver.sdk.EditorsKt;
import com.intellij.driver.sdk.Project;
import com.intellij.driver.sdk.ProjectManagerKt;
import com.intellij.driver.sdk.VirtualFile;
import com.intellij.driver.sdk.ui.remote.Component;
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
 * US2 in real IDEs (SC-003 automated part, research R9): with the built plug-in zip installed in
 * IntelliJ IDEA, Rider, WebStorm and PyCharm 2026.2, the root of an example map is selected in the
 * designer, Add Child Node runs through the action system as its shortcut would, and the IDE's own
 * Undo then returns the file to its original bytes.
 * <p>
 * The products, {@code ADP_IT_PRODUCTS}, {@code ADP_IDE_HOME_<CODE>} and {@code ADP_IDEA_LICENSE}
 * work as in {@link OpenMapIntegrationTest}.
 */
class EditUndoIntegrationTest {

    private static final String PLUGIN = "etalii.adp";
    private static final String MAP = "freemind-1.0.1-rich-notes.mm";

    private final Path repository = Path.of(System.getProperty("adp.repository", "."));

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
    void addChildNodeThenUndoRestoresTheOriginalBytes(String product, Supplier<IdeInfo> ide, String version, boolean licensed, @TempDir Path project)
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
        byte[] original = Files.readAllBytes(repository.resolve("freemind/testdata/examples").resolve(MAP));
        Files.write(project.resolve("map.mm"), original);

        IdeInfo info = installedOrDownloaded(ide.get());
        TestCase<LocalProjectInfo> testCase = new TestCase<>(info, localProject(project), List.of(), false, null);
        if (System.getenv("ADP_IDE_HOME_" + info.getProductCode()) == null) {
            testCase = testCase.withVersion(version);
        }
        IDETestContext context = Starter.INSTANCE.newContext("EditUndo-" + product.replace(' ', '-'), testCase, false);
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
        Throwable failure = null;
        try {
            Driver driver = run.getDriver();
            Project opened = waitForProject(driver);
            String[] path = new String[1];
            String[] texts = new String[3];

            driver.withContext(OnDispatcher.EDT, LockSemantics.NO_LOCK, d -> {
                EditorsKt.openFile(d, "map.mm", opened, false, false);
                VirtualFile map = EditorsKt.findOpenFile(d, "map.mm", opened, false);
                assertNotNull(map, product + ": the map is open");
                path[0] = map.getPath();
                CompositeRemote composite = d.service(JvmClassMappingKt.getKotlinClass(FileEditorManagerRemote.class), opened, RdTarget.DEFAULT)
                        .getSelectedEditor(map);
                assertNotNull(composite, product + ": the map opens in an editor");
                DesignerRemote designer = composite.designer();
                assertTrue(designer.isEditable(), product + ": the map can be edited");
                designer.reveal(designer.model().root().key());
                texts[0] = document(d, map).getText();
                return Unit.INSTANCE;
            });

            invoke(driver, opened, path[0], "etalii.adp.freemind.AddChild");
            texts[1] = text(driver, opened);
            assertNotEquals(texts[0], texts[1], product + ": Add Child Node changed the map");
            assertTrue(texts[1].contains("TEXT=\"New Node\""), product + ": the new node is in the text");

            invoke(driver, opened, path[0], "$Undo");
            texts[2] = text(driver, opened);
            assertEquals(texts[0], texts[2], product + ": Undo returns the text");

            driver.withContext(OnDispatcher.EDT, LockSemantics.WRITE_ACTION, d -> {
                d.service(JvmClassMappingKt.getKotlinClass(FileDocumentManagerRemote.class), RdTarget.DEFAULT).saveAllDocuments();
                return Unit.INSTANCE;
            });
            assertArrayEquals(original, Files.readAllBytes(Path.of(path[0])), product + ": the saved file has its original bytes");
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            closeIdeAndWait(run, failure);
        }
    }

    /** Runs an action through the action system with the designer's canvas as its context, as its shortcut would. */
    private static void invoke(Driver driver, Project project, String path, String actionId) {
        driver.withContext(OnDispatcher.EDT, LockSemantics.NO_LOCK, d -> {
            VirtualFile map = EditorsKt.findOpenFile(d, "map.mm", project, false);
            DesignerRemote designer = d.service(JvmClassMappingKt.getKotlinClass(FileEditorManagerRemote.class), project, RdTarget.DEFAULT)
                    .getSelectedEditor(map).designer();
            ActionManagerKt.invokeAction(d, actionId, true, designer.view(), null, RdTarget.DEFAULT);
            return Unit.INSTANCE;
        });
    }

    private static String text(Driver driver, Project project) {
        String[] text = new String[1];
        driver.withContext(OnDispatcher.EDT, LockSemantics.READ_ACTION, d -> {
            text[0] = document(d, EditorsKt.findOpenFile(d, "map.mm", project, false)).getText();
            return Unit.INSTANCE;
        });
        return text[0];
    }

    private static Document document(Driver driver, VirtualFile file) {
        return driver.service(JvmClassMappingKt.getKotlinClass(FileDocumentManagerRemote.class), RdTarget.DEFAULT).getDocument(file);
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

    // The IDE's services and the plug-in's designer, as the Driver sees them.

    @Remote("com.intellij.openapi.fileEditor.FileEditorManager")
    public interface FileEditorManagerRemote {
        CompositeRemote getSelectedEditor(VirtualFile file);
    }

    @Remote("com.intellij.openapi.fileEditor.FileDocumentManager")
    public interface FileDocumentManagerRemote {
        Document getDocument(VirtualFile file);

        void saveAllDocuments();
    }

    @Remote(value = "etalii.adp.core.AdpEditorProvider$Composite", plugin = PLUGIN)
    public interface CompositeRemote {
        DesignerRemote designer();
    }

    @Remote(value = "etalii.adp.freemind.ui.MindMapDesigner", plugin = PLUGIN)
    public interface DesignerRemote {
        boolean isEditable();

        MindMapRemote model();

        void reveal(NodeKeyRemote key);

        Component view();
    }

    @Remote(value = "etalii.adp.freemind.model.MindMap", plugin = PLUGIN)
    public interface MindMapRemote {
        MapNodeRemote root();
    }

    @Remote(value = "etalii.adp.freemind.model.MapNode", plugin = PLUGIN)
    public interface MapNodeRemote {
        NodeKeyRemote key();
    }

    @Remote(value = "etalii.adp.freemind.model.NodeKey", plugin = PLUGIN)
    public interface NodeKeyRemote {
        String id();
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

    /**
     * Closes the IDE. When the test already failed, a failure to close is added to that failure rather than
     * replacing it: an IDE killed at the end of its run reports only the kill, which hides what the test was doing.
     */
    private static void closeIdeAndWait(BackgroundRun run, Throwable failure) throws ReflectiveOperationException {
        Method close = Arrays.stream(BackgroundRun.class.getMethods())
                .filter(m -> m.getName().startsWith("closeIdeAndWait") && !m.getName().endsWith("$default") && m.getParameterCount() == 2)
                .findFirst().orElseThrow(() -> new NoSuchMethodException("closeIdeAndWait"));
        try {
            close.invoke(run, DurationKt.toDuration(2, DurationUnit.MINUTES), false);
        } catch (InvocationTargetException e) {
            if (failure == null) {
                throw e;
            }
            failure.addSuppressed(e.getCause());
        }
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
