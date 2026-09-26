package etalii.adp.core.diagram.properties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import javax.swing.table.AbstractTableModel;

import etalii.adp.core.diagram.ConnectionType;
import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.EditorKind;
import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.PropertyDecl;
import etalii.adp.core.diagram.model.Connection;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;

/**
 * The ADP Properties rows (research R14, FR-022, FR-024): the declarations every selected item
 * shares, by id, grouped under a row per category in the order the categories first appear. A
 * value the items do not agree on is mixed and shows empty. A value the designer's rules keep for
 * one of the items is read-only. A placeholder shows only its key, read only. Column 0 is the label and column 1 the value in the file's notation; an edit of column 1
 * goes to the applier as the property id and the new value.
 */
public final class PropertyTableModel extends AbstractTableModel {

    /** The id of a placeholder's only row: its key. */
    public static final String KEY = "key";

    private static final PropertyDecl KEY_DECL = new PropertyDecl(KEY, "Key", "General", EditorKind.TEXT, true, null);

    /** One row: a category heading ({@code decl} is {@code null}) or a property. */
    public record Row(String category, PropertyDecl decl, String value, boolean mixed) {

        boolean heading() {
            return decl == null;
        }
    }

    private final BiConsumer<String, String> applier;
    private List<Row> rows = List.of();
    private List<Object> keys = List.of();
    private boolean editable;

    /** {@code applier} gets an edit's property id and value. */
    public PropertyTableModel(BiConsumer<String, String> applier) {
        this.applier = applier;
    }

    /** Show the selection's shared declarations; {@code editable} is false for a file that cannot be edited. */
    public void show(DiagramDefinition definition, Diagram diagram, List<Object> selection, boolean editable) {
        this.keys = List.copyOf(selection);
        this.editable = editable;
        this.rows = diagram == null ? List.of() : rows(definition, diagram, selection);
        fireTableDataChanged();
    }

    /** No rows. */
    public void clear() {
        keys = List.of();
        rows = List.of();
        fireTableDataChanged();
    }

    /** The items the rows are for. */
    public List<Object> keys() {
        return keys;
    }

    public Row row(int row) {
        return rows.get(row);
    }

    /** The rows for {@code selection}; none for an empty selection or one that is gone. */
    static List<Row> rows(DiagramDefinition definition, Diagram diagram, List<Object> selection) {
        List<List<PropertyDecl>> declared = new ArrayList<>();
        List<Map<String, String>> values = new ArrayList<>();
        boolean placeholder = false;
        for (Object key : selection) {
            Element element = diagram.element(key);
            Connection connection = diagram.connection(key);
            if (element != null) {
                ElementType type = definition.elementType(element.type());
                placeholder |= type == null;
                declared.add(type == null ? List.of() : type.properties());
                values.add(element.properties());
            } else if (connection != null) {
                ConnectionType type = definition.connectionType(connection.type());
                placeholder |= type == null;
                declared.add(type == null ? List.of() : type.properties());
                values.add(connection.properties());
            } else {
                return List.of();
            }
        }
        if (declared.isEmpty()) {
            return List.of();
        }
        if (placeholder) {
            String value = selection.size() == 1 ? selection.get(0).toString() : "";
            return List.of(new Row(KEY_DECL.category(), null, "", false), new Row(KEY_DECL.category(), KEY_DECL, value, selection.size() > 1));
        }
        Map<String, List<Row>> byCategory = new LinkedHashMap<>();
        for (PropertyDecl first : declared.get(0)) {
            PropertyDecl decl = first;
            boolean shared = true;
            for (int i = 1; i < declared.size() && shared; i++) {
                PropertyDecl other = declared.get(i).stream().filter(d -> d.id().equals(first.id())).findFirst().orElse(null);
                shared = other != null;
                if (shared && other.readOnly() && !decl.readOnly()) {
                    decl = other;
                }
            }
            if (!shared) {
                continue;
            }
            if (!decl.readOnly() && selection.stream().anyMatch(key -> !definition.rules().canSetProperty(diagram, key, first.id()).allowed())) {
                decl = new PropertyDecl(decl.id(), decl.label(), decl.category(), decl.editor(), true, decl.defaultValue());
            }
            String value = values.get(0).getOrDefault(decl.id(), "");
            boolean mixed = false;
            for (int i = 1; i < values.size() && !mixed; i++) {
                mixed = !Objects.equals(value, values.get(i).getOrDefault(decl.id(), ""));
            }
            byCategory.computeIfAbsent(decl.category(), c -> new ArrayList<>()).add(new Row(decl.category(), decl, mixed ? "" : value, mixed));
        }
        List<Row> rows = new ArrayList<>();
        byCategory.forEach((category, properties) -> {
            rows.add(new Row(category, null, "", false));
            rows.addAll(properties);
        });
        return rows;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public String getColumnName(int column) {
        return column == 0 ? "Name" : "Value";
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Row row = rows.get(rowIndex);
        if (columnIndex == 0) {
            return row.heading() ? row.category() : row.decl().label();
        }
        return row.value();
    }

    /** Only the value of a property row that is not read-only, in a file that can be edited. */
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        Row row = rows.get(rowIndex);
        return columnIndex == 1 && editable && !row.heading() && !row.decl().readOnly();
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        if (!isCellEditable(rowIndex, columnIndex)) {
            return;
        }
        Row row = rows.get(rowIndex);
        String text = value == null ? "" : value.toString();
        if (!row.mixed() && text.equals(row.value())) {
            return;
        }
        applier.accept(row.decl().id(), text);
    }
}
