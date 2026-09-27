package etalii.adp.core.settings;

import com.intellij.util.messages.Topic;

/** Told after the ADP page applied a change. Subscribers repaint or read again; they never change a document (FR-006). */
public interface AdpSettingsListener {

    Topic<AdpSettingsListener> TOPIC = Topic.create("ADP settings", AdpSettingsListener.class);

    void settingsChanged();
}
