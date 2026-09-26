package etalii.adp.core.diagram;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named attachment point of an element type (FR-009, FR-010, research R11).
 *
 * @param fx relative position on the bounds, 0 left to 1 right; ignored for a perimeter anchor
 * @param fy relative position on the bounds, 0 top to 1 bottom; ignored for a perimeter anchor
 * @param perimeter a floating anchor: connections attach where the line meets the outline
 * @param visible drawn as a marker; invisible anchors still attach connections
 * @param accepts connection type id to the ends it takes
 */
public record Anchor(String id, double fx, double fy, boolean perimeter, boolean visible, Map<String, Direction> accepts) {

    public Anchor {
        accepts = Collections.unmodifiableMap(new LinkedHashMap<>(accepts));
    }

    /** True when the anchor takes the end of {@code connectionType} that plays {@code role} ({@code IN} or {@code OUT}). */
    public boolean accepts(String connectionType, Direction role) {
        Direction direction = accepts.get(connectionType);
        return direction != null && direction.allows(role);
    }

    /** Defaults: the centre, visible, accepting nothing. */
    public static final class Builder {

        private final String id;
        private double fx = 0.5;
        private double fy = 0.5;
        private boolean perimeter;
        private boolean visible = true;
        private final Map<String, Direction> accepts = new LinkedHashMap<>();

        Builder(String id) {
            this.id = id;
        }

        public Builder at(double fx, double fy) {
            this.fx = fx;
            this.fy = fy;
            this.perimeter = false;
            return this;
        }

        public Builder perimeter() {
            this.perimeter = true;
            return this;
        }

        public Builder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public Builder accepts(String connectionType, Direction direction) {
            accepts.put(connectionType, direction);
            return this;
        }

        Anchor build() {
            return new Anchor(id, fx, fy, perimeter, visible, accepts);
        }
    }
}