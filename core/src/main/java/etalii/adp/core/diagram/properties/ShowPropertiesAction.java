package etalii.adp.core.diagram.properties;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

import etalii.adp.core.AdpDataKeys;
import etalii.adp.core.diagram.view.DiagramDesigner;

/**
 * {@code etalii.adp.core.ShowProperties} (Alt+Shift+P): activates the ADP Properties tool window.
 * It is enabled only while a diagram canvas has focus.
 */
public final class ShowPropertiesAction extends AnAction implements DumbAware {

    public static final String ID = "etalii.adp.core.ShowProperties";

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(designer(event) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        DiagramDesigner designer = designer(event);
        if (designer == null) {
            return;
        }
        ToolWindow window = ToolWindowManager.getInstance(designer.project()).getToolWindow(PropertiesToolWindowFactory.ID);
        if (window != null) {
            window.activate(null);
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    /** The diagram designer whose canvas has focus, or {@code null}. */
    private static DiagramDesigner designer(AnActionEvent event) {
        if (!(event.getData(AdpDataKeys.ADP_DESIGNER) instanceof DiagramDesigner designer)) {
            return null;
        }
        return event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) == designer.canvas() ? designer : null;
    }
}
