package etalii.adp.core.diagram;

import java.awt.geom.Rectangle2D;

import etalii.adp.core.diagram.model.End;

/**
 * One difference between two successive diagrams (FR-004, research R8). Keys identify elements or
 * connections. A reconnect is {@link Disconnected} then {@link Connected} for the same key.
 */
public sealed interface DiagramChange {

    Object key();

    record Added(Object key) implements DiagramChange {
    }

    record Removed(Object key) implements DiagramChange {
    }

    /** The position changed, the size did not; also a new parent in a laid-out designer. */
    record Moved(Object key, Rectangle2D from, Rectangle2D to) implements DiagramChange {
    }

    record Resized(Object key, Rectangle2D from, Rectangle2D to) implements DiagramChange {
    }

    record Connected(Object key, End source, End target) implements DiagramChange {
    }

    record Disconnected(Object key) implements DiagramChange {
    }

    record PropertyChanged(Object key, String property, String from, String to) implements DiagramChange {
    }

    record SectorChanged(Object key, Object from, Object to) implements DiagramChange {
    }
}