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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

import kotlin.Unit;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.time.DurationKt;
import kotlin.time.DurationUnit;

/**
 * T111, the draw.io designer in a real IntelliJ IDEA 2026.2 with the built plug-in zip installed:
 * {@code flowchart_1.drawio} opens in the draw.io designer, a rounded rectangle is added from the
 * ADP Toolbox as pressing Enter on its entry does, and the IDE's own Undo then returns the file to
 * its original bytes. {@code ADP_IT_PRODUCTS} and {@code ADP_IDE_HOME_<CODE>} work as in
 * {@link OpenMapIntegrationTest}.
 */
class OpenDrawioTest {

    private static final String PLUGIN = "etalii.adp";
    private static final String PRODUCT = "IntelliJ IDEA";
    private static final String FILE = "flowchart.drawio";

    private final Path repository = Path.of(System.getProperty("adp.repository", "."));

    @Test
    void addAShapeFromTheToolboxThenUndoRestoresTheOriginalBytes(@TempDir Path project) throws Exception {
        String selected = System.getenv("ADP_IT_PRODUCTS");
        assumeTrue(selected == null || selected.isBlank() || Arrays.stream(selected.split(",")).map(String::strip).anyMatch(PRODUCT::equals),
                PRODUCT + " is not in ADP_IT_PRODUCTS");
        Path zip = Path.of(System.getProperty("adp.plugin.zip"));
        assertTrue(Files.isRegularFile(zip), "No plug-in zip at " + zip);
        byte[] original = Files.readAllBytes(repository.resolve("drawio/testdata/examples/flowchart_1.drawio"));
        Files.write(project.resolve(FILE), original);

        IdeInfo info = installedOrDownloaded(IdeaUltimateProductInitKt.getDefaultIdeaUltimate());
        TestCase<LocalProjectInfo> testCase = new TestCase<>(info, localProject(project), List.of(), false, null);
        if (System.getenv("ADP_IDE_HOME_" + info.getProductCode()) == null) {
            testCase = testCase.withVersion("2026.2.3");
        }
        IDETestContext context = Starter.INSTANCE.newContext("OpenDrawio", testCase, false);
        context.getPluginConfigurator().installPluginFromPath(zip);

        BackgroundRun run = runIdeWithDriver(context);
        Throwable failure = null;
        try {
            Driver driver = run.getDriver();
            Project opened = waitForProject(driver);
            awaitIgnoreFile(project);
            String[] path = new String[1];
            String[] texts = new String[3];

            driver.withContext(OnDispatcher.EDT, LockSemantics.NO_LOCK, d -> {
                EditorsKt.openFile(d, FILE, opened, false, false);
                VirtualFile file = EditorsKt.findOpenFile(d, FILE, opened, false);
                assertNotNull(file, "the diagram is open");
                path[0] = file.getPath();
                CompositeRemote composite = d.service(JvmClassMappingKt.getKotlinClass(FileEditorManagerRemote.class), opened, RdTarget.DEFAULT)
                        .getSelectedEditor(file);
                assertNotNull(composite, "the diagram opens in an editor");
                assertEquals("draw.io Designer", composite.getName());
                assertTrue(composite.designer().isEditable(), "the diagram can be edited");
                texts[0] = document(d, file).getText();
                return Unit.INSTANCE;
            });

            DesignerTab.select(driver, opened, FILE, "OpenDrawio");
            driver.withContext(OnDispatcher.EDT, LockSemantics.NO_LOCK, d -> {
                DesignerTab.front(d, opened, EditorsKt.findOpenFile(d, FILE, opened, false), "OpenDrawio");
                ToolWindowRemote toolbox = d.service(JvmClassMappingKt.getKotlinClass(ToolWindowManagerRemote.class), opened, RdTarget.DEFAULT)
                        .getToolWindow("ADP Toolbox");
                assertNotNull(toolbox, "the ADP Toolbox is registered");
                toolbox.show();
                ToolboxRemote panel = toolbox.getContentManager().getContent(0).getComponent();
                panel.refreshFromSelectedEditor();
                assertTrue(panel.entries().contains("rounded"), "the toolbox lists draw.io shapes: " + panel.entries());
                panel.activate("rounded");
                return Unit.INSTANCE;
            });

            texts[1] = text(driver, opened);
            assertNotEquals(texts[0], texts[1], "adding from the toolbox changed the diagram");
            assertTrue(texts[1].contains("<mxCell id=\"adp-1\" value=\"\" style=\"rounded=1;whiteSpace=wrap;html=1;\""), "the new shape is in the text");

            DesignerTab.select(driver, opened, FILE, "OpenDrawio");
            invoke(driver, opened, "$Undo");
            texts[2] = text(driver, opened);
            assertEquals(texts[0], texts[2], "Undo returns the text");

            driver.withContext(OnDispatcher.EDT, LockSemantics.WRITE_ACTION, d -> {
                d.service(JvmClassMappingKt.getKotlinClass(FileDocumentManagerRemote.class), RdTarget.DEFAULT).saveAllDocuments();
                return Unit.INSTANCE;
            });
            assertArrayEquals(original, Files.readAllBytes(Path.of(path[0])), "the saved file has its original bytes");
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            closeIdeAndWait(run, failure);
        }
    }

