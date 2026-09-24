package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.example;
import static etalii.adp.freemind.MindMapAsserts.key;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.Adapters;
import org.eclipse.draw2d.FigureCanvas;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.EditDomain;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.editparts.ZoomManager;
import org.eclipse.gef.tools.MarqueeSelectionTool;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.Test;

import etalii.adp.freemind.model.NodeKey;
import etalii.adp.testing.DesignerDriver;

/** FR-017, FR-027, US1-AS3: zoom, pan, selection and keyboard navigation. */
class ViewerInteractionTest {

    static final String MAP = """
            <map version="1.0.1">
            <node ID="R" TEXT="Root">
            <node ID="B" POSITION="right" TEXT="B"/>
            <node ID="C" POSITION="right" TEXT="C"/>
            <node ID="E" POSITION="right" TEXT="E"/>
            <node ID="L" POSITION="left" TEXT="L"/>
            </node>
            </map>
            """;

    /** GEF's zoom commands, bound to Ctrl+= and Ctrl+- in every GEF editor. */
    static final String ZOOM_IN = "org.eclipse.gef.zoom_in";
    static final String ZOOM_OUT = "org.eclipse.gef.zoom_out";

    @Test
    void zoomInAndOutThroughThePlatformCommands() {
        try (var d = DesignerDriver.openText("zoom.mm", MAP, MindMapEditor.ID)) {
            ZoomManager zoom = d.editor().getAdapter(ZoomManager.class);
            double before = zoom.getZoom();
            int width = d.figureOf(key("R")).getBounds().width;
            d.run(ZOOM_IN);
            assertTrue(zoom.getZoom() > before);
            assertTrue(absolute(d.figureOf(key("R"))).width > width, "the map is drawn larger");
            d.run(ZOOM_OUT);
            assertEquals(before, zoom.getZoom(), 1e-9);
        }
    }

    @Test
    void panByScrolling() {
        try (var d = DesignerDriver.open(example("freeplane-large-map.mm"), MindMapEditor.ID)) {
            FigureCanvas canvas = assertInstanceOf(FigureCanvas.class, d.editor().viewer().getControl());
            Point before = canvas.getViewport().getViewLocation().getCopy();
            canvas.scrollTo(before.x + 50, before.y + 80);
            d.settle();
            assertNotEquals(before, canvas.getViewport().getViewLocation());
        }
    }

    @Test
    void singleAndMultipleSelection() {
        try (var d = DesignerDriver.openText("select.mm", MAP, MindMapEditor.ID)) {
            d.select(key("B"));
            assertEquals(List.of(key("B")), d.selectedModels());
            d.select(key("B"), key("L"));
            assertEquals(List.of(key("B"), key("L")), d.selectedModels());
        }
    }

    @Test
    void marqueeSelectsTheNodesInside() {
        try (var d = DesignerDriver.openText("marquee.mm", MAP, MindMapEditor.ID)) {
            GraphicalViewer viewer = d.editor().viewer();
            Rectangle area = absolute(d.figureOf(key("B"))).union(absolute(d.figureOf(key("C")))).expand(3, 3);
            EditDomain domain = viewer.getEditDomain();
            domain.setActiveTool(new MarqueeSelectionTool());
            domain.mouseDown(mouse(viewer, area.x, area.y, 0), viewer);
            domain.mouseDrag(mouse(viewer, area.x + 10, area.y + 10, SWT.BUTTON1), viewer);
            domain.mouseDrag(mouse(viewer, area.right(), area.bottom(), SWT.BUTTON1), viewer);
            domain.mouseUp(mouse(viewer, area.right(), area.bottom(), SWT.BUTTON1), viewer);
            domain.loadDefaultTool();
            d.settle();
            assertEquals(Set.of(key("B"), key("C")), Set.copyOf(d.selectedModels()));
        }
    }

    @Test
    void arrowKeysMoveTheSelectionBetweenNodes() {
        try (var d = DesignerDriver.openText("keys.mm", MAP, MindMapEditor.ID)) {
            GraphicalViewer viewer = d.editor().viewer();
            d.select(key("B"));
            viewer.getKeyHandler().keyPressed(keyEvent(viewer, SWT.ARROW_DOWN));
            d.settle();
            assertEquals(List.of(key("C")), d.selectedModels());
        }
    }

    @Test
    void theSelectionIsPublishedAsNodeKeys() {
        try (var d = DesignerDriver.openText("publish.mm", MAP, MindMapEditor.ID)) {
            d.select(key("C"), key("L"));
            IStructuredSelection selection = assertInstanceOf(IStructuredSelection.class,
                    d.editor().getSite().getSelectionProvider().getSelection());
            List<NodeKey> keys = ((List<?>) selection.toList()).stream().map(e -> Adapters.adapt(e, NodeKey.class)).toList();
            assertEquals(List.of(key("C"), key("L")), keys);
        }
    }

    private static Rectangle absolute(IFigure figure) {
        Rectangle bounds = figure.getBounds().getCopy();
        figure.translateToAbsolute(bounds);
        return bounds;
    }

    private static MouseEvent mouse(GraphicalViewer viewer, int x, int y, int stateMask) {
        Event event = new Event();
        event.widget = viewer.getControl();
        event.display = viewer.getControl().getDisplay();
        event.x = x;
        event.y = y;
        event.button = 1;
        event.stateMask = stateMask;
        return new MouseEvent(event);
    }

    private static KeyEvent keyEvent(GraphicalViewer viewer, int keyCode) {
        Event event = new Event();
        event.widget = viewer.getControl();
        event.display = viewer.getControl().getDisplay();
        event.keyCode = keyCode;
        return new KeyEvent(event);
    }
}
