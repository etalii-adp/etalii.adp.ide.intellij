package etalii.adp.it;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.intellij.driver.client.Driver;
import com.intellij.driver.client.Remote;
import com.intellij.driver.model.LockSemantics;
import com.intellij.driver.model.OnDispatcher;
import com.intellij.driver.model.RdTarget;
import com.intellij.driver.sdk.EditorsKt;
import com.intellij.driver.sdk.Project;
import com.intellij.driver.sdk.VirtualFile;

import kotlin.Unit;
import kotlin.jvm.JvmClassMappingKt;

/**
 * Brings an open file's tool to the front before a test acts on it, closing any other editor tab.
 * <p>
 * IntelliJ IDEA without a licence opens its "Trial" page as an editor tab a little after a project
 * opens, and selects it, so the tool's canvas is no longer showing: the action system then
 * refuses to run an action with it ("target component is not showing") and the toolbox, which
 * follows the selected editor, lists nothing. A test opens only the one file, so every other tab
 * is the IDE's own: {@link #select}, {@link #front} and {@link #act} close it, and say so in the
 * log, and select the file's tab again. Should the Trial page keep breaking the tests in other
 * ways, the unlicensed IntelliJ IDEA run is the one to retire (Peter, 2026-10-06).
 */
final class ToolTab {

    private static final String PLUGIN = "etalii.adp";

    private ToolTab() {
    }

    /**
     * Waits, for at most half a minute, until the file's tool canvas shows, closing the other tabs and selecting its own
     * when another one is.
     */
    static void select(Driver driver, Project project, String fileName) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        String[] state = new String[1];
        while (true) {
            boolean[] showing = new boolean[1];
            driver.withContext(OnDispatcher.EDT, LockSemantics.NO_LOCK, d -> {
                VirtualFile file = EditorsKt.findOpenFile(d, fileName, project, false);
                EditorsRemote editors = d.service(JvmClassMappingKt.getKotlinClass(EditorsRemote.class), project, RdTarget.DEFAULT);
                closeOthers(editors, file);
                showing[0] = editors.getSelectedEditor(file).tool().view().isShowing();
                if (!showing[0]) {
                    try {
                        state[0] = describe(d, editors);
                    } catch (RuntimeException e) {
                        state[0] = "not described: " + e;
                    }
                    editors.openFile(file, true);
                }
                return Unit.INSTANCE;
            });
            if (showing[0]) {
                return;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError(fileName + "'s tool canvas is not showing after half a minute of selecting its tab: " + state[0]);
            }
            Thread.sleep(250);
        }
    }

    /**
     * Closes the other tabs and selects the file's tab again when its canvas is not showing, on the event dispatch
     * thread the caller is on, so an action invoked next in the same call runs with a showing canvas: between two
     * Driver calls the IDE can still open another tab. Whether the canvas shows now.
     */
    static boolean front(Driver d, Project project, VirtualFile file) {
        EditorsRemote editors = d.service(JvmClassMappingKt.getKotlinClass(EditorsRemote.class), project, RdTarget.DEFAULT);
        closeOthers(editors, file);
        if (!editors.getSelectedEditor(file).tool().view().isShowing()) {
            editors.openFile(file, true);
        }
        return editors.getSelectedEditor(file).tool().view().isShowing();
    }

    /**
     * Runs {@code body} on the event dispatch thread with the file's tool canvas showing: {@link #select}, then
     * {@link #front} and {@code body} in one call. When a tab opened in between still hides the canvas after
     * {@link #front}, it starts again rather than act on a hidden canvas, three times at most.
     */
    static void act(Driver driver, Project project, String fileName, Consumer<Driver> body) throws InterruptedException {
        for (int attempt = 1; ; attempt++) {
            select(driver, project, fileName);
            boolean[] done = new boolean[1];
            driver.withContext(OnDispatcher.EDT, LockSemantics.NO_LOCK, d -> {
                if (front(d, project, EditorsKt.findOpenFile(d, fileName, project, false))) {
                    body.accept(d);
                    done[0] = true;
                }
                return Unit.INSTANCE;
            });
            if (done[0]) {
                return;
            }
            if (attempt == 3) {
                throw new AssertionError(fileName + "'s tool canvas was hidden again each time, three times, right before acting on it");
            }
        }
    }

    /** Closes every editor tab but the file's, naming the closed ones in the log. */
    private static void closeOthers(EditorsRemote editors, VirtualFile file) {
        List<String> closed = new ArrayList<>();
        for (VirtualFile other : editors.getOpenFiles()) {
            if (!other.getPath().equals(file.getPath())) {
                closed.add(other.getName());
                editors.closeFile(other);
            }
        }
        if (!closed.isEmpty()) {
            System.out.println("Closed the editor tabs " + closed + " so " + file.getName() + "'s tool shows");
        }
    }

    /** The editor tabs and the windows showing. */
    private static String describe(Driver d, EditorsRemote editors) {
        StringBuilder text = new StringBuilder("selected files [");
        for (VirtualFile each : editors.getSelectedFiles()) {
            text.append(each.getName()).append(' ');
        }
        text.append("], open files [");
        for (VirtualFile each : editors.getOpenFiles()) {
            text.append(each.getName()).append(' ');
        }
        text.append("], windows showing [");
        for (WindowRemote window : d.utility(JvmClassMappingKt.getKotlinClass(WindowStatics.class), RdTarget.DEFAULT).getWindows()) {
            if (window.isShowing()) {
                text.append(window.getAccessibleContext().getAccessibleName()).append(" | ");
            }
        }
        return text.append(']').toString();
    }

    @Remote("com.intellij.openapi.fileEditor.FileEditorManager")
    public interface EditorsRemote {
        CompositeRemote getSelectedEditor(VirtualFile file);

        VirtualFile[] getSelectedFiles();

        VirtualFile[] getOpenFiles();

        void openFile(VirtualFile file, boolean focusEditor);

        void closeFile(VirtualFile file);
    }

    @Remote(value = "etalii.adp.core.AdpEditorProvider$Composite", plugin = PLUGIN)
    public interface CompositeRemote {
        ToolRemote tool();
    }

    @Remote(value = "etalii.adp.core.diagram.view.DiagramFileEditor", plugin = PLUGIN)
    public interface ToolRemote {
        ComponentRemote view();
    }

    @Remote("java.awt.Component")
    public interface ComponentRemote {
        boolean isShowing();
    }

    @Remote("java.awt.Window")
    public interface WindowStatics {
        WindowRemote[] getWindows();
    }

    @Remote("java.awt.Window")
    public interface WindowRemote {
        boolean isShowing();

        AccessibleContextRemote getAccessibleContext();
    }

    @Remote("javax.accessibility.AccessibleContext")
    public interface AccessibleContextRemote {
        String getAccessibleName();
    }
}
