package etalii.adp.core.diagram.view;

import java.awt.Point;
import java.util.List;

import javax.swing.JComponent;

/**
 * What the ADP Toolbox tool window's content offers besides the mouse: its entries, Enter on an
 * entry, and a finished drag. The test kit finds it in the tool window by this interface, so its
 * scripts use the real toolbox without knowing how it is built.
 */
public interface ToolboxContent {

    /** The type ids listed now, in toolbox order; empty when no diagram designer is selected. */
    List<String> entries();

    /** Enter on an entry: an element type is added at the centre of the visible canvas, a connection type is armed. */
    void activate(String typeId);

    /** A drag of the entry dropped on {@code target} at {@code point}, in the target's coordinates, as the drag and drop manager delivers it. */
    void dragTo(String typeId, JComponent target, Point point);
}
