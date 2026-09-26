package etalii.adp.core.diagram;

/** Which ends of a connection type an anchor accepts: its source ({@code OUT}), its target ({@code IN}) or both. */
public enum Direction {
    IN, OUT, BOTH;

    /** True when an anchor declared with this direction may take the end playing {@code role} ({@code IN} or {@code OUT}). */
    public boolean allows(Direction role) {
        return this == BOTH || this == role;
    }
}