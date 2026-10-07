package etalii.adp.fbl.expression;

/** Where a CEL expression appears, which decides the variables it may use (FBL §2.4). */
public enum CelContext {

    /** A rule of a yaml, json or xml binding: entry, parent, path, line, registration. */
    TREE,

    /** A rule of a lines or blocks binding: entry, parent, groups, line, registration. */
    LINES,

    /** {@code insert.when}: attributes. */
    INSERT,
}
