package etalii.adp.freemind.ui.handlers;

import java.util.List;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.ui.MindMapEditor;
import etalii.adp.freemind.ui.NodeRenameManager;

/** Rename Node (FR-020): F2 or a double-click opens the in-place editor on the one selected node. */
public class RenameHandler extends NodeHandler {

    @Override
    protected boolean enabledFor(MindMapEditor editor, List<MapNode> nodes) {
        return editor.isEditable() && nodes.size() == 1;
    }

    @Override
    protected void run(MindMapEditor editor, List<MapNode> nodes) {
        NodeRenameManager.open(editor, nodes.get(0).key());
    }
}
