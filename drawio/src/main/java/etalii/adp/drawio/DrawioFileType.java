package etalii.adp.drawio;

import com.intellij.icons.AllIcons;

import etalii.adp.core.AdpFileType;

/** "draw.io Diagram": {@code .drawio} files, which are text, so they open as such too (contracts/plugin-contributions.md). */
public final class DrawioFileType extends AdpFileType {

    public static final DrawioFileType INSTANCE = new DrawioFileType();

    private DrawioFileType() {
        super("draw.io Diagram", "draw.io diagram", "drawio", AllIcons.FileTypes.Diagram);
    }
}
