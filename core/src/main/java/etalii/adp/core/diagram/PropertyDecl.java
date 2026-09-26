package etalii.adp.core.diagram;

/**
 * One value of an element or connection type that the property panel shows (FR-022).
 *
 * @param category groups rows in the panel
 * @param defaultValue the value a new item gets, or {@code null} for none
 */
public record PropertyDecl(String id, String label, String category, EditorKind editor, boolean readOnly, String defaultValue) {

    /** Defaults: the label is the id, the category "General", a text editor, editable, no default value. */
    public static final class Builder {

        private final String id;
        private String label;
        private String category = "General";
        private EditorKind editor = EditorKind.TEXT;
        private boolean readOnly;
        private String defaultValue;

        Builder(String id) {
            this.id = id;
            this.label = id;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder editor(EditorKind editor) {
            this.editor = editor;
            return this;
        }

        public Builder readOnly(boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        public Builder defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        PropertyDecl build() {
            return new PropertyDecl(id, label, category, editor, readOnly, defaultValue);
        }
    }
}