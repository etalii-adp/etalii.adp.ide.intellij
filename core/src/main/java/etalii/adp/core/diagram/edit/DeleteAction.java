package etalii.adp.core.diagram.edit;

import java.awt.Component;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.DumbAware;

import etalii.adp.core.AdpDataKeys;
import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.view.DiagramFileEditor;
import etalii.adp.core.diagram.view.Scene.ConnectionRender;
import etalii.adp.core.diagram.view.Scene.ElementRender;

/**
 * {@code etalii.adp.core.Delete}: deletes the selected elements and connections, an element's
 * connections with it, as one "Delete". It has the platform's Delete shortcut, and is enabled only
 * while a diagram canvas has focus, so Delete keeps its meaning in an in-place editor and in other
 * editors. A refusal, such as a placeholder or a diagram rule, shows its reason in a balloon by
 * the selection.
 */
public final class DeleteAction extends AnAction implements DumbAware {

    @Override
    public void update(@NotNull AnActionEvent event) {
        DiagramFileEditor fileEditor = tool(event);
        event.getPresentation().setEnabled(fileEditor != null && !deletable(fileEditor).isEmpty());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        DiagramFileEditor fileEditor = tool(event);
        if (fileEditor == null) {
            return;
        }
        List<Object> keys = deletable(fileEditor);
        if (keys.isEmpty()) {
            return;
        }
        Point2D near = near(fileEditor, keys.get(0));
        Verdict verdict = fileEditor.commands().remove(keys);
        RefusalFeedback feedback = RefusalFeedback.of(fileEditor.canvas());
        if (!verdict.allowed() && feedback != null) {
            feedback.balloon(near, verdict.reason());
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    /** The diagram whose canvas has focus and whose file may be edited, or {@code null}. */
    private static DiagramFileEditor tool(AnActionEvent event) {
        if (!(event.getData(AdpDataKeys.ADP_TOOL) instanceof DiagramFileEditor fileEditor) || !fileEditor.isEditable()) {
            return null;
        }
        Component focused = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
        return focused == fileEditor.canvas() ? fileEditor : null;
    }

    /** The selected items that still exist. */
    private static List<Object> deletable(DiagramFileEditor fileEditor) {
        Diagram diagram = fileEditor.diagram();
        if (diagram == null) {
            return List.of();
        }
        return fileEditor.selection().stream().filter(key -> diagram.element(key) != null || diagram.connection(key) != null).toList();
    }

    /** The middle of an item as drawn, where the balloon points. */
    private static Point2D near(DiagramFileEditor fileEditor, Object key) {
        ElementRender element = fileEditor.canvas().scene().elements().get(key);
        ConnectionRender connection = fileEditor.canvas().scene().connections().get(key);
        Rectangle2D box = element != null ? element.bounds() : connection != null ? connection.extent() : new Rectangle2D.Double();
        return new Point2D.Double(box.getCenterX(), box.getCenterY());
    }
}
