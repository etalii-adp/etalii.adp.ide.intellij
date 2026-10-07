package etalii.adp.fbl.plugin;

/** One file a plugin reads: the body itself ({@code relativePath} empty) or a file of a folder subject. */
public record PluginFile(String relativePath, byte[] bytes) {
}
