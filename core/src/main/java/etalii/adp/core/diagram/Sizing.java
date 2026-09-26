package etalii.adp.core.diagram;

/** How an element type gets its size (FR-008). Sizes are in unscaled pixels. */
public sealed interface Sizing {

    /** Fit the text slots within {@code maxWidth}, wrapping where the slot allows. */
    record Auto(int maxWidth) implements Sizing {
    }

    /** Always this size. */
    record Fixed(int width, int height) implements Sizing {
    }

    /** The size stored in the file or set by the user, never below the minimum. */
    record FromDiagram(int minWidth, int minHeight) implements Sizing {
    }

    static Sizing auto(int maxWidth) {
        return new Auto(maxWidth);
    }

    static Sizing fixed(int width, int height) {
        return new Fixed(width, height);
    }

    static Sizing fromDiagram(int minWidth, int minHeight) {
        return new FromDiagram(minWidth, minHeight);
    }
}