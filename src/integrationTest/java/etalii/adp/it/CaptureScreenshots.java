package etalii.adp.it;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
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
 * Takes the screenshots in {@code docs/screenshots/} (see its readme): each row opens one example
 * file in its designer in a real IntelliJ IDEA 2026.2 with the built plug-in zip installed, sizes the
 * IDE window to 1600×900, and paints that window into a PNG. It is not a test and does not run with
 * {@code integrationTest}; {@code ./gradlew captureScreenshots} runs it and writes the images into
 * {@code docs/screenshots/}. {@code ADP_IDE_HOME_IU} works as in {@link OpenMapIntegrationTest}.
 */
@Tag("capture")
class CaptureScreenshots {

    private static final String PLUGIN = "etalii.adp";
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 900;
    private static final int TOOL_WINDOW_WIDTH = 260;

    private final Path repository = Path.of(System.getProperty("adp.repository", "."));
    private final Path output = Path.of(System.getProperty("adp.screenshots", repository.resolve("docs/screenshots").toString()));

    static Stream<Arguments> screenshots() {
        return Stream.of(
                Arguments.of("mindmap.png", "freemind/testdata/examples/freemind-0.9.0-arrow-links-icons-cjk.mm", List.of()),
                Arguments.of("drawio-activity.png", "drawio/testdata/examples/activity_diagram_1.drawio", List.of("ADP Toolbox")),
                Arguments.of("drawio-cross-functional.png", "drawio/testdata/examples/cross_functional_flowchart_1.drawio", List.of("ADP Toolbox")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("screenshots")
    void capture(String image, String example, List<String> toolWindows, @TempDir Path temporary) throws Exception {
        Path zip = Path.of(System.getProperty("adp.plugin.zip"));
        assertTrue(Files.isRegularFile(zip), "No plug-in zip at " + zip);
        // The folder's name is the project's name in the title bar and the Project view.
        Path project = Files.createDirectories(temporary.resolve("Examples"));
        String file = Path.of(example).getFileName().toString();
        Files.copy(repository.resolve(example), project.resolve(file));
        Files.createDirectories(output);

        IdeInfo info = installedOrDownloaded(IdeaUltimateProductInitKt.getDefaultIdeaUltimate());
        TestCase<LocalProjectInfo> testCase = new TestCase<>(info, localProject(project), List.of(), false, null);
        if (System.getenv("ADP_IDE_HOME_" + info.getProductCode()) == null) {
            testCase = testCase.withVersion("2026.2.3");
        }
        IDETestContext context = Starter.INSTANCE.newContext("Screenshot-" + image.replace(".png", ""), testCase, false);
        context.getPluginConfigurator().installPluginFromPath(zip);

        BackgroundRun run = runIdeWithDriver(context);
        Throwable failure = null;
        try {
            Driver driver = run.getDriver();
            Project opened = waitForProject(driver);

            driver.withContext(OnDispatcher.EDT, LockSemantics.NO_LOCK, d -> {
                WindowRemote window = d.utility(JvmClassMappingKt.getKotlinClass(SwingUtilitiesRemote.class), RdTarget.DEFAULT)
                        .getWindowAncestor(d.service(JvmClassMappingKt.getKotlinClass(WindowManagerRemote.class), RdTarget.DEFAULT)
                                .getIdeFrame(opened).getComponent());
                d.cast(window, JvmClassMappingKt.getKotlinClass(FrameRemote.class)).setExtendedState(0);
                window.setBounds(40, 40, WIDTH, HEIGHT);
                window.validate();
                EditorsKt.openFile(d, file, opened, false, false);
                return Unit.INSTANCE;
            });

            // Let indexing, the pages the IDE opens on a first start and the designer's layout settle.
            Thread.sleep(15_000);

            driver.withContext(OnDispatcher.EDT, LockSemantics.NO_LOCK, d -> {
                VirtualFile opening = EditorsKt.findOpenFile(d, file, opened, false);
                assertNotNull(opening, image + ": the example is open");
                // The IDE opens pages of its own on a first start (a trial notice, what's new); only the example stays.
                FileEditorManagerRemote editors = d.service(JvmClassMappingKt.getKotlinClass(FileEditorManagerRemote.class), opened, RdTarget.DEFAULT);
                for (VirtualFile other : editors.getOpenFiles()) {
                    if (!other.getPath().equals(opening.getPath())) {
                        editors.closeFile(other);
                    }
                }
                ToolWindowManagerRemote manager = d.service(JvmClassMappingKt.getKotlinClass(ToolWindowManagerRemote.class), opened, RdTarget.DEFAULT);
                for (String id : List.of("Project", "Services", "Problems View", "Terminal")) {
                    ToolWindowRemote bottom = manager.getToolWindow(id);
                    if (bottom != null) {
                        bottom.hide();
                    }
                }
                for (String id : toolWindows) {
                    ToolWindowRemote shown = manager.getToolWindow(id);
                    shown.show();
                    // A first start gives a side tool window a third of the frame; the diagram gets that room.
                    d.cast(shown, JvmClassMappingKt.getKotlinClass(ToolWindowExRemote.class)).stretchWidth(TOOL_WINDOW_WIDTH - shown.getComponent().getWidth());
                }
                return Unit.INSTANCE;
            });

            // Let the tool windows and the toolbox follow the designer before painting.
            Thread.sleep(5_000);

            Path target = output.resolve(image).toAbsolutePath();
            driver.withContext(OnDispatcher.EDT, LockSemantics.NO_LOCK, d -> {
                WindowRemote window = d.utility(JvmClassMappingKt.getKotlinClass(SwingUtilitiesRemote.class), RdTarget.DEFAULT)
                        .getWindowAncestor(d.service(JvmClassMappingKt.getKotlinClass(WindowManagerRemote.class), RdTarget.DEFAULT)
                                .getIdeFrame(opened).getComponent());
                BufferedImageRemote picture = create(d, BufferedImageRemote.class, window.getWidth(), window.getHeight(), 1);
                GraphicsRemote graphics = picture.createGraphics();
                window.printAll(graphics);
                graphics.dispose();
                FileRemote destination = create(d, FileRemote.class, target.toString());
                assertTrue(d.utility(JvmClassMappingKt.getKotlinClass(ImageIORemote.class), RdTarget.DEFAULT).write(picture, "png", destination),
                        image + ": a PNG writer is available");
                return Unit.INSTANCE;
            });
            assertTrue(Files.size(target) > 0, image + " was written");
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            closeIdeAndWait(run, failure);
        }
    }

    /** A new object in the IDE, through {@code Driver.new}, which Java cannot call by name. */
    private static <T> T create(Driver driver, Class<T> type, Object... arguments) {
        try {
            Method method = Driver.class.getMethod("new", kotlin.reflect.KClass.class, Object[].class);
            return type.cast(method.invoke(driver, JvmClassMappingKt.getKotlinClass(type), arguments));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Driver.new(" + type.getSimpleName() + ")", e);
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

    // The IDE's frame and tool windows, and the AWT and ImageIO calls that paint the frame into a file.

    @Remote("com.intellij.openapi.wm.WindowManager")
    public interface WindowManagerRemote {
        IdeFrameRemote getIdeFrame(Project project);
    }

    @Remote("com.intellij.openapi.wm.IdeFrame")
    public interface IdeFrameRemote {
        Component getComponent();
    }

    @Remote("javax.swing.SwingUtilities")
    public interface SwingUtilitiesRemote {
        WindowRemote getWindowAncestor(Component component);
    }

    @Remote("java.awt.Window")
    public interface WindowRemote {
        void setBounds(int x, int y, int width, int height);

        void validate();

        int getWidth();

        int getHeight();

        void printAll(GraphicsRemote graphics);
    }

    @Remote("java.awt.Frame")
    public interface FrameRemote {
        void setExtendedState(int state);
    }

    @Remote("com.intellij.openapi.wm.ToolWindowManager")
    public interface ToolWindowManagerRemote {
        ToolWindowRemote getToolWindow(String id);
    }

    @Remote("com.intellij.openapi.wm.ToolWindow")
    public interface ToolWindowRemote {
        void show();

        void hide();

        Component getComponent();
    }

    @Remote("com.intellij.openapi.wm.ex.ToolWindowEx")
    public interface ToolWindowExRemote {
        void stretchWidth(int value);
    }

    @Remote("com.intellij.openapi.fileEditor.FileEditorManager")
    public interface FileEditorManagerRemote {
        VirtualFile[] getOpenFiles();

        void closeFile(VirtualFile file);
    }

    @Remote("java.awt.image.BufferedImage")
    public interface BufferedImageRemote {
        GraphicsRemote createGraphics();
    }

    @Remote("java.awt.Graphics2D")
    public interface GraphicsRemote {
        void dispose();
    }

    @Remote("java.io.File")
    public interface FileRemote {
    }

    @Remote("javax.imageio.ImageIO")
    public interface ImageIORemote {
        boolean write(BufferedImageRemote image, String format, FileRemote output);
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
