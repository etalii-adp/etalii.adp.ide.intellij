package etalii.adp.core.diagram;

import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.util.Map;
import java.util.function.Function;

import etalii.adp.core.ViewState;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;

/** Positions for designers whose layout is computed rather than stored (research R7). */
@FunctionalInterface
public interface DiagramLayout {

    /** Bounds for every element to show; elements left out are not drawn. {@code measure} gives each element's auto size. */
    Map<Object, Rectangle2D> layout(Diagram diagram, ViewState view, Function<Element, Dimension2D> measure);
}