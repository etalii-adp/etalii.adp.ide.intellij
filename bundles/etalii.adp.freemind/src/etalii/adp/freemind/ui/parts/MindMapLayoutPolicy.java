package etalii.adp.freemind.ui.parts;

import java.util.List;

import org.eclipse.draw2d.ColorConstants;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.RectangleFigure;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPolicy;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.Request;
import org.eclipse.gef.RequestConstants;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.editpolicies.LayoutEditPolicy;
import org.eclipse.gef.editpolicies.NonResizableEditPolicy;
import org.eclipse.gef.requests.ChangeBoundsRequest;
import org.eclipse.gef.requests.CreateRequest;

import etalii.adp.core.TextEditCommand;
import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.edit.MindMapEdits.Placement;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.ui.MindMapEditor;

/**
 * Drag and drop of nodes (FR-022). Dropping in the upper quarter of a node puts the dragged node
 * before it, in the lower quarter after it, and elsewhere makes it the node's last child. The
 * command is a {@link MindMapEdits#move} edit; there is none for the root, for a drop into the
 * dragged node's own subtree, or on a read-only file. Coordinates are never stored: the layout
 * places the node wherever the tree puts it.
 */
public class MindMapLayoutPolicy extends LayoutEditPolicy {

    /** Where a drop lands: a node and a placement relative to it. */
    record Drop(GraphicalEditPart target, Placement placement) {
    }

    private IFigure feedback;

    @Override
    protected EditPolicy createChildEditPolicy(EditPart child) {
        return new NonResizableEditPolicy();
    }

    @Override
    public Command getCommand(Request request) {
        if (RequestConstants.REQ_MOVE.equals(request.getType()) || RequestConstants.REQ_ADD.equals(request.getType())) {
            return moveCommand((ChangeBoundsRequest) request);
        }
        return super.getCommand(request);
    }

    @Override
    protected Command getMoveChildrenCommand(Request request) {
        return moveCommand((ChangeBoundsRequest) request);
    }

    @Override
    protected Command getCreateCommand(CreateRequest request) {
        return null;
    }

    private Command moveCommand(ChangeBoundsRequest request) {
        MindMapEditor editor = PartSupport.editor(getHost());
        List<? extends EditPart> dragged = request.getEditParts();
        if (editor == null || !editor.isEditable() || editor.model() == null || dragged == null || dragged.size() != 1
                || !(dragged.get(0).getModel() instanceof NodeKey key)) {
            return null;
        }
        Drop drop = dropAt(request);
        if (drop == null) {
            return null;
        }
        MindMap map = editor.model();
        if (map.node(key) == null || map.node((NodeKey) drop.target().getModel()) == null) {
            return null;
        }
        Edit edit = MindMapEdits.move(map, key, (NodeKey) drop.target().getModel(), drop.placement());
        return edit == null ? null : new TextEditCommand(edit.label(), edit::textEdit);
    }

    /** The node under the request's location, other than the dragged ones, and where on it. */
    Drop dropAt(ChangeBoundsRequest request) {
        if (request.getLocation() == null) {
            return null;
        }
        Point location = request.getLocation().getCopy();
        getHostFigure().translateToRelative(location);
        for (EditPart child : getHost().getChildren()) {
            if (child instanceof GraphicalEditPart part && !request.getEditParts().contains(part)
                    && part.getModel() instanceof NodeKey) {
                Rectangle bounds = part.getFigure().getBounds();
                if (bounds.contains(location)) {
                    int offset = location.y - bounds.y;
                    Placement placement = offset < bounds.height / 4 ? Placement.BEFORE
                            : offset >= bounds.height - bounds.height / 4 ? Placement.AFTER : Placement.INTO;
                    return new Drop(part, placement);
                }
            }
        }
        return null;
    }

    @Override
    protected void showLayoutTargetFeedback(Request request) {
        if (!(request instanceof ChangeBoundsRequest change)) {
            return;
        }
        Drop drop = moveCommand(change) == null ? null : dropAt(change);
        if (drop == null) {
            eraseLayoutTargetFeedback(request);
            return;
        }
        if (feedback == null) {
            RectangleFigure figure = new RectangleFigure();
            figure.setForegroundColor(ColorConstants.blue);
            figure.setBackgroundColor(ColorConstants.blue);
            figure.setLineWidth(2);
            feedback = figure;
            addFeedback(feedback);
        }
        Rectangle bounds = drop.target().getFigure().getBounds().getCopy();
        drop.target().getFigure().translateToAbsolute(bounds);
        feedback.translateToRelative(bounds);
        ((RectangleFigure) feedback).setFill(drop.placement() != Placement.INTO);
        feedback.setBounds(switch (drop.placement()) {
        case BEFORE -> new Rectangle(bounds.x, bounds.y - 2, bounds.width, 3);
        case AFTER -> new Rectangle(bounds.x, bounds.bottom() - 1, bounds.width, 3);
        case INTO -> bounds.expand(2, 2);
        });
    }

    @Override
    protected void eraseLayoutTargetFeedback(Request request) {
        if (feedback != null) {
            removeFeedback(feedback);
            feedback = null;
        }
    }
}
