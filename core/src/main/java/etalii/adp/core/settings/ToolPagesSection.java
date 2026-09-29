package etalii.adp.core.settings;

import java.util.List;

import javax.swing.JComponent;

import com.intellij.openapi.options.Configurable;

/**
 * The tools' own pages under ADP (US4, FR-014): one per tool whose settings declarations
 * hold together, and none for a tool without settings. It shows nothing on the ADP page
 * itself; each child page applies on its own.
 */
public final class ToolPagesSection implements SettingsSection {

    @Override
    public int order() {
        return 30;
    }

    @Override
    public JComponent createComponent() {
        return null;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public void apply() {
    }

    @Override
    public void reset() {
    }

    /** The tools' own labels are searched on their own pages (research R4). */
    @Override
    public List<String> searchableLabels() {
        return List.of();
    }

    @Override
    public List<Configurable> children() {
        return AdpTools.withPages().stream()
                .<Configurable>map(tool -> new ToolSettingsConfigurable(tool.getEditorTypeId(), tool.toolInfo().name(), tool.settings()))
                .toList();
    }
}
