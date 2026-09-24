package etalii.adp.freemind.ui.handlers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.ui.MindMapEditor;
import etalii.adp.freemind.ui.ViewState;

/**
 * Fold / Unfold Branch (FR-016, FR-023, research R7). The first selected branch decides: shown
 * folded, every selected branch unfolds; else every one folds. On an editable file that is one
 * labelled {@code FOLDED} edit for all of them; on a read-only file it changes the display only.
 * A branch only shown differently from its {@code FOLDED}, after a reveal, just drops that override.
 */
public class ToggleFoldHandler extends NodeHandler {

    @Override
    protected boolean enabledFor(MindMapEditor editor, List<MapNode> nodes) {
        return nodes.stream().anyMatch(node -> !node.children().isEmpty());
    }

    @Override
    protected void run(MindMapEditor editor, List<MapNode> nodes) {
        List<MapNode> branches = nodes.stream().filter(node -> !node.children().isEmpty()).toList();
        ViewState view = editor.viewState();
        boolean fold = !view.isShownFolded(branches.get(0));
        if (!editor.isEditable()) {
            editor.showFolded(branches, fold);
            return;
        }
        List<TextEdit> edits = new ArrayList<>();
        for (MapNode branch : branches) {
            view.clearOverride(branch.key());
            Edit edit = MindMapEdits.setFolded(editor.model(), branch.key(), fold);
            if (edit != null) {
                edits.add(edit.textEdit());
            }
        }
        if (edits.isEmpty()) {
            editor.redraw();
            return;
        }
        TextEdit edit = edits.get(0);
        if (edits.size() > 1) {
            MultiTextEdit all = new MultiTextEdit();
            edits.forEach(all::addChild);
            edit = all;
        }
        editor.execute(fold ? MindMapEdits.FOLD : MindMapEdits.UNFOLD, edit);
    }
}
