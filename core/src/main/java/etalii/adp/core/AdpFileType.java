package etalii.adp.core;

import javax.swing.Icon;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.fileTypes.FileType;

/**
 * A text file type for a designer's own extension, so its files open as text too. A format
 * subclasses it with an {@code INSTANCE} field for its {@code fileType} registration.
 */
public abstract class AdpFileType implements FileType {

    private final String name;
    private final String description;
    private final String extension;
    private final Icon icon;

    protected AdpFileType(String name, String description, String extension, Icon icon) {
        this.name = name;
        this.description = description;
        this.extension = extension;
        this.icon = icon;
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public @NotNull String getDescription() {
        return description;
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return extension;
    }

    @Override
    public Icon getIcon() {
        return icon;
    }

    @Override
    public boolean isBinary() {
        return false;
    }
}