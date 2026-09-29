package etalii.adp.core.settings;

import java.util.List;

import javax.swing.JComponent;

import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.Configurable;

/**
 * One part of the ADP page, registered from its own descriptor fragment (research R1). The page
 * owns the dialog contract and asks each section in {@link #order()}. Tool engineers do not
 * implement it.
 */
public interface SettingsSection {

    ExtensionPointName<SettingsSection> EP_NAME = ExtensionPointName.create("etalii.adp.settingsSection");

    /** Tools 10, canvas 20, tool pages 30. */
    int order();

    /** {@code null} for a section that only adds child pages. */
    @Nullable
    JComponent createComponent();

    boolean isModified();

    /** Writes {@link AdpSettings}; the page publishes {@link AdpSettingsListener#TOPIC} once. */
    void apply();

    void reset();

    /** The labels the settings search finds the ADP page by. */
    List<String> searchableLabels();

    default List<Configurable> children() {
        return List.of();
    }

    default void disposeUIResources() {
    }
}
