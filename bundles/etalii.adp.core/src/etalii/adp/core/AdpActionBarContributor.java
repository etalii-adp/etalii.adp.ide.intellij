package etalii.adp.core;

import org.eclipse.gef.ui.actions.GEFActionConstants;
import org.eclipse.jface.action.IAction;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.MultiPageEditorActionBarContributor;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;

/**
 * Global actions per page. On the visual page, undo and redo run the workbench history in the
 * document's undo context, so Edit > Undo and Ctrl+Z mean the same on both pages (FR-004), and zoom
 * and select all act on the viewer. On the text page, the text editor's own actions apply.
 */
public class AdpActionBarContributor extends MultiPageEditorActionBarContributor {

    private static final String[] TEXT_ACTIONS = { ITextEditorActionConstants.UNDO, ITextEditorActionConstants.REDO,
            ITextEditorActionConstants.CUT, ITextEditorActionConstants.COPY, ITextEditorActionConstants.PASTE,
            ITextEditorActionConstants.DELETE, ITextEditorActionConstants.SELECT_ALL, ITextEditorActionConstants.FIND,
            ITextEditorActionConstants.PRINT };

    private static final String[] VISUAL_ACTIONS = { ActionFactory.UNDO.getId(), ActionFactory.REDO.getId(),
            ActionFactory.SELECT_ALL.getId(), GEFActionConstants.ZOOM_IN, GEFActionConstants.ZOOM_OUT };

    private AdpDesignerEditor<?> designer;

    @Override
    public void setActiveEditor(IEditorPart part) {
        designer = part instanceof AdpDesignerEditor<?> editor ? editor : null;
        super.setActiveEditor(part);
    }

    @Override
    public void setActivePage(IEditorPart activeEditor) {
        IActionBars bars = getActionBars();
        if (bars == null || designer == null) {
            return;
        }
        bars.clearGlobalActionHandlers();
        ITextEditor text = designer.textEditor();
        if (activeEditor instanceof ITextEditor pageEditor) {
            for (String id : TEXT_ACTIONS) {
                bars.setGlobalActionHandler(id, pageEditor.getAction(id));
            }
        } else {
            for (String id : VISUAL_ACTIONS) {
                bars.setGlobalActionHandler(id, designer.visualAction(id));
            }
        }
        if (text != null) {
            IAction revert = text.getAction(ITextEditorActionConstants.REVERT_TO_SAVED);
            bars.setGlobalActionHandler(ActionFactory.REVERT.getId(), revert);
        }
        bars.updateActionBars();
    }
}
