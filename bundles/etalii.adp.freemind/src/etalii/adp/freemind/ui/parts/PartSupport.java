package etalii.adp.freemind.ui.parts;

import org.eclipse.gef.DefaultEditDomain;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPartViewer;

import etalii.adp.freemind.ui.MindMapEditor;
import etalii.adp.freemind.ui.ViewState;

/** Lookups every FreeMind edit part needs: its editor and the viewer's {@link ViewState}. */
final class PartSupport {

    private PartSupport() {
    }

    /** The editor the part is drawn in, or {@code null} outside a {@link MindMapEditor}. */
    static MindMapEditor editor(EditPart part) {
        EditPartViewer viewer = part.getViewer();
        if (viewer != null && viewer.getEditDomain() instanceof DefaultEditDomain domain
                && domain.getEditorPart() instanceof MindMapEditor editor) {
            return editor;
        }
        return null;
    }

    static ViewState viewState(EditPart part) {
        EditPartViewer viewer = part.getViewer();
        return viewer != null && viewer.getContents() != null && viewer.getContents().getModel() instanceof ViewState state ? state : null;
    }
}
