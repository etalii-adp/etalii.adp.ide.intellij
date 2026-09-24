package etalii.adp.freemind.ui.parts;

import org.eclipse.draw2d.ColorConstants;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.PolygonDecoration;
import org.eclipse.draw2d.PolylineConnection;
import org.eclipse.gef.editparts.AbstractConnectionEditPart;

import etalii.adp.freemind.model.ArrowLink;

/**
 * An arrow link between two drawn nodes (FR-015), with an arrow head at each end the file asks for
 * one. FreeMind writes {@code None} for no arrow; any other value, or none at the end, is an arrow.
 */
public class ArrowLinkEditPart extends AbstractConnectionEditPart {

    public ArrowLink link() {
        return (ArrowLink) getModel();
    }

    @Override
    protected IFigure createFigure() {
        PolylineConnection connection = new PolylineConnection();
        connection.setForegroundColor(ColorConstants.darkBlue);
        connection.setLineDash(new float[] { 4, 3 });
        return connection;
    }

    @Override
    protected void refreshVisuals() {
        PolylineConnection connection = (PolylineConnection) getFigure();
        ArrowLink link = link();
        connection.setSourceDecoration(arrow(link.startArrow(), false) ? new PolygonDecoration() : null);
        connection.setTargetDecoration(arrow(link.endArrow(), true) ? new PolygonDecoration() : null);
    }

    private static boolean arrow(String value, boolean byDefault) {
        return value == null ? byDefault : !"None".equals(value);
    }

    @Override
    protected void createEditPolicies() {
        // arrow links are displayed only (FR-018)
    }

    @Override
    public boolean isSelectable() {
        return false;
    }
}
