package etalii.adp.freemind.ui.actions;

import java.util.List;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.ui.InPlaceRename;
import etalii.adp.freemind.ui.MindMapDesigner;

/** Rename Node (spec 001 FR-020): opens the in-place editor on the one selected node. */
public final class RenameAction extends MindMapAction {

    @Override
    protected String disabledReason(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        return nodes.size() == 1 ? null : ONE_NODE;
    }

    @Override
    protected void perform(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        InPlaceRename.open(designer, nodes.get(0).key());
    }
}
