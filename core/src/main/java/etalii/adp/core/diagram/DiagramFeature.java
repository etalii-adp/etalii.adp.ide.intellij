package etalii.adp.core.diagram;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;

import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramDesigner;

/**
 * A part of the framework that adds tools and layers to every diagram canvas: editing,
 * properties, navigation. Each registers one from its own descriptor fragment as
 * {@code <etalii.adp.diagramFeature implementation="..."/>}, so no part edits another's
 * registration. Designer authors do not implement it.
 */
public interface DiagramFeature {

    ExtensionPointName<DiagramFeature> EP_NAME = ExtensionPointName.create("etalii.adp.diagramFeature");

    /** Add tools and layers to {@code canvas}; anything to undo when the designer closes goes with {@code lifetime}. */
    void install(DiagramDesigner designer, DiagramCanvas canvas, Disposable lifetime);
}
