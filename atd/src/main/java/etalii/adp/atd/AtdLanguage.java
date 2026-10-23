package etalii.adp.atd;

import com.intellij.lang.Language;

/**
 * ATD, the ADP tool language (etalii.adp spec 035): the language of {@code .atd} tool descriptions. Everything the IDE
 * knows of it comes from its language server, {@code atd-lsp}; the plug-in parses nothing itself.
 */
public final class AtdLanguage extends Language {

    public static final AtdLanguage INSTANCE = new AtdLanguage();

    private AtdLanguage() {
        super("ATD");
    }
}
