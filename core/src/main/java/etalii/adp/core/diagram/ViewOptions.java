package etalii.adp.core.diagram;

import java.util.EnumMap;
import java.util.Map;

import etalii.adp.core.settings.CanvasOption;

/**
 * Pan, zoom and the grid per diagram (FR-025, spec 004 research R8). Whether the grid is shown
 * and snapped to is the user's choice, unless the definition fixes it.
 *
 * @param grid the grid spacing in unscaled pixels
 * @param backgroundPan whether a plain left drag on empty canvas pans instead of dragging a marquee
 * @param fixed canvas options this diagram keeps whatever the user chose
 */
public record ViewOptions(boolean zoom, boolean pan, int grid, boolean backgroundPan, Map<CanvasOption, Boolean> fixed) {

    public ViewOptions {
        fixed = Map.copyOf(fixed);
    }

    /** Defaults: zoom and pan on, a grid of 10, a drag on empty canvas drags a marquee, nothing fixed. */
    public static final class Builder {

        private boolean zoom = true;
        private boolean pan = true;
        private int grid = 10;
        private boolean backgroundPan;
        private final Map<CanvasOption, Boolean> fixed = new EnumMap<>(CanvasOption.class);

        Builder() {
        }

        public Builder zoom(boolean zoom) {
            this.zoom = zoom;
            return this;
        }

        public Builder pan(boolean pan) {
            this.pan = pan;
            return this;
        }

        public Builder grid(int grid) {
            this.grid = grid;
            return this;
        }

        /** Pan with a plain left drag on empty canvas; the marquee is then dragged with Ctrl or Shift held. */
        public Builder backgroundPan(boolean backgroundPan) {
            this.backgroundPan = backgroundPan;
            return this;
        }

        /** Keep {@code option} at {@code value} in this diagram, whatever the user chose. */
        public Builder fix(CanvasOption option, boolean value) {
            fixed.put(option, value);
            return this;
        }

        ViewOptions build() {
            return new ViewOptions(zoom, pan, grid, backgroundPan, fixed);
        }
    }
}