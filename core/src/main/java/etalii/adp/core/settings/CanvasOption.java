package etalii.adp.core.settings;

/** A canvas option a tool's definition can fix (FR-012). Opening zoom is not one: a tool that must not zoom turns zoom off. */
public enum CanvasOption {
    SHOW_GRID("Show grid"),
    SNAP_TO_GRID("Snap to grid");

    private final String label;

    CanvasOption(String label) {
        this.label = label;
    }

    /** As the page shows it. */
    public String label() {
        return label;
    }
}
