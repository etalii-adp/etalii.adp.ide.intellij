package etalii.adp.freemind.ui.handlers;

import java.util.List;

import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.ui.MindMapEditor;

/**
 * Delete Node / Delete Nodes (FR-021): the selected nodes with their descendants, and the arrow links
 * into them, as one edit. The root is never deleted; when it is selected with other nodes, the others
 * are, and the status line says why the root stayed.
 */
public class DeleteHandler extends NodeHandler {

    static final String ROOT_REFUSED = "The root node cannot be deleted";

    @Override
    protected boolean enabledFor(MindMapEditor editor, List<MapNode> nodes) {
        return editor.isEditable() && nodes.stream().anyMatch(node -> node.parent() != null);
    }

    @Override
    protected void run(MindMapEditor editor, List<MapNode> nodes) {
        List<MapNode> deleted = nodes.stream().filter(node -> node.parent() != null).toList();
        if (deleted.size() < nodes.size()) {
            showStatus(editor, ROOT_REFUSED);
        }
        List<NodeKey> keys = deleted.stream().map(MapNode::key).toList();
        Edit edit = MindMapEdits.delete(editor.model(), keys);

        // Select what remains nearest to the first deleted node. Paths of other nodes can shift when
        // several go, so a path-keyed survivor is only reselected after a single delete.
        MapNode survivor = deleted.get(0).parent();
        while (survivor.parent() != null && isInside(survivor, deleted)) {
            survivor = survivor.parent();
        }
        if (survivor.id() != null || deleted.size() == 1) {
            editor.viewState().selectAfterRefresh(List.of(survivor.key()));
        }
        editor.execute(edit.label(), edit.textEdit());
    }

    private static boolean isInside(MapNode node, List<MapNode> deleted) {
        return deleted.stream().anyMatch(d -> d.contains(node));
    }
}
