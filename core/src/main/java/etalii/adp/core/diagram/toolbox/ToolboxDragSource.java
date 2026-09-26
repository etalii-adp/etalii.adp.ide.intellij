package etalii.adp.core.diagram.toolbox;

import java.awt.Point;
import java.util.function.Function;

import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDSource;

/**
 * Drags an element entry out of the toolbox through the platform's {@code DnDManager}. What
 * travels is a {@link ToolboxDrag}; the canvas's {@link ToolboxDropTarget} takes it.
 */
public final class ToolboxDragSource implements DnDSource {

    /** What a toolbox drag carries: the element type to add. */
    public record ToolboxDrag(String typeId) {
    }

    private final Function<Point, String> elementTypeAt;

    /** @param elementTypeAt the element type id listed at a point of the list, or {@code null} for none or a connection entry */
    ToolboxDragSource(Function<Point, String> elementTypeAt) {
        this.elementTypeAt = elementTypeAt;
    }

    @Override
    public boolean canStartDragging(DnDAction action, Point dragOrigin) {
        return elementTypeAt.apply(dragOrigin) != null;
    }

    @Override
    public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
        String typeId = elementTypeAt.apply(dragOrigin);
        return typeId == null ? null : start(typeId);
    }

    /** The drag of an entry, as {@link #startDragging} begins it. */
    DnDDragStartBean start(String typeId) {
        return new DnDDragStartBean(new ToolboxDrag(typeId));
    }
}
