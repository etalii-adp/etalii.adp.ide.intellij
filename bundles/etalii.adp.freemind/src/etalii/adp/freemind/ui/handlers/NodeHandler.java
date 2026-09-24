package etalii.adp.freemind.ui.handlers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.gef.EditPart;
import org.eclipse.ui.ISources;
import org.eclipse.ui.handlers.HandlerUtil;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.ui.MindMapEditor;

/**
 * Base of the FreeMind command handlers. It finds the active {@link MindMapEditor} and the nodes
 * selected on its visual page; a subclass says when it applies ({@link #enabledFor}) and what it
 * does ({@link #run}). By default a handler needs an editable file and at least one selected node.
 */
public abstract class NodeHandler extends AbstractHandler {

    /** Whether the command applies to these selected nodes. */
    protected boolean enabledFor(MindMapEditor editor, List<MapNode> nodes) {
        return editor.isEditable() && !nodes.isEmpty();
    }

    /** Do the command. Only called when {@link #enabledFor} said yes. */
    protected abstract void run(MindMapEditor editor, List<MapNode> nodes) throws ExecutionException;

    @Override
    public void setEnabled(Object evaluationContext) {
        MindMapEditor editor = editorOf(evaluationContext);
        setBaseEnabled(editor != null && enabledFor(editor, selectedNodes(editor)));
    }

    @Override
    public final Object execute(ExecutionEvent event) throws ExecutionException {
        MindMapEditor editor = HandlerUtil.getActivePart(event) instanceof MindMapEditor part ? part
                : HandlerUtil.getActiveEditor(event) instanceof MindMapEditor active ? active : null;
        if (editor != null) {
            List<MapNode> nodes = selectedNodes(editor);
            if (enabledFor(editor, nodes)) {
                run(editor, nodes);
            }
        }
        return null;
    }

    /** The selected nodes in the latest parse, in selection order. */
    protected static List<MapNode> selectedNodes(MindMapEditor editor) {
        MindMap map = editor.model();
        List<MapNode> nodes = new ArrayList<>();
        if (map == null || editor.viewer() == null) {
            return nodes;
        }
        for (NodeKey key : selectedKeys(editor)) {
            MapNode node = map.node(key);
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    /** The keys of the node parts selected on the visual page. */
    protected static List<NodeKey> selectedKeys(MindMapEditor editor) {
        List<NodeKey> keys = new ArrayList<>();
        for (EditPart part : editor.viewer().getSelectedEditParts()) {
            if (part.getModel() instanceof NodeKey key) {
                keys.add(key);
            }
        }
        return keys;
    }

    /** Explain on the status line why nothing happened, for example "The root node cannot be moved". */
    protected static void showStatus(MindMapEditor editor, String message) {
        editor.getEditorSite().getActionBars().getStatusLineManager().setMessage(message);
    }

    private static MindMapEditor editorOf(Object evaluationContext) {
        if (!(evaluationContext instanceof IEvaluationContext context)) {
            return null;
        }
        if (context.getVariable(ISources.ACTIVE_PART_NAME) instanceof MindMapEditor editor) {
            return editor;
        }
        return context.getVariable(ISources.ACTIVE_EDITOR_NAME) instanceof MindMapEditor editor ? editor : null;
    }
}
