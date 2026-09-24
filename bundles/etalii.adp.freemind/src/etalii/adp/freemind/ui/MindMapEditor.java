package etalii.adp.freemind.ui;

import java.util.Collection;
import java.util.List;

import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPartFactory;
import org.eclipse.jface.text.IDocument;

import etalii.adp.core.AdpDesignerEditor;
import etalii.adp.core.FormatProblem;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.parse.MindMapParser;
import etalii.adp.freemind.ui.parts.MindMapEditPartFactory;

/**
 * The FreeMind mind map designer (FR-001): the map drawn around its root on the visual page, the
 * {@code .mm} text on the text page. The GEF contents is one {@link ViewState} for the editor's
 * lifetime, so a re-parse refreshes the existing edit parts and keeps the selection.
 */
public class MindMapEditor extends AdpDesignerEditor<MindMap> {

    public static final String ID = "etalii.adp.freemind.editor";
    public static final String CONTEXT_ID = "etalii.adp.freemind.context";

    private final ViewState viewState = new ViewState();

    @Override
    protected MindMap parse(IDocument document) throws FormatProblem {
        return MindMapParser.parse(document.get());
    }

    @Override
    protected EditPartFactory createEditPartFactory() {
        return new MindMapEditPartFactory();
    }

    @Override
    protected Object contentsFor(MindMap model) {
        viewState.setMap(model);
        return viewState;
    }

    @Override
    protected String visualContextId() {
        return CONTEXT_ID;
    }

    @Override
    protected void createPages() {
        super.createPages();
        if (model() != null) {
            EditPart root = viewer().getEditPartRegistry().get(model().root().key());
            if (root != null) {
                viewer().getControl().getDisplay().asyncExec(() -> {
                    if (root.isActive() && viewer().getSelectedEditParts().isEmpty()) {
                        viewer().reveal(root);
                    }
                });
            }
        }
    }

    public ViewState viewState() {
        return viewState;
    }

    /**
     * Show the node: expand its collapsed ancestors for display only, then select and reveal it.
     * The document is not edited, so nothing becomes dirty (US4-AS2, research R7).
     */
    public void reveal(NodeKey key) {
        MindMap map = model();
        MapNode node = map == null ? null : map.node(key);
        if (node == null) {
            return;
        }
        for (MapNode ancestor = node.parent(); ancestor != null; ancestor = ancestor.parent()) {
            if (viewState.isShownFolded(ancestor)) {
                viewState.setShownFolded(ancestor, false);
            }
        }
        viewState.selectAfterRefresh(List.of(key));
        refreshView();
    }

    /** Collapse or expand branches for display only, without an edit (read-only files, R7). */
    public void showFolded(Collection<MapNode> nodes, boolean folded) {
        for (MapNode node : nodes) {
            viewState.setShownFolded(node, folded);
        }
        refreshView();
    }

    /** Redraw after a change to {@link #viewState()} alone. */
    public void redraw() {
        refreshView();
    }
}
