package etalii.adp.freemind.model;

/** Which side of the root a first-level branch is drawn on, as {@code POSITION} records it. */
public enum Side {
    LEFT, RIGHT;

    /** The {@code POSITION} value, or {@code null} for an absent or unknown one. */
    public static Side of(String position) {
        if ("left".equals(position)) {
            return LEFT;
        }
        return "right".equals(position) ? RIGHT : null;
    }

    public String attributeValue() {
        return this == LEFT ? "left" : "right";
    }
}
