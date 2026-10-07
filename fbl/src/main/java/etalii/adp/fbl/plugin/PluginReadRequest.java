package etalii.adp.fbl.plugin;

import java.util.List;

import etalii.adp.fbl.family.json.JsonValue;

/**
 * What {@code read} is asked: the files to read.
 *
 * @param args the arguments the binding passes its plugin, or null
 */
public record PluginReadRequest(List<PluginFile> files, JsonValue args) {

    public PluginReadRequest {
        files = List.copyOf(files);
    }
}
