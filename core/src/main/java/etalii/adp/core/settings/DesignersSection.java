package etalii.adp.core.settings;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;

import com.intellij.ide.DataManager;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;

/**
 * The designer list at the top of the ADP page (US1, US2, FR-007 to FR-010, FR-017): every
 * installed designer with its file types, version, origin and status, and an On check box.
 * Selecting a designer shows its problems, its conflicts and the canvas options it does not
 * follow. File types and default editors stay the platform's: a link opens its page (FR-004).
 */
public final class DesignersSection implements SettingsSection {

    static final String TITLE = "Designers";
    static final String FILE_TYPES_LINK = "File types and default editors…";
    /** The platform's File Types page. */
    static final String FILE_TYPES_ID = "preferences.fileTypes";

    private final DesignerTableModel model = new DesignerTableModel();
    private JBTable table;
    private JBTextArea detail;

    @Override
    public int order() {
        return 10;
    }

    @Override
    public JComponent createComponent() {
        table = new JBTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMaxWidth(JBUI.scale(40));
        table.getSelectionModel().addListSelectionListener(e -> showDetail());
        detail = new JBTextArea(4, 60);
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setOpaque(false);

        ActionLink fileTypes = new ActionLink(FILE_TYPES_LINK, (ActionListener) e -> openFileTypes((JComponent) e.getSource()));

        JBScrollPane list = new JBScrollPane(table);
        list.setPreferredSize(JBUI.size(600, 140));
        JPanel below = new JPanel(new BorderLayout());
        below.add(detail, BorderLayout.CENTER);
        below.add(fileTypes, BorderLayout.SOUTH);
        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(4)));
        panel.add(new TitledSeparator(TITLE), BorderLayout.NORTH);
        panel.add(list, BorderLayout.CENTER);
        panel.add(below, BorderLayout.SOUTH);
        return panel;
    }

    /** Within the Settings dialog, go to the platform's File Types page. */
    private static void openFileTypes(JComponent source) {
        Settings settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(source));
        if (settings != null) {
            settings.select(settings.find(FILE_TYPES_ID));
        }
    }

    @Override
    public boolean isModified() {
        return model.isModified();
    }

    @Override
    public void apply() {
        AdpSettings settings = AdpSettings.getInstance();
        model.on().forEach((id, on) -> settings.setOff(id, !on));
        reset();
    }

    @Override
    public void reset() {
        int selected = table == null ? -1 : table.getSelectedRow();
        model.load(AdpDesigners.all());
        if (table != null && selected >= 0 && selected < model.getRowCount()) {
            table.setRowSelectionInterval(selected, selected);
        }
        showDetail();
    }

    @Override
    public List<String> searchableLabels() {
        List<String> labels = new ArrayList<>(List.of(TITLE, FILE_TYPES_LINK));
        labels.addAll(DesignerTableModel.COLUMNS);
        return labels;
    }

    @Override
    public void disposeUIResources() {
        table = null;
        detail = null;
    }

    /** What the area below the list says about the selected designer. */
    String detail() {
        return detail == null ? "" : detail.getText();
    }

    private void showDetail() {
        if (detail == null) {
            return;
        }
        int row = table.getSelectedRow();
        detail.setText(row < 0 ? "" : describe(model.row(row), model.rows()));
        detail.setCaretPosition(0);
    }

    /** Problems, conflicts and unfollowed options of one designer, in sentences. */
    static String describe(DesignerInfo designer, List<DesignerInfo> all) {
        List<String> lines = new ArrayList<>();
        if (!designer.problems().isEmpty()) {
            lines.add(DesignerInfo.Status.NOT_LOADED.label() + ":");
            designer.problems().forEach(problem -> lines.add("  • " + problem));
        }
        if (!designer.conflictsWith().isEmpty()) {
            Map<String, DesignerInfo> byId = all.stream().collect(Collectors.toMap(DesignerInfo::id, d -> d));
            String others = designer.conflictsWith().stream().map(id -> byId.containsKey(id) ? byId.get(id).name() : id)
                    .collect(Collectors.joining(", "));
            // the platform asks the providers in extension order: the first that accepts a file is used
            DesignerInfo used = all.stream().filter(d -> d == designer || designer.conflictsWith().contains(d.id())).findFirst().orElse(designer);
            lines.add("Shares file types with " + others + ". For a file both can open, " + used.name()
                    + " is used; turn it off here to use the other instead.");
        }
        if (!designer.unfollowed().isEmpty()) {
            lines.add("Does not follow: " + designer.unfollowed().stream().sorted().map(CanvasOption::label).collect(Collectors.joining(", "))
                    + ". Its definition fixes them.");
        }
        return String.join("\n", lines);
    }
}
