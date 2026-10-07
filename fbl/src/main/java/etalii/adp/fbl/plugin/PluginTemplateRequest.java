package etalii.adp.fbl.plugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** What {@code template} is asked: the new file's name and the values of the placeholders of FBL §13. */
public record PluginTemplateRequest(String name, Map<String, String> placeholders) {

    public PluginTemplateRequest {
        placeholders = Collections.unmodifiableMap(new LinkedHashMap<>(placeholders));
    }
}
