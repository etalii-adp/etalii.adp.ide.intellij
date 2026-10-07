package etalii.adp.fbl.plugin;

import java.util.List;

import etalii.adp.fbl.family.json.JsonValue;
import etalii.adp.fbl.plan.ModelChange;

/**
 * What {@code plan} is asked: the files as they are, the plugin's last reading of them, and the change to realise.
 *
 * @param args the arguments the binding passes its plugin, or null
 */
public record PluginPlanRequest(List<PluginFile> files, PluginReadResult last, ModelChange change, JsonValue args) {

    public PluginPlanRequest {
        files = List.copyOf(files);
    }
}
