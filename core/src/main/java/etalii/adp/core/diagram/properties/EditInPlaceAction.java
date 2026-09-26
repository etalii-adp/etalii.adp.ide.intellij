package etalii.adp.core.diagram.properties;

import java.awt.Component;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.DumbAware;

import etalii.adp.core.AdpDataKeys;
import etalii.adp.core.diagram.properties.InPlaceEditor.Target;
import etalii.adp.core.diagram.view.DiagramDesigner;

/**
 * {@code etalii.adp.core.EditInPlace} (F2): edits the selected item's first editable text in
 * place. It is enabled only while a diagram canvas has focus and a selected item has such a text,
 * so F2 keeps its meaning elsewhere.
 */
public final class EditInPlaceAction extends AnAction implements DumbAware {

    public static final String ID = "etalii.adp.core.EditInPlace";

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(target(event) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Target target = target(event);
        if (target != null) {
            InPlaceEditor.open((DiagramDesigner) event.getData(AdpDataKeys.ADP_DESIGNER), target);
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    /** The first editable text of the first selected item that has one, in a focused canvas of an editable file, or {@code null}. */
    private static Target target(AnActionEvent event) {
        if (!(event.getData(AdpDataKeys.ADP_DESIGNER) instanceof DiagramDesigner designer) || !designer.isEditable()) {
            return null;
        }
        Component focused = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
        if (focused != designer.canvas()) {
            return null;
        }
        for (Object key : designer.selection()) {
            Target target = InPlaceEditor.first(designer, key);
            if (target != null) {
                return target;
            }
        }
        return null;
    }
}
