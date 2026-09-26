package etalii.adp.freemind.ui.actions;

import java.util.ArrayList;
import java.util.List;

import etalii.adp.core.TextChange;
import etalii.adp.core.TextChanges;
import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.ui.MindMapDesigner;

/**
 * Fold / Unfold Branch (spec 001 FR-016, FR-023). The first selected branch decides: shown folded,
 * every selected branch unfolds; else every one folds. On an editable file that is one
 * {@code FOLDED} step for all of them; on a read-only file it changes the display only, so it stays
 * available there. A branch only shown differently from its {@code FOLDED}, after a reveal, just
 * drops that display override.
 */
public final class ToggleFoldAction extends MindMapAction {

    @Override
    protected boolean needsEditable() {
        return false;
    }

    @Override
    protected String disabledReason(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        return nodes.stream().anyMatch(node -> !node.children().isEmpty()) ? null : NOTHING_TO_FOLD;
    }

    @Override
    protected void perform(MindMapDesigner designer, MindMap map, List<MapNode> nodes) {
        List<MapNode> branches = nodes.stream().filter(node -> !node.children().isEmpty()).toList();
        boolean fold = !designer.isShownFolded(branches.get(0));
        if (!designer.isEditable()) {
            branches.forEach(branch -> designer.setShownFolded(branch, fold));
            return;
        }
        List<TextChange> changes = new ArrayList<>();
        for (MapNode branch : branches) {
            // the file decides again; where it already says what the user asked for, that is all
            designer.setShownFolded(branch, branch.folded());
            Edit edit = MindMapEdits.setFolded(map, branch.key(), fold);
            if (edit != null) {
                changes.addAll(edit.changes().changes());
            }
        }
        if (!changes.isEmpty()) {
            designer.runCommand(fold ? MindMapEdits.FOLD : MindMapEdits.UNFOLD, text -> new TextChanges(changes), () -> {
            });
        }
    }
}
