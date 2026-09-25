package etalii.adp.freemind.ui.actions;

import java.util.List;

import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.ui.InPlaceRename;
import etalii.adp.freemind.ui.MindMapDesigner;

/**
 * Add Child Node (spec 001 FR-019): a new last child of the one selected node, selected, with its
 * in-place editor open so its text can be typed right away. A child of a folded branch is shown by
 * expanding the branch for display only, without another edit.
 */
public final class AddChildAction extends MindMapAction {

    @Override
    protected String disabledReason(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        return nodes.size() == 1 ? null : ONE_NODE;
    }

    @Override
    protected void perform(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        MapNode parent = nodes.get(0);
        Edit edit = MindMapEdits.addChild(map, parent.key(), NEW_NODE_TEXT, now(), random());
        if (designer.isShownFolded(parent)) {
            designer.setShownFolded(parent, false);
        }
        added(designer, edit);
    }

    /** Runs the edit, then selects the new node and opens its in-place editor. */
    static void added(MindMapDesigner designer, Edit edit) {
        execute(designer, edit, () -> {
            MindMap after = designer.model();
            if (after != null && after.node(edit.created()) != null) {
                designer.select(List.of(edit.created()));
            }
        });
        if (designer.selection().equals(List.of(edit.created()))) {
            InPlaceRename.open(designer, edit.created());
        }
    }
}
