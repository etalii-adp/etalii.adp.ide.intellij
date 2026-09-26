package etalii.adp.core.diagram;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A kind of shape a diagram contains (FR-005 to FR-011): its looks, texts, anchors, sizing,
 * permissions and the properties the panel shows. Built only through {@link DiagramDefinition.Builder}.
 */
public record ElementType(String id, String label, Outline outline, Tone tone, List<TextSlot> texts, Sizing sizing, List<Anchor> anchors,
        boolean selectable, boolean movable, boolean droppableOnto, Resize resize, List<PropertyDecl> properties) {

    public ElementType {
        texts = List.copyOf(texts);
        anchors = List.copyOf(anchors);
        properties = List.copyOf(properties);
    }

    /** The declared property, or {@code null}. */
    public PropertyDecl property(String propertyId) {
        return properties.stream().filter(p -> p.id().equals(propertyId)).findFirst().orElse(null);
    }

    /** The declared text slot, or {@code null}. */
    public TextSlot text(String slotId) {
        return texts.stream().filter(t -> t.id().equals(slotId)).findFirst().orElse(null);
    }

    /** The declared anchor, or {@code null}. */
    public Anchor anchor(String anchorId) {
        return anchors.stream().filter(a -> a.id().equals(anchorId)).findFirst().orElse(null);
    }

    /**
     * Defaults: the label is the id, a neutral rectangle, auto-sized to 200 wide, no texts or
     * anchors, selectable and movable, not droppable onto others, not resizable.
     */
    public static final class Builder {

        final String id;
        final List<String> duplicates = new ArrayList<>();
        private String label;
        private Outline outline = Outline.RECTANGLE;
        private Tone tone = Tone.NEUTRAL;
        final Map<String, TextSlot.Builder> texts = new LinkedHashMap<>();
        Sizing sizing = Sizing.auto(200);
        final Map<String, Anchor.Builder> anchors = new LinkedHashMap<>();
        boolean selectable = true;
        boolean movable = true;
        private boolean droppableOnto;
        Resize resize = Resize.NONE;
        final Map<String, PropertyDecl.Builder> properties = new LinkedHashMap<>();

        Builder(String id) {
            this.id = id;
            this.label = id;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder outline(Outline outline) {
            this.outline = outline;
            return this;
        }

        public Builder tone(Tone tone) {
            this.tone = tone;
            return this;
        }

        public Builder text(String slotId, Consumer<TextSlot.Builder> slot) {
            TextSlot.Builder builder = new TextSlot.Builder(slotId);
            slot.accept(builder);
            if (texts.putIfAbsent(slotId, builder) != null) {
                duplicates.add("text '" + slotId + "': is declared twice");
            }
            return this;
        }

        public Builder sizing(Sizing sizing) {
            this.sizing = sizing;
            return this;
        }

        public Builder anchor(String anchorId, Consumer<Anchor.Builder> anchor) {
            Anchor.Builder builder = new Anchor.Builder(anchorId);
            anchor.accept(builder);
            if (anchors.putIfAbsent(anchorId, builder) != null) {
                duplicates.add("anchor '" + anchorId + "': is declared twice");
            }
            return this;
        }

        public Builder selectable(boolean selectable) {
            this.selectable = selectable;
            return this;
        }

        public Builder movable(boolean movable) {
            this.movable = movable;
            return this;
        }

        public Builder droppableOnto(boolean droppableOnto) {
            this.droppableOnto = droppableOnto;
            return this;
        }

        public Builder resize(Resize resize) {
            this.resize = resize;
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

        ElementType build() {
            return new ElementType(id, label, outline, tone, texts.values().stream().map(TextSlot.Builder::build).toList(), sizing,
                    anchors.values().stream().map(Anchor.Builder::build).toList(), selectable, movable, droppableOnto, resize,
                    properties.values().stream().map(PropertyDecl.Builder::build).toList());
        }
    }
}