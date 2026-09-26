package etalii.adp.core.diagram;

/**
 * One text an element shows (FR-007): the value of a property at a position in its bounds.
 *
 * @param wrap wrap to the width; without it overflow is cut off with an ellipsis
 * @param editable editable in place on the canvas
 */
public record TextSlot(String id, String property, SlotPosition position, boolean wrap, Style style, boolean editable) {

    public enum Style {
        PLAIN, BOLD, ITALIC, SMALL
    }

    /** Defaults: shows the property named like the slot, centred, not wrapped, plain, not editable. */
    public static final class Builder {

        private final String id;
        private String property;
        private SlotPosition position = SlotPosition.CENTER;
        private boolean wrap;
        private Style style = Style.PLAIN;
        private boolean editable;

        Builder(String id) {
            this.id = id;
            this.property = id;
        }

        public Builder property(String property) {
            this.property = property;
            return this;
        }

        public Builder position(SlotPosition position) {
            this.position = position;
            return this;
        }

        public Builder wrap(boolean wrap) {
            this.wrap = wrap;
            return this;
        }

        public Builder style(Style style) {
            this.style = style;
            return this;
        }

        public Builder editable(boolean editable) {
            this.editable = editable;
            return this;
        }

        TextSlot build() {
            return new TextSlot(id, property, position, wrap, style, editable);
        }
    }
}