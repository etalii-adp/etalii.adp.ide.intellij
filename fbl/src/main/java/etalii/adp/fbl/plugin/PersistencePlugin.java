package etalii.adp.fbl.plugin;

import java.util.List;

/**
 * The persistence plugin contract of FBL §11.2, as data exchanged through an interface so that a
 * host binds it to its own plugin mechanism. A plugin reads and plans; it never writes files, keeps
 * no undo history and stores no view data (FBL §11.4). No plugin is implemented in this library.
 */
public interface PersistencePlugin {

    /** The plugin's id, as a binding's {@code reader.plugin} names it. */
    String id();

    /** {@code read}: the model of a body, never failing on content. */
    PluginReadResult read(PluginReadRequest request);

    /** {@code plan}: the splices that realise one model change, or the refusal the host shows. */
    PluginPlanResult plan(PluginPlanRequest request);

    /** {@code template}: the bytes of a new body, asked only when the binding has no {@code template.text}. */
    byte[] template(PluginTemplateRequest request);

    /** {@code watch}: the paths a folder subject's reading depends on beyond its file rules. */
    List<String> watch(PluginReadResult last);
}
