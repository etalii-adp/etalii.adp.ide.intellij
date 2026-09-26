package etalii.adp.core.diagram;

/** A kind of swimlane or region (FR-026, research R16). */
public record SectorDecl(String id, String label, Orientation orientation, Space space) {

    /** Defaults: the label is the id, horizontal bands, in diagram space. */
    public static final class Builder {

        private final String id;
        private String label;
        private Orientation orientation = Orientation.HORIZONTAL;
        private Space space = Space.DIAGRAM;

        Builder(String id) {
            this.id = id;
            this.label = id;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder orientation(Orientation orientation) {
            this.orientation = orientation;
            return this;
        }

        public Builder space(Space space) {
            this.space = space;
            return this;
        }

        SectorDecl build() {
            return new SectorDecl(id, label, orientation, space);
        }
    }
}