package etalii.adp.freemind.ui.parts;

import java.util.List;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.draw2d.IFigure;
import org.eclipse.gef.Request;
import org.eclipse.gef.RequestConstants;
import org.eclipse.gef.editparts.AbstractGraphicalEditPart;
import org.eclipse.ui.handlers.IHandlerService;

import etalii.adp.freemind.model.ArrowLink;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.ui.LinkOpener;
import etalii.adp.freemind.ui.MindMapEditor;
import etalii.adp.freemind.ui.ViewState;
import etalii.adp.freemind.ui.figures.NodeFigure;

/**
 * One node: model its {@link NodeKey} (research R6), drawn from the node of that key in the latest
 * parse. It adapts to {@link NodeKey} for the workbench selection (FR-027), and a double-click runs
 * the Rename command.
 */
public class NodeEditPart extends AbstractGraphicalEditPart {

    public static final String RENAME_COMMAND = "etalii.adp.freemind.rename";

    public NodeKey key() {
        return (NodeKey) getModel();
    }

    /** The node in the latest parse, or {@code null} while this part is being removed. */
    public MapNode node() {
        ViewState state = PartSupport.viewState(this);
        MindMap map = state == null ? null : state.map();
        return map == null ? null : map.node(key());
    }

    @Override
    protected IFigure createFigure() {
        return new NodeFigure(this::followLink);
    }

    @Override
    protected void createEditPolicies() {
        // selection and drag feedback come from the map's layout policy
    }

    @Override
    protected void refreshVisuals() {
        MapNode node = node();
        ViewState state = PartSupport.viewState(this);
        if (node != null && getParent() instanceof MapEditPart map) {
            ((NodeFigure) getFigure()).update(node, state.isShownFolded(node), node.parent() == null, map.resources());
        }
    }

    @Override
    protected List<ArrowLink> getModelSourceConnections() {
        MapNode node = node();
        return node == null ? List.of() : PartSupport.viewState(this).linksFrom(node);
    }

    @Override
    protected List<ArrowLink> getModelTargetConnections() {
        MapNode node = node();
        return node == null ? List.of() : PartSupport.viewState(this).linksTo(node);
    }

    @Override
    public void performRequest(Request request) {
        if (RequestConstants.REQ_OPEN.equals(request.getType())) {
            MindMapEditor editor = PartSupport.editor(this);
            if (editor != null) {
                try {
                    editor.getSite().getService(IHandlerService.class).executeCommand(RENAME_COMMAND, null);
                } catch (NotHandledException | NotEnabledException | NotDefinedException | ExecutionException e) {
                    // nothing to rename with (yet), or not now
                }
            }
            return;
        }
        super.performRequest(request);
    }

    private void followLink() {
        MapNode node = node();
        MindMapEditor editor = PartSupport.editor(this);
        if (node != null && editor != null) {
            LinkOpener.open(editor, node.link());
        }
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (adapter == NodeKey.class) {
            return adapter.cast(key());
        }
        return super.getAdapter(adapter);
    }
}
