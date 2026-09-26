package etalii.adp.core.diagram;

/**
 * Pan and zoom per designer (FR-025).
 *
 * @param grid the move and resize snap in unscaled pixels; 0 is no snap
 */
public record ViewOptions(boolean zoom, boolean pan, int grid) {

    /** Defaults: zoom and pan on, no snap. */
    public static final class Builder {

        private boolean zoom = true;
        private boolean pan = true;
        private int grid;

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

        ViewOptions build() {
            return new ViewOptions(zoom, pan, grid);
        }
    }
}