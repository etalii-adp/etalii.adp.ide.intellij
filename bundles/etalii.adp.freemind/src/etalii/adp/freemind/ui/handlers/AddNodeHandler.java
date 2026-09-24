package etalii.adp.freemind.ui.handlers;

import java.util.List;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;

import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.ui.MindMapEditor;
import etalii.adp.freemind.ui.NodeRenameManager;
import etalii.adp.freemind.ui.ViewState;

/**
 * Add Child Node and Add Sibling Node (FR-019), chosen by the parameter after the class name in
 * {@code plugin.xml}: {@code child} or {@code sibling}. The new node is selected and its in-place
 * editor opens, so its text can be typed right away; typing it is a separate "Rename Node" edit. A
 * child of a folded branch is shown by expanding the branch for display only, without another edit.
 */
public class AddNodeHandler extends NodeHandler implements IExecutableExtension {

    static final String NEW_NODE_TEXT = "New Node";

    private boolean sibling;

    @Override
    public void setInitializationData(IConfigurationElement config, String propertyName, Object data) {
        sibling = data instanceof String parameter && "sibling".equals(parameter.trim());
    }

    @Override
    protected boolean enabledFor(MindMapEditor editor, List<MapNode> nodes) {
        return editor.isEditable() && nodes.size() == 1 && (!sibling || nodes.get(0).parent() != null);
    }

    @Override
    protected void run(MindMapEditor editor, List<MapNode> nodes) {
        MapNode node = nodes.get(0);
        Edit edit = sibling ? MindMapEdits.addSibling(editor.model(), node.key(), NEW_NODE_TEXT)
                : MindMapEdits.addChild(editor.model(), node.key(), NEW_NODE_TEXT);
        ViewState view = editor.viewState();
        if (!sibling && view.isShownFolded(node)) {
            view.setShownFolded(node, false);
        }
        view.selectAfterRefresh(List.of(edit.created()));
        editor.execute(edit.label(), edit.textEdit());
        if (editor.model() == null || editor.model().node(edit.created()) == null) {
            view.takePendingSelection(); // the edit did not happen
            return;
        }
        NodeRenameManager.open(editor, edit.created());
    }
}
