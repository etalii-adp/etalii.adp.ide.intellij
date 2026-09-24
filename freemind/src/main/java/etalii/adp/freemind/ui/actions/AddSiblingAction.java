package etalii.adp.freemind.ui.actions;

import java.util.List;

import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.ui.MindMapDesigner;

/**
 * Add Sibling Node (spec 001 FR-019): a new node right after the one selected node, on the same
 * side on the first level, selected with its in-place editor open. The root has no siblings.
 */
public final class AddSiblingAction extends MindMapAction {

    @Override
    protected String disabledReason(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        if (nodes.size() != 1) {
            return ONE_NODE;
        }
        return nodes.get(0).parent() == null ? ROOT_HAS_NO_SIBLINGS : null;
    }

    @Override
    protected void perform(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        AddChildAction.added(designer, MindMapEdits.addSibling(map, nodes.get(0).key(), NEW_NODE_TEXT, now(), random()));
    }
}
