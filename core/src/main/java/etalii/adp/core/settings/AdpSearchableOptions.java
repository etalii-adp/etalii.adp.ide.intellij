package etalii.adp.core.settings;

import org.jetbrains.annotations.NotNull;

import com.intellij.ide.ui.search.SearchableOptionContributor;
import com.intellij.ide.ui.search.SearchableOptionProcessor;

import etalii.adp.core.AdpEditorProvider;

/**
 * What the settings search finds the ADP page and the tools' own pages by (FR-001, research
 * R4): "ADP", every installed tool's name and every section's labels for the page, and each
 * tool's setting labels for its own page. Tools are known only at run time, so an index
 * built with the plug-in could not hold them.
 */
public final class AdpSearchableOptions extends SearchableOptionContributor {

    @Override
    public void processOptions(@NotNull SearchableOptionProcessor processor) {
        add(processor, AdpConfigurable.NAME, AdpConfigurable.ID, AdpConfigurable.NAME);
        for (ToolInfo tool : AdpTools.all()) {
            add(processor, tool.name(), AdpConfigurable.ID, AdpConfigurable.NAME);
        }
        for (SettingsSection section : SettingsSection.EP_NAME.getExtensionList()) {
            for (String label : section.searchableLabels()) {
                add(processor, label, AdpConfigurable.ID, AdpConfigurable.NAME);
            }
        }
        for (AdpEditorProvider tool : AdpTools.withPages()) {
            String name = tool.toolInfo().name();
            for (ToolSetting setting : tool.settings()) {
                add(processor, setting.label(), AdpConfigurable.pageId(tool.getEditorTypeId()), name);
            }
        }
    }

    private static void add(SearchableOptionProcessor processor, String text, String configurableId, String configurableName) {
        processor.addOptions(text, null, text, configurableId, configurableName, false);
    }
}
