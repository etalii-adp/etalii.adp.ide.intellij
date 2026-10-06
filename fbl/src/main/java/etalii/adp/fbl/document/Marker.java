package etalii.adp.fbl.document;

import etalii.adp.fbl.family.json.JsonValue;

/**
 * A marker (FBL §12.2): exactly one of a root key, a first-line prefix or a pattern. The others are
 * null.
 *
 * @param rootValue the value the root key must have, or null when its presence is enough
 * @param lines how many lines a pattern looks at
 */
public record Marker(String rootKey, JsonValue rootValue, String firstLine, String pattern, int lines) {
}
