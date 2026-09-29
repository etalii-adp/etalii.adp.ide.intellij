package etalii.adp.drawio;

import etalii.adp.core.diagram.view.DiagramEditorProvider;

/**
 * Opens {@code .drawio} files whose first element is {@code mxfile} or {@code mxGraphModel} in the
 * draw.io diagram; {@code .drawio.svg} and {@code .drawio.png} are not claimed.
 */
public final class DrawioEditorProvider extends DiagramEditorProvider {

    public static final String EDITOR_TYPE_ID = "etalii.adp.drawio";

    public DrawioEditorProvider() {
        super(DrawioDefinition.DEFINITION, DrawioMapping::new, EDITOR_TYPE_ID, "draw.io diagram", "drawio", "mxfile", "mxGraphModel");
    }
}
