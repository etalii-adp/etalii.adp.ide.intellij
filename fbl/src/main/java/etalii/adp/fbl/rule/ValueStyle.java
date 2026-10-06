package etalii.adp.fbl.rule;

/** How a value is written: FBL §6.3 keeps the style of a replaced value when the new value fits it. */
public enum ValueStyle {
    PLAIN,
    SINGLE,
    DOUBLE,
    LITERAL,
    FOLDED,
    FLOW_SEQUENCE,
    FLOW_MAPPING,
    BLOCK,
    EMPTY,
    JSON_STRING,
    JSON_OTHER,
}
