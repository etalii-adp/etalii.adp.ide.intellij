package etalii.adp.freemind.ui.parts;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.draw2d.ColorConstants;
import org.eclipse.draw2d.FreeformLayer;
import org.eclipse.draw2d.Graphics;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPolicy;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.editparts.AbstractGraphicalEditPart;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.viewers.StructuredSelection;

import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.ui.MindMapLayout;
import etalii.adp.freemind.ui.ViewState;

/**
 * The map: model {@link ViewState}, children the drawn nodes' {@link NodeKey}s, laid out by
 * {@link MindMapLayout} and painted with the parent-child lines (FR-013, FR-017).
 */
public class MapEditPart extends AbstractGraphicalEditPart {

    private ResourceManager resources;
    private MindMapLayout layout;

    public ViewState viewState() {
        return (ViewState) getModel();
    }

    /** Colours and fonts for node figures, disposed with the viewer's control. */
    ResourceManager resources() {
        if (resources == null) {
            resources = new LocalResourceManager(JFaceResources.getResources(), getViewer().getControl());
        }
        return resources;
    }

    @Override
    protected IFigure createFigure() {
        layout = new MindMapLayout(this::viewState, key -> {
            EditPart part = getViewer().getEditPartRegistry().get(key);
            return part instanceof GraphicalEditPart graphical && part.getParent() == this ? graphical.getFigure() : null;
        });
        FreeformLayer layer = new FreeformLayer() {
            @Override
            protected void paintFigure(Graphics graphics) {
                super.paintFigure(graphics);
                paintEdges(graphics);
            }
        };
        layer.setLayoutManager(layout);
        return layer;
    }

    private void paintEdges(Graphics graphics) {
        graphics.setForegroundColor(ColorConstants.gray);
        graphics.setLineWidth(1);
        for (MindMapLayout.Edge edge : layout.edges()) {
            Rectangle parent = edge.parent().getBounds();
            Rectangle child = edge.child().getBounds();
            int parentX = edge.left() ? parent.x : parent.right();
            int childX = edge.left() ? child.right() : child.x;
            int childY = child.bottom() - 1;
            graphics.drawLine(parentX, parent.getCenter().y, childX, childY);
            graphics.drawLine(child.x, childY, child.right(), childY);
        }
    }

    @Override
    protected List<NodeKey> getModelChildren() {
        return viewState().visibleKeys();
    }

    @Override
    protected void createEditPolicies() {
        installEditPolicy(EditPolicy.LAYOUT_ROLE, new MindMapLayoutPolicy());
    }

    @Override
    protected void refreshVisuals() {
        // The tree may have changed shape without any node figure changing.
        getFigure().revalidate();
        getFigure().repaint();
    }

    @Override
    protected void refreshChildren() {
        super.refreshChildren();
        List<NodeKey> keys = viewState().takePendingSelection();
        if (keys != null) {
            List<EditPart> parts = new ArrayList<>();
            for (NodeKey key : keys) {
                EditPart part = getViewer().getEditPartRegistry().get(key);
                if (part != null) {
                    parts.add(part);
                }
            }
            getViewer().setSelection(new StructuredSelection(parts));
            if (!parts.isEmpty()) {
                // after the layout has run, which the update manager does next
                EditPart last = parts.get(parts.size() - 1);
                getViewer().getControl().getDisplay().asyncExec(() -> {
                    if (last.isActive()) {
                        last.getViewer().reveal(last);
                    }
                });
            }
        }
    }

    @Override
    public boolean isSelectable() {
        return false;
    }
}
