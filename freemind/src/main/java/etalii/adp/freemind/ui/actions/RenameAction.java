package etalii.adp.freemind.ui.actions;

import java.util.List;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.ui.MindMapDesigner;

/** Rename Node (spec 001 FR-020): opens the framework's in-place editor on the one selected node's text. */
public final class RenameAction extends MindMapAction {

    @Override
    protected String disabledReason(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        return nodes.size() == 1 ? null : ONE_NODE;
    }

    @Override
    protected void perform(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        designer.rename(nodes.get(0).key());
    }
}
