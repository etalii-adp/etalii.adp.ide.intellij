package etalii.adp.it;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

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
 * Brings an open file's designer to the front before a test acts on it, as someone clicks its tab.
 * <p>
 * IntelliJ IDEA without a licence opens its "Trial" page as an editor tab a little after a project
 * opens, and selects it, so the designer's canvas is no longer showing: the action system then refuses to run an action with
 * it ("target component is not showing") and the toolbox, which follows the selected editor, lists
 * nothing. {@link #select} selects the file's tab again until its canvas shows, and notes what was
 * selected instead in {@code reselections.txt} in {@code ADP_IDE_TESTS_HOME}.
 */
final class DesignerTab {

    private static final String PLUGIN = "etalii.adp";

    private DesignerTab() {
    }

    /** Waits, for at most half a minute, until the file's designer canvas shows, selecting its tab when another one is. */
    static void select(Driver driver, Project project, String fileName, String test) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        String[] state = new String[1];
        while (true) {
            boolean[] showing = new boolean[1];
            driver.withContext(OnDispatcher.EDT, LockSemantics.NO_LOCK, d -> {
                VirtualFile file = EditorsKt.findOpenFile(d, fileName, project, false);
                EditorsRemote editors = d.service(JvmClassMappingKt.getKotlinClass(EditorsRemote.class), project, RdTarget.DEFAULT);
                showing[0] = editors.getSelectedEditor(file).designer().view().isShowing();
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
            note(test + ": " + fileName + "'s canvas was not showing; " + state[0]);
            if (System.nanoTime() > deadline) {
                throw new AssertionError(fileName + "'s designer canvas is not showing after half a minute of selecting its tab: " + state[0]);
            }
            Thread.sleep(250);
        }
    }

    /**
     * Selects the file's tab again when its canvas is not showing, on the event dispatch thread the caller is on, so
     * an action invoked next in the same call runs with a showing canvas: between two Driver calls the IDE can still
     * select another tab.
     */
    static void front(Driver d, Project project, VirtualFile file, String test) {
        EditorsRemote editors = d.service(JvmClassMappingKt.getKotlinClass(EditorsRemote.class), project, RdTarget.DEFAULT);
        if (!editors.getSelectedEditor(file).designer().view().isShowing()) {
            String state;
            try {
                state = describe(d, editors);
            } catch (RuntimeException e) {
                state = "not described: " + e;
            }
            editors.openFile(file, true);
            note(test + ": " + file.getName() + "'s canvas was not showing just before an action; " + state);
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

    private static void note(String line) {
        String home = System.getenv("ADP_IDE_TESTS_HOME");
        if (home == null || home.isBlank()) {
            return;
        }
        try {
            Path file = Path.of(home).resolve("reselections.txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // a note for CI's log only
        }
    }

    @Remote("com.intellij.openapi.fileEditor.FileEditorManager")
    public interface EditorsRemote {
        CompositeRemote getSelectedEditor(VirtualFile file);

        VirtualFile[] getSelectedFiles();

        VirtualFile[] getOpenFiles();

        void openFile(VirtualFile file, boolean focusEditor);
    }

    @Remote(value = "etalii.adp.core.AdpEditorProvider$Composite", plugin = PLUGIN)
    public interface CompositeRemote {
        DesignerRemote designer();
    }

    @Remote(value = "etalii.adp.core.diagram.view.DiagramDesigner", plugin = PLUGIN)
    public interface DesignerRemote {
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
