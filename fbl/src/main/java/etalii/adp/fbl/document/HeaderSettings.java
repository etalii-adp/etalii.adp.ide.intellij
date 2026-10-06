package etalii.adp.fbl.document;

import etalii.adp.fbl.family.json.JsonValue;

/**
 * The header a body carries (FBL §4.1.3): a key with its value in a tree, or a line. The other is null.
 *
 * @param value the value the key must have, or null when its presence is enough
 */
public record HeaderSettings(String key, JsonValue value, String line, boolean required) {
}
