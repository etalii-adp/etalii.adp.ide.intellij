package etalii.adp.core.settings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.table.AbstractTableModel;

/**
 * The designer list's rows (FR-007): one per installed designer, with the On column editable and
 * kept here until the page applies (FR-002).
 */
final class DesignerTableModel extends AbstractTableModel {

    static final List<String> COLUMNS = List.of("On", "Name", "File types", "Version", "Origin", "Status");

    private List<DesignerInfo> rows = List.of();
    private final Map<String, Boolean> on = new HashMap<>();

    /** Show these designers, each on or off as stored. */
    void load(List<DesignerInfo> designers) {
        rows = List.copyOf(designers);
        on.clear();
        designers.forEach(designer -> on.put(designer.id(), designer.on()));
        fireTableDataChanged();
    }

    List<DesignerInfo> rows() {
        return rows;
    }

    DesignerInfo row(int index) {
        return rows.get(index);
    }

    /** Each designer's On column as the user left it. */
    Map<String, Boolean> on() {
        return Map.copyOf(on);
    }

    boolean isModified() {
        return rows.stream().anyMatch(designer -> on.get(designer.id()) != designer.on());
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.size();
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS.get(column);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == 0 ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return column == 0;
    }

    @Override
    public Object getValueAt(int row, int column) {
        DesignerInfo designer = rows.get(row);
        return switch (column) {
        case 0 -> on.get(designer.id());
        case 1 -> designer.name();
        case 2 -> designer.fileTypes().stream().map(type -> "." + type).collect(Collectors.joining(", "));
        case 3 -> designer.version();
        case 4 -> designer.origin().describe();
        case 5 -> designer.status().label();
        default -> throw new IndexOutOfBoundsException(column);
        };
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        if (column == 0 && value instanceof Boolean checked) {
            on.put(rows.get(row).id(), checked);
            fireTableCellUpdated(row, column);
        }
    }
}
