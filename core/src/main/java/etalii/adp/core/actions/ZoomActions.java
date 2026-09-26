package etalii.adp.core.actions;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;

import etalii.adp.core.AdpDataKeys;
import etalii.adp.core.AdpDesignerEditor;
import etalii.adp.core.diagram.view.DiagramDesigner;

/**
 * Zoom In, Zoom Out and Actual Size, enabled only with a focused designer that zooms: every
 * designer but a diagram designer whose view options turn zoom off (FR-025).
 */
public final class ZoomActions {

    private ZoomActions() {
    }

    /** False for a diagram designer whose view options turn zoom off. */
    static boolean zooms(AdpDesignerEditor<?> designer) {
        return !(designer instanceof DiagramDesigner diagram) || diagram.definition().view().zoom();
    }

    /** An action on the focused designer; disabled anywhere else, so its shortcut keeps its meaning in other editors. */
    abstract static class DesignerAction extends AnAction implements DumbAware {

        protected abstract void run(AdpDesignerEditor<?> designer);

        @Override
        public void update(@NotNull AnActionEvent event) {
            AdpDesignerEditor<?> designer = event.getData(AdpDataKeys.ADP_DESIGNER);
            event.getPresentation().setEnabled(designer != null && zooms(designer));
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
            AdpDesignerEditor<?> designer = event.getData(AdpDataKeys.ADP_DESIGNER);
            if (designer != null && zooms(designer)) {
                run(designer);
            }
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    public static final class ZoomIn extends DesignerAction {
        @Override
        protected void run(AdpDesignerEditor<?> designer) {
            designer.zoomIn();
        }
    }

    public static final class ZoomOut extends DesignerAction {
        @Override
        protected void run(AdpDesignerEditor<?> designer) {
            designer.zoomOut();
        }
    }

    public static final class ZoomReset extends DesignerAction {
        @Override
        protected void run(AdpDesignerEditor<?> designer) {
            designer.resetZoom();
        }
    }
}
