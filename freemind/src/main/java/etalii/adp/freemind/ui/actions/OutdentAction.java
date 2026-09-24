package etalii.adp.freemind.ui.actions;

import java.util.List;

import etalii.adp.freemind.edit.MindMapEdits.Placement;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.ui.MindMapDesigner;

/**
 * Move Up a Level (spec 001 FR-022): the one selected node, at depth two or deeper, goes right
 * after its parent, and gets its parent's side as {@code POSITION} when it reaches the first level.
 */
public final class OutdentAction extends MindMapAction {

    @Override
    protected String disabledReason(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        if (nodes.size() != 1) {
            return ONE_NODE;
        }
        if (nodes.get(0).parent() == null) {
            return ROOT_CANNOT_BE_MOVED;
        }
        return nodes.get(0).depth() < 2 ? FIRST_LEVEL : null;
    }

    @Override
    protected void perform(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        move(designer, map, nodes.get(0), nodes.get(0).parent(), Placement.AFTER);
    }
}
