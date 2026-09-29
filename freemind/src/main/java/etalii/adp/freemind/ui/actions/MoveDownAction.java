package etalii.adp.freemind.ui.actions;

import java.util.List;

import etalii.adp.freemind.edit.MindMapEdits.Placement;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.ui.MindMapFileEditor;

/**
 * Move Node Down (spec 001 FR-022): the one selected node goes after its next sibling; on the first
 * level, after the next node on its own side, as they are drawn.
 */
public final class MoveDownAction extends MindMapAction {

    @Override
    protected String disabledReason(MindMapFileEditor tool, MindMap map, List<MapNode> nodes) {
        if (nodes.size() != 1) {
            return ONE_NODE;
        }
        if (nodes.get(0).parent() == null) {
            return ROOT_CANNOT_BE_MOVED;
        }
        return neighbour(map, nodes.get(0), 1) == null ? NO_NEXT_SIBLING : null;
    }

    @Override
    protected void perform(MindMapFileEditor tool, MindMap map, List<MapNode> nodes) {
        move(tool, map, nodes.get(0), neighbour(map, nodes.get(0), 1), Placement.AFTER);
    }
}
