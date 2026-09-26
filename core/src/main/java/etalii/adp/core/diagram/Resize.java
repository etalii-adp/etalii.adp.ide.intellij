package etalii.adp.core.diagram;

/** Along which axes the user may resize an element. */
public enum Resize {
    NONE, HORIZONTAL, VERTICAL, BOTH;

    public boolean horizontal() {
        return this == HORIZONTAL || this == BOTH;
    }

    public boolean vertical() {
        return this == VERTICAL || this == BOTH;
    }
}