package etalii.adp.fbl.plugin;

import etalii.adp.fbl.Splice;

/** One splice of a plugin's plan, in the file it names (empty for a file body). */
public record PluginSplice(String file, Splice splice) {
}
