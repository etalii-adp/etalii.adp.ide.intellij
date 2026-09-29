package etalii.adp.core.settings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.table.AbstractTableModel;

/**
 * The tool list's rows (FR-007): one per installed tool, with the On column editable and
 * kept here until the page applies (FR-002).
 */
final class ToolTableModel extends AbstractTableModel {

    static final List<String> COLUMNS = List.of("On", "Name", "File types", "Version", "Origin", "Status");

    private List<ToolInfo> rows = List.of();
    private final Map<String, Boolean> on = new HashMap<>();

    /** Show these tools, each on or off as stored. */
    void load(List<ToolInfo> tools) {
        rows = List.copyOf(tools);
        on.clear();
        tools.forEach(tool -> on.put(tool.id(), tool.on()));
        fireTableDataChanged();
    }

    List<ToolInfo> rows() {
        return rows;
    }

    ToolInfo row(int index) {
        return rows.get(index);
    }

    /** Each tool's On column as the user left it. */
    Map<String, Boolean> on() {
        return Map.copyOf(on);
    }

    boolean isModified() {
        return rows.stream().anyMatch(tool -> on.get(tool.id()) != tool.on());
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
        ToolInfo tool = rows.get(row);
        return switch (column) {
        case 0 -> on.get(tool.id());
        case 1 -> tool.name();
        case 2 -> tool.fileTypes().stream().map(type -> "." + type).collect(Collectors.joining(", "));
        case 3 -> tool.version();
        case 4 -> tool.origin().describe();
        case 5 -> tool.status().label();
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
