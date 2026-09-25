package etalii.adp.freemind.ui.actions;

import java.util.List;

import etalii.adp.freemind.edit.MindMapEdits.Placement;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.ui.MindMapDesigner;

/**
 * Move Node Up (spec 001 FR-022): the one selected node goes before its previous sibling; on the
 * first level, before the previous node on its own side, as they are drawn.
 */
public final class MoveUpAction extends MindMapAction {

    @Override
    protected String disabledReason(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        if (nodes.size() != 1) {
            return ONE_NODE;
        }
        if (nodes.get(0).parent() == null) {
            return ROOT_CANNOT_BE_MOVED;
        }
        return neighbour(map, nodes.get(0), -1) == null ? NO_PREVIOUS_SIBLING : null;
    }

    @Override
    protected void perform(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        move(designer, map, nodes.get(0), neighbour(map, nodes.get(0), -1), Placement.BEFORE);
    }
}
