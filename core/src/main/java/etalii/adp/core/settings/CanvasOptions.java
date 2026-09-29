package etalii.adp.core.settings;

/**
 * The user's canvas options for every diagram (FR-011).
 *
 * @param openingZoom the zoom a diagram opens at, 1.0 being 100%
 */
public record CanvasOptions(boolean showGrid, boolean snapToGrid, double openingZoom) {

    public static final CanvasOptions DEFAULTS = new CanvasOptions(false, true, 1.0);

    public boolean value(CanvasOption option) {
        return switch (option) {
        case SHOW_GRID -> showGrid;
        case SNAP_TO_GRID -> snapToGrid;
        };
    }
}
