package etalii.adp.core.settings;

import org.jetbrains.annotations.NotNull;

import com.intellij.ide.ui.search.SearchableOptionContributor;
import com.intellij.ide.ui.search.SearchableOptionProcessor;

import etalii.adp.core.AdpEditorProvider;

/**
 * What the settings search finds the ADP page and the designers' own pages by (FR-001, research
 * R4): "ADP", every installed designer's name and every section's labels for the page, and each
 * designer's setting labels for its own page. Designers are known only at run time, so an index
 * built with the plug-in could not hold them.
 */
public final class AdpSearchableOptions extends SearchableOptionContributor {

    @Override
    public void processOptions(@NotNull SearchableOptionProcessor processor) {
        add(processor, AdpConfigurable.NAME, AdpConfigurable.ID, AdpConfigurable.NAME);
        for (DesignerInfo designer : AdpDesigners.all()) {
            add(processor, designer.name(), AdpConfigurable.ID, AdpConfigurable.NAME);
        }
        for (SettingsSection section : SettingsSection.EP_NAME.getExtensionList()) {
            for (String label : section.searchableLabels()) {
                add(processor, label, AdpConfigurable.ID, AdpConfigurable.NAME);
            }
        }
        for (AdpEditorProvider designer : AdpDesigners.withPages()) {
            String name = designer.designerInfo().name();
            for (DesignerSetting setting : designer.settings()) {
                add(processor, setting.label(), AdpConfigurable.pageId(designer.getEditorTypeId()), name);
            }
        }
    }

    private static void add(SearchableOptionProcessor processor, String text, String configurableId, String configurableName) {
        processor.addOptions(text, null, text, configurableId, configurableName, false);
    }
}
