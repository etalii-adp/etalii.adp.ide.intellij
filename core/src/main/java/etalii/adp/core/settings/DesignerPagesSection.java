package etalii.adp.core.settings;

import java.util.List;

import javax.swing.JComponent;

import com.intellij.openapi.options.Configurable;

/**
 * The designers' own pages under ADP (US4, FR-014): one per designer whose settings declarations
 * hold together, and none for a designer without settings. It shows nothing on the ADP page
 * itself; each child page applies on its own.
 */
public final class DesignerPagesSection implements SettingsSection {

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

    /** The designers' own labels are searched on their own pages (research R4). */
    @Override
    public List<String> searchableLabels() {
        return List.of();
    }

    @Override
    public List<Configurable> children() {
        return AdpDesigners.withPages().stream()
                .<Configurable>map(designer -> new DesignerSettingsConfigurable(designer.getEditorTypeId(), designer.designerInfo().name(), designer.settings()))
                .toList();
    }
}
