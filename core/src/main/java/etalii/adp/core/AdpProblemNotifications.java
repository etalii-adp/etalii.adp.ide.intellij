package etalii.adp.core;

import java.util.function.Function;

import javax.swing.JComponent;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;

/**
 * A banner above a designer's file while its text cannot be shown as a design, with the reason. A
 * problem sends the user to the text view, where the designer's own problem panel is out of sight.
 */
public final class AdpProblemNotifications implements EditorNotificationProvider, DumbAware {

    @Override
    public Function<? super FileEditor, ? extends JComponent> collectNotificationData(@NotNull Project project, @NotNull VirtualFile file) {
        return editor -> {
            String problem = editor instanceof AdpEditorProvider.Composite composite ? composite.designer().problemMessage() : null;
            if (problem == null) {
                return null;
            }
            EditorNotificationPanel panel = new EditorNotificationPanel(editor, EditorNotificationPanel.Status.Warning);
            panel.setText(problem);
            return panel;
        };
    }
}