package etalii.adp.core.diagram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A kind of line between anchors (FR-012 to FR-015). Line, dash, arrows and thickness are defaults
 * a file may override per connection. Built only through {@link DiagramDefinition.Builder}.
 *
 * @param routed routed around elements; orthogonal lines only (research R12)
 * @param userConnectable false when drawn from the diagram but never created or removed by a gesture
 */
public record ConnectionType(String id, String label, LineStyle line, Dash dash, float thickness, Tone tone, ArrowHead sourceArrow,
        ArrowHead targetArrow, Map<LabelSlot, LabelDecl> labels, boolean routed, boolean userConnectable, List<PropertyDecl> properties) {

    /** A label slot: the property it shows and whether it is edited in place. */
    public record LabelDecl(String property, boolean editable) {
    }

    public ConnectionType {
        labels = Collections.unmodifiableMap(labels.isEmpty() ? new EnumMap<>(LabelSlot.class) : new EnumMap<>(labels));
        properties = List.copyOf(properties);
    }

    /** The declared property, or {@code null}. */
    public PropertyDecl property(String propertyId) {
        return properties.stream().filter(p -> p.id().equals(propertyId)).findFirst().orElse(null);
    }

    /**
     * Defaults: the label is the id, a straight solid neutral line of thickness 1, no arrows, no
     * labels, not routed, connectable by the user.
     */
    public static final class Builder {

        final String id;
        final List<String> duplicates = new ArrayList<>();
        private String label;
        LineStyle line = LineStyle.STRAIGHT;
        private Dash dash = Dash.SOLID;
        float thickness = 1f;
        private Tone tone = Tone.NEUTRAL;
        private ArrowHead sourceArrow = ArrowHead.NONE;
        private ArrowHead targetArrow = ArrowHead.NONE;
        final Map<LabelSlot, LabelDecl> labels = new EnumMap<>(LabelSlot.class);
        boolean routed;
        boolean userConnectable = true;
        final Map<String, PropertyDecl.Builder> properties = new LinkedHashMap<>();

        Builder(String id) {
            this.id = id;
            this.label = id;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder line(LineStyle line) {
            this.line = line;
            return this;
        }

        public Builder dash(Dash dash) {
            this.dash = dash;
            return this;
        }

        public Builder thickness(float thickness) {
            this.thickness = thickness;
            return this;
        }

        public Builder tone(Tone tone) {
            this.tone = tone;
            return this;
        }

        public Builder arrows(ArrowHead source, ArrowHead target) {
            this.sourceArrow = source;
            this.targetArrow = target;
            return this;
        }

        public Builder label(LabelSlot slot, String property, boolean editable) {
            labels.put(slot, new LabelDecl(property, editable));
            return this;
        }

        public Builder routed(boolean routed) {
            this.routed = routed;
            return this;
        }

        public Builder userConnectable(boolean userConnectable) {
            this.userConnectable = userConnectable;
            return this;
        }

        public Builder property(String propertyId, Consumer<PropertyDecl.Builder> property) {
            PropertyDecl.Builder builder = new PropertyDecl.Builder(propertyId);
            property.accept(builder);
            if (properties.putIfAbsent(propertyId, builder) != null) {
                duplicates.add("property '" + propertyId + "': is declared twice");
            }
            return this;
        }

        ConnectionType build() {
            return new ConnectionType(id, label, line, dash, thickness, tone, sourceArrow, targetArrow, labels, routed, userConnectable,
                    properties.values().stream().map(PropertyDecl.Builder::build).toList());
        }
    }
}