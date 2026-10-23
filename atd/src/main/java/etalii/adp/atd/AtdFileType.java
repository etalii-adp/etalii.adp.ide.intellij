package etalii.adp.atd;

import javax.swing.Icon;

import org.jetbrains.annotations.NotNull;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;

/**
 * "ATD": the file type of {@code .atd} tool descriptions (etalii.adp spec 035, T117). It is registered only in an IDE
 * with the platform's language server support, with the server that gives such a file its colours, findings,
 * completion and the rest; elsewhere an {@code .atd} opens as plain text.
 */
public final class AtdFileType extends LanguageFileType {

    public static final AtdFileType INSTANCE = new AtdFileType();

    private AtdFileType() {
        super(AtdLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "ATD";
    }

    @Override
    public @NotNull String getDescription() {
        return "ADP tool description";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "atd";
    }

    @Override
    public Icon getIcon() {
        return AllIcons.FileTypes.Text;
    }
}
