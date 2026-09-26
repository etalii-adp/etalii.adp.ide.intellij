package etalii.adp.core.diagram.properties;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

/**
 * The ADP Properties tool window (research R14, contracts/plugin-contributions.md): one
 * {@link PropertyPanel}, shared by every diagram designer, that follows the selected editor.
 */
public final class PropertiesToolWindowFactory implements ToolWindowFactory, DumbAware {

    public static final String ID = "ADP Properties";

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        PropertyPanel panel = new PropertyPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, null, false);
        content.setDisposer(panel);
        content.setPreferredFocusableComponent(panel.table());
        toolWindow.getContentManager().addContent(content);
    }
}
