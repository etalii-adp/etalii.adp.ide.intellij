package etalii.adp.core.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBIntSpinner;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;

/**
 * One designer's own settings page under ADP (US4, FR-014, research R6), built from what the
 * designer declares: a check box for yes/no, a spinner for a number, a list for a choice. Values
 * are stored in {@link AdpSettings} under the designer's id, so they outlast the designer being off
 * or uninstalled (FR-015). Changes are kept until Apply.
 */
public final class DesignerSettingsConfigurable implements SearchableConfigurable {

    private final String designerId;
    private final String designerName;
    private final List<DesignerSetting> settings;
    private final Map<DesignerSetting, JComponent> editors = new LinkedHashMap<>();

    public DesignerSettingsConfigurable(String designerId, String designerName, List<DesignerSetting> settings) {
        this.designerId = designerId;
        this.designerName = designerName;
        this.settings = List.copyOf(settings);
    }

    @Override
    public @NotNull String getId() {
        return AdpConfigurable.pageId(designerId);
    }

    @Override
    public String getDisplayName() {
        return designerName;
    }

    @Override
    public JComponent createComponent() {
        FormBuilder form = FormBuilder.createFormBuilder();
        for (DesignerSetting setting : settings) {
            JComponent editor = switch (setting.kind()) {
            case YES_NO -> new JBCheckBox(setting.label());
            case NUMBER -> new JBIntSpinner(Integer.parseInt(setting.defaultValue()), setting.min(), setting.max());
            case CHOICE -> new ComboBox<>(setting.choices().toArray(String[]::new));
            };
            editors.put(setting, editor);
            if (setting.kind() == DesignerSetting.Kind.YES_NO) {
                form.addComponent(editor);
            } else {
                form.addLabeledComponent(setting.label() + ":", editor);
            }
        }
        return form.addComponentFillVertically(new JPanel(), 0).getPanel();
    }

    @Override
    public boolean isModified() {
        AdpSettings stored = AdpSettings.getInstance();
        return editors.keySet().stream().anyMatch(setting -> !shown(setting).equals(stored.value(designerId, setting)));
    }

    @Override
    public void apply() {
        if (!isModified()) {
            return;
        }
        AdpSettings stored = AdpSettings.getInstance();
        editors.keySet().forEach(setting -> stored.setValue(designerId, setting, shown(setting)));
        AdpConfigurable.publish();
    }

    @Override
    public void reset() {
        AdpSettings stored = AdpSettings.getInstance();
        editors.forEach((setting, editor) -> {
            String value = stored.value(designerId, setting);
            switch (editor) {
            case JBCheckBox box -> box.setSelected(Boolean.parseBoolean(value));
            case JBIntSpinner spinner -> spinner.setNumber(Integer.parseInt(value));
            case ComboBox<?> choice -> choice.setSelectedItem(value);
            default -> throw new IllegalStateException(editor.getClass().getName());
            }
        });
    }

    @Override
    public void disposeUIResources() {
        editors.clear();
    }

    /** The value an editor shows, as stored text. */
    private String shown(DesignerSetting setting) {
        return switch (editors.get(setting)) {
        case JBCheckBox box -> Boolean.toString(box.isSelected());
        case JBIntSpinner spinner -> Integer.toString(spinner.getNumber());
        case ComboBox<?> choice -> String.valueOf(choice.getSelectedItem());
        default -> throw new IllegalStateException(setting.key());
        };
    }
}