    /** Runs an action through the action system with the designer's canvas as its context, as its shortcut would. */
    private static void invoke(Driver driver, Project project, String actionId) {
        driver.withContext(OnDispatcher.EDT, LockSemantics.NO_LOCK, d -> {
            VirtualFile file = EditorsKt.findOpenFile(d, FILE, project, false);
            DesignerTab.front(d, project, file, "OpenDrawio");
            DesignerRemote designer = d.service(JvmClassMappingKt.getKotlinClass(FileEditorManagerRemote.class), project, RdTarget.DEFAULT)
                    .getSelectedEditor(file).designer();
            ActionManagerKt.invokeAction(d, actionId, true, designer.view(), null, RdTarget.DEFAULT);
            return Unit.INSTANCE;
        });
    }

    private static String text(Driver driver, Project project) {
        String[] text = new String[1];
        driver.withContext(OnDispatcher.EDT, LockSemantics.READ_ACTION, d -> {
            text[0] = document(d, EditorsKt.findOpenFile(d, FILE, project, false)).getText();
            return Unit.INSTANCE;
        });
        return text[0];
    }

    private static Document document(Driver driver, VirtualFile file) {
        return driver.service(JvmClassMappingKt.getKotlinClass(FileDocumentManagerRemote.class), RdTarget.DEFAULT).getDocument(file);
    }

    /**
     * Waits, for at most a minute, until the IDE has written {@code .idea/.gitignore} into the new project,
     * which it does on its own shortly after opening it. That write is a global undoable change: an edit made
     * before it lands under it on the undo stack, and Undo then asks whether to undo the new file first.
     */
    private static void awaitIgnoreFile(Path project) throws InterruptedException {
        Path file = project.resolve(".idea").resolve(".gitignore");
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(1);
        while (!Files.exists(file) && System.nanoTime() < deadline) {
            Thread.sleep(250);
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

    // The IDE's services and the plug-in's designer and toolbox, as the Driver sees them.

    @Remote("com.intellij.openapi.fileEditor.FileEditorManager")
    public interface FileEditorManagerRemote {
        CompositeRemote getSelectedEditor(VirtualFile file);
    }

    @Remote("com.intellij.openapi.fileEditor.FileDocumentManager")
    public interface FileDocumentManagerRemote {
        Document getDocument(VirtualFile file);

        void saveAllDocuments();
    }

    @Remote("com.intellij.openapi.wm.ToolWindowManager")
    public interface ToolWindowManagerRemote {
        ToolWindowRemote getToolWindow(String id);
    }

    @Remote("com.intellij.openapi.wm.ToolWindow")
    public interface ToolWindowRemote {
        void show();

        ContentManagerRemote getContentManager();
    }

    @Remote("com.intellij.ui.content.ContentManager")
    public interface ContentManagerRemote {
        ContentRemote getContent(int index);
    }

    @Remote("com.intellij.ui.content.Content")
    public interface ContentRemote {
        ToolboxRemote getComponent();
    }

    @Remote(value = "etalii.adp.core.diagram.toolbox.ToolboxPanel", plugin = PLUGIN)
    public interface ToolboxRemote {
        void refreshFromSelectedEditor();

        List<String> entries();

        void activate(String typeId);
    }

    @Remote(value = "etalii.adp.core.AdpEditorProvider$Composite", plugin = PLUGIN)
    public interface CompositeRemote {
        String getName();

        DesignerRemote designer();
    }

    @Remote(value = "etalii.adp.core.diagram.view.DiagramDesigner", plugin = PLUGIN)
    public interface DesignerRemote {
        boolean isEditable();

        Component view();
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
