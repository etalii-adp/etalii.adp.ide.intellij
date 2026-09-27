package etalii.adp.core.settings;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.util.ui.JBUI;

/**
 * Settings > Tools > ADP (FR-001, FR-002, research R1). It owns the dialog contract: modified when
 * any section is, Apply writes the changed sections and tells open designers once, Reset and
 * Cancel write nothing. What the page shows comes from the registered {@link SettingsSection}s, in
 * their order, and so do its child pages.
 */
public final class AdpConfigurable implements SearchableConfigurable, Configurable.Composite {

    public static final String ID = "etalii.adp.settings";
    public static final String NAME = "ADP";

    private final List<SettingsSection> sections = SettingsSection.EP_NAME.getExtensionList().stream()
            .sorted(Comparator.comparingInt(SettingsSection::order)).toList();
    private Configurable[] children;

    /** The id of a designer's own page, under this one. */
    public static String pageId(String designerId) {
        return ID + "." + designerId;
    }

    /** Tell open designers that settings changed, once per apply. */
    static void publish() {
        ApplicationManager.getApplication().getMessageBus().syncPublisher(AdpSettingsListener.TOPIC).settingsChanged();
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return NAME;
    }

    @Override
    public JComponent createComponent() {
        JPanel page = new JPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        for (SettingsSection section : sections) {
            JComponent component = section.createComponent();
            if (component != null) {
                component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
                component.setBorder(JBUI.Borders.emptyBottom(12));
                page.add(component);
            }
        }
        JPanel top = new JPanel(new BorderLayout());
        top.add(page, BorderLayout.NORTH);
        return top;
    }

    @Override
    public boolean isModified() {
        return sections.stream().anyMatch(SettingsSection::isModified);
    }

    @Override
    public void apply() {
        boolean changed = false;
        for (SettingsSection section : sections) {
            if (section.isModified()) {
                section.apply();
                changed = true;
            }
        }
        if (changed) {
            publish();
        }
    }

    @Override
    public void reset() {
        sections.forEach(SettingsSection::reset);
    }

    @Override
    public void disposeUIResources() {
        sections.forEach(SettingsSection::disposeUIResources);
    }

    @Override
    public Configurable @NotNull [] getConfigurables() {
        if (children == null) {
            List<Configurable> all = new ArrayList<>();
            sections.forEach(section -> all.addAll(section.children()));
            children = all.toArray(Configurable[]::new);
        }
        return children;
    }
}
