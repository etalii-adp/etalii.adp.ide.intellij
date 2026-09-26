package etalii.adp.core.diagram.edit;

import com.intellij.openapi.Disposable;

import etalii.adp.core.diagram.DiagramFeature;
import etalii.adp.core.diagram.toolbox.ToolboxDropTarget;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramDesigner;

/**
 * Editing on every diagram canvas (US2): the refusal feedback, the resize handles, and the tools
 * in the order they claim a press: a selected element's resize handle, then an anchor or a
 * selected connection's end, then selection, then moving the selection. It also takes toolbox
 * drops. The {@code etalii.adp.core.DiagramPopup} context menu this fragment registers is
 * installed by the designer itself.
 */
public final class EditingFeature implements DiagramFeature {

    @Override
    public void install(DiagramDesigner designer, DiagramCanvas canvas, Disposable lifetime) {
        RefusalFeedback feedback = RefusalFeedback.install(canvas);
        HandlesLayer handles = new HandlesLayer(designer);
        canvas.addLayer(handles);

        // simplified: a selected element's resize handle wins over an anchor at the same spot (the sample task's
        // in and out anchors sit on its W and E handles), so connecting from there needs the element unselected;
        // if that binds, draw the handles outside the bounds and hit-test them there
        ResizeTool resize = new ResizeTool(designer, handles, feedback);
        ConnectTool connect = new ConnectTool(designer, feedback);
        SelectionTool selection = new SelectionTool(designer);
        MoveTool move = new MoveTool(designer, feedback);
        canvas.addTool(resize);
        canvas.addTool(connect);
        canvas.addTool(selection);
        canvas.addTool(move);
        canvas.addLayer(resize);
        canvas.addLayer(connect);
        canvas.addLayer(selection);
        canvas.addLayer(move);

        ToolboxDropTarget.install(designer, feedback, lifetime);
    }
}
