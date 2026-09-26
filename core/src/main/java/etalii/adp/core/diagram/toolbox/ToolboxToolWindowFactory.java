package etalii.adp.core.diagram.toolbox;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

/**
 * The ADP Toolbox tool window (research R13, contracts/plugin-contributions.md): one
 * {@link ToolboxPanel} that follows the selected editor and lists its diagram designer's toolbox.
 */
public final class ToolboxToolWindowFactory implements ToolWindowFactory, DumbAware {

    public static final String ID = "ADP Toolbox";

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ToolboxPanel panel = new ToolboxPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, null, false);
        content.setDisposer(panel);
        content.setPreferredFocusableComponent(panel.entryList());
        toolWindow.getContentManager().addContent(content);
    }
}
