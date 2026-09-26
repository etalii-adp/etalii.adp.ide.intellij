package etalii.adp.core.diagram.navigation;

import com.intellij.openapi.Disposable;

import etalii.adp.core.diagram.DiagramFeature;
import etalii.adp.core.diagram.Space;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramDesigner;

/**
 * Navigation on every diagram canvas (US4): the sectors in diagram and view space, panning and
 * wheel zooming. Its fragment registers it first, so the pan tool claims a Space-drag before
 * selection and move do. Sector membership is decided by {@code DiagramCommands} on every move.
 */
public final class NavigationFeature implements DiagramFeature {

    @Override
    public void install(DiagramDesigner designer, DiagramCanvas canvas, Disposable lifetime) {
        canvas.addLayer(new SectorLayer(designer, Space.DIAGRAM));
        canvas.addLayer(new SectorLayer(designer, Space.VIEW));
        canvas.addTool(new PanTool(designer));
        canvas.addTool(new WheelZoomTool(designer));
    }
}
