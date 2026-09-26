package etalii.adp.freemind.ui.actions;

import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.wm.StatusBar;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.ui.MindMapDesigner;

/**
 * Delete Node / Delete Nodes (spec 001 FR-021): the selected nodes with their descendants, and the
 * arrow links into them, as one step. The root is never deleted; when it is selected with other
 * nodes, the others are, and the status bar says why the root stayed. What remains nearest to the
 * first deleted node is selected afterwards. It is the framework's delete, through the designer's
 * commands, with spec 001's name and selection.
 */
public final class DeleteAction extends MindMapAction {

    private static final String SEVERAL = "Delete Nodes";

    @Override
    public void update(@NotNull AnActionEvent event) {
        super.update(event);
        Object[] selected = event.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
        long nodes = selected == null ? 0 : Arrays.stream(selected).filter(NodeKey.class::isInstance).count();
        event.getPresentation().setText(nodes > 1 ? SEVERAL : getTemplatePresentation().getText());
    }

    @Override
    protected String disabledReason(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        return nodes.stream().anyMatch(node -> node.parent() != null) ? null : ROOT_CANNOT_BE_DELETED;
    }

    @Override
    protected void perform(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        List<MapNode> deleted = nodes.stream().filter(node -> node.parent() != null).toList();
        if (deleted.size() < nodes.size()) {
            StatusBar.Info.set(ROOT_CANNOT_BE_DELETED, designer.project());
        }
        MapNode survivor = deleted.get(0).parent();
        while (survivor.parent() != null && isInside(survivor, deleted)) {
            survivor = survivor.parent();
        }
        // Paths of other nodes can shift when several go, so a path-keyed survivor is only
        // selected again after a single delete.
        NodeKey keep = survivor.id() != null || deleted.size() == 1 ? survivor.key() : null;
        designer.withReselect(() -> {
            MindMap after = designer.model();
            designer.select(keep != null && after != null && after.node(keep) != null ? List.of(keep) : List.of());
        }, () -> designer.commands().remove(deleted.stream().map(MapNode::key).toList()));
    }

    private static boolean isInside(MapNode node, List<MapNode> deleted) {
        return deleted.stream().anyMatch(d -> d.contains(node));
    }
}
