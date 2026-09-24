package etalii.adp.freemind.ui.parts;

import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPartFactory;

import etalii.adp.freemind.model.ArrowLink;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.ui.ViewState;

/** Edit parts for the visual page's models: the map, its nodes and its arrow links. */
public class MindMapEditPartFactory implements EditPartFactory {

    @Override
    public EditPart createEditPart(EditPart context, Object model) {
        EditPart part;
        if (model instanceof ViewState) {
            part = new MapEditPart();
        } else if (model instanceof NodeKey) {
            part = new NodeEditPart();
        } else if (model instanceof ArrowLink) {
            part = new ArrowLinkEditPart();
        } else {
            throw new IllegalArgumentException("No edit part for " + model);
        }
        part.setModel(model);
        return part;
    }
}
