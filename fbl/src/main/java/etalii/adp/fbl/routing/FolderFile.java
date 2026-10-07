package etalii.adp.fbl.routing;

import java.nio.file.Path;

import etalii.adp.fbl.document.FileRule;

/** A file of a folder subject (FBL §10.2): its {@code /}-separated path relative to the folder and the file rule that selected it. */
public record FolderFile(String relativePath, Path fullPath, FileRule rule) {
}
