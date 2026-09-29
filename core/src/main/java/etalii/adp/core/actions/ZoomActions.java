package etalii.adp.core.actions;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;

import etalii.adp.core.AdpDataKeys;
import etalii.adp.core.AdpToolFileEditor;
import etalii.adp.core.diagram.view.DiagramFileEditor;

/**
 * Zoom In, Zoom Out and Actual Size, enabled only with a focused tool that zooms: every
 * tool but a diagram whose view options turn zoom off (FR-025).
 */
public final class ZoomActions {

    private ZoomActions() {
    }

    /** False for a diagram whose view options turn zoom off. */
    static boolean zooms(AdpToolFileEditor<?> tool) {
        return !(tool instanceof DiagramFileEditor diagram) || diagram.definition().view().zoom();
    }

    /** An action on the focused tool; disabled anywhere else, so its shortcut keeps its meaning in other editors. */
    abstract static class ToolAction extends AnAction implements DumbAware {

        protected abstract void run(AdpToolFileEditor<?> tool);

        @Override
        public void update(@NotNull AnActionEvent event) {
            AdpToolFileEditor<?> tool = event.getData(AdpDataKeys.ADP_TOOL);
            event.getPresentation().setEnabled(tool != null && zooms(tool));
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            AdpToolFileEditor<?> tool = event.getData(AdpDataKeys.ADP_TOOL);
            if (tool != null && zooms(tool)) {
                run(tool);
            }
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    public static final class ZoomIn extends ToolAction {
        @Override
        protected void run(AdpToolFileEditor<?> tool) {
            tool.zoomIn();
        }
    }

    public static final class ZoomOut extends ToolAction {
        @Override
        protected void run(AdpToolFileEditor<?> tool) {
            tool.zoomOut();
        }
    }

    public static final class ZoomReset extends ToolAction {
        @Override
        protected void run(AdpToolFileEditor<?> tool) {
            tool.resetZoom();
        }
    }
}
