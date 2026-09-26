package etalii.adp.core.diagram.properties;

import java.awt.event.MouseEvent;

import javax.swing.SwingUtilities;

import com.intellij.openapi.Disposable;

import etalii.adp.core.diagram.DiagramFeature;
import etalii.adp.core.diagram.view.CanvasTool;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramDesigner;

/**
 * Properties on every diagram canvas (US3): a double-click on an editable text slot or connection
 * label edits it in place. The ADP Properties tool window and the EditInPlace and ShowProperties
 * actions this fragment registers need nothing on the canvas.
 */
public final class PropertiesFeature implements DiagramFeature {

    @Override
    public void install(DiagramDesigner designer, DiagramCanvas canvas, Disposable lifetime) {
        canvas.addTool(new CanvasTool() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2
                        && InPlaceEditor.open(designer, InPlaceEditor.at(designer, canvas.toDiagram(e.getPoint())))) {
                    e.consume();
                }
            }
        });
    }
}
