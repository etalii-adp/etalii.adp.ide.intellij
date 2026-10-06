package etalii.adp.fbl.document;

import etalii.adp.fbl.family.json.JsonValue;

/**
 * The persistence plugin that reads a binding's body (FBL §11).
 *
 * @param version the version the binding asks for, or null
 * @param args the arguments the binding passes, or null
 */
public record PluginReader(String plugin, String version, JsonValue args) {
}
