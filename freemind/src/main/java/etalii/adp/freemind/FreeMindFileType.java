package etalii.adp.freemind;

import javax.swing.Icon;

import org.jetbrains.annotations.NotNull;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;

/**
 * "FreeMind Mind Map": the file type {@link FreeMindFileTypeDetector} gives files the sniffer
 * recognises, so they have a name and an icon and open as text. It is registered without
 * extensions, so {@code .mm} stays free for other file types, such as Objective-C++ in CLion
 * (FR-002, research R3).
 */
public final class FreeMindFileType implements FileType {

    public static final FreeMindFileType INSTANCE = new FreeMindFileType();

    private FreeMindFileType() {
    }

    @Override
    public @NotNull String getName() {
        return "FreeMind Mind Map";
    }

    @Override
    public @NotNull String getDescription() {
        return "FreeMind mind map";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "mm";
    }

    @Override
    public Icon getIcon() {
        return AllIcons.FileTypes.Diagram;
    }

    /** A map is text: the IDE's text editor, search and version control treat it as such. */
    @Override
    public boolean isBinary() {
        return false;
    }
}
