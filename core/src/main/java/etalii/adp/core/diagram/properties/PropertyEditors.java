package etalii.adp.core.diagram.properties;

import java.awt.Color;
import java.awt.Component;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.AbstractCellEditor;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import javax.swing.text.JTextComponent;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.diagram.EditorKind;
import etalii.adp.core.diagram.EditorKind.Option;
import etalii.adp.core.diagram.properties.PropertyTableModel.Row;

/**
 * The cell renderer and editors of ADP Properties (research R14, FR-023): a text field for text
 * and numbers, an expandable field for multi-line text, a check box for yes/no, a combo box for a
 * choice and a colour panel for a colour, stored as {@code #RRGGBB}. Every editor refuses input its
 * kind does not accept when the edit is committed, before anything is applied: the editor stays
 * open with an error outline and the reason goes to the panel.
 */
final class PropertyEditors {

    /** Shown in place of a value the selected items do not share. */
    static final String DIFFERENT_VALUES = "different values";

    private PropertyEditors() {
    }

    /** A new editor for a value of {@code kind}; {@code problems} gets the reason of a refused input. */
    static TableCellEditor editor(EditorKind kind, Consumer<String> problems) {
        return switch (kind) {
        case EditorKind.Multiline _ -> {
            ExpandableTextField field = new ExpandableTextField(text -> List.of(text.split("\n", -1)), lines -> String.join("\n", lines));
            // a single-line field would turn the line breaks it is given into spaces
            field.getDocument().putProperty("filterNewlines", Boolean.FALSE);
            yield text(kind, field, problems);
        }
        case EditorKind.YesNo _ -> {
            JBCheckBox check = new JBCheckBox();
            KindEditor editor = new KindEditor(kind, check, () -> Boolean.toString(check.isSelected()), value -> check.setSelected("true".equals(value)),
                    problems);
            check.addActionListener(event -> editor.stopCellEditing());
            yield editor;
        }
        case EditorKind.Choice choice -> {
            ComboBox<Option> combo = new ComboBox<>(choice.options().toArray(Option[]::new));
            combo.setRenderer(new SimpleListCellRenderer<>() {
                @Override
                public void customize(@NotNull JList<? extends Option> list, Option option, int index, boolean selected, boolean hasFocus) {
                    setText(option == null ? "" : option.label());
                }
            });
            yield new KindEditor(kind, combo, () -> combo.getSelectedItem() instanceof Option option ? option.value() : "", value -> {
                combo.setSelectedIndex(-1);
                for (int i = 0; i < combo.getItemCount(); i++) {
                    if (combo.getItemAt(i).value().equals(value)) {
                        combo.setSelectedIndex(i);
                    }
                }
            }, problems);
        }
        case EditorKind.Colour _ -> {
            ColorPanel panel = new ColorPanel();
            KindEditor editor = new KindEditor(kind, panel, () -> hex(panel.getSelectedColor()), value -> panel.setSelectedColor(colour(value)),
                    problems);
            panel.addActionListener(event -> editor.stopCellEditing());
            yield editor;
        }
        case EditorKind.Text _ -> text(kind, new JBTextField(), problems);
        case EditorKind.Number _ -> text(kind, new JBTextField(), problems);
        };
    }

    private static KindEditor text(EditorKind kind, JBTextField field, Consumer<String> problems) {
        KindEditor editor = new KindEditor(kind, field, field::getText, field::setText, problems);
        field.addActionListener(event -> editor.stopCellEditing());
        return editor;
    }

    /** {@code #RRGGBB}, or empty for no colour. */
    static String hex(Color colour) {
        return colour == null ? "" : String.format("#%02X%02X%02X", colour.getRed(), colour.getGreen(), colour.getBlue());
    }

    /** The colour of a {@code #RRGGBB} value, or {@code null}. */
    static Color colour(String value) {
        return value != null && EditorKind.COLOR.validate(value) == null && !value.isEmpty() ? Color.decode(value) : null;
    }

    /** One editor component for one kind, read and written as the file's notation. */
    private static final class KindEditor extends AbstractCellEditor implements TableCellEditor {

        private final EditorKind kind;
        private final JComponent component;
        private final Supplier<String> read;
        private final Consumer<String> write;
        private final Consumer<String> problems;

        KindEditor(EditorKind kind, JComponent component, Supplier<String> read, Consumer<String> write, Consumer<String> problems) {
            this.kind = kind;
            this.component = component;
            this.read = read;
            this.write = write;
            this.problems = problems;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            showProblem(null);
            write.accept(value == null ? "" : value.toString());
            if (component instanceof JTextComponent text) {
                text.selectAll();
            }
            return component;
        }

        @Override
        public Object getCellEditorValue() {
            return read.get();
        }

        @Override
        public boolean stopCellEditing() {
            String problem = kind.validate(read.get());
            showProblem(problem);
            if (problem != null) {
                problems.accept(problem);
                return false;
            }
            return super.stopCellEditing();
        }

        @Override
        public void cancelCellEditing() {
            showProblem(null);
            super.cancelCellEditing();
        }

        private void showProblem(String problem) {
            component.putClientProperty("JComponent.outline", problem == null ? null : "error");
            component.setToolTipText(problem);
            component.repaint();
        }
    }

    /** Labels, greyed when read-only and bold for a category; values in their kind's terms, a hint when mixed. */
    static final class Renderer extends ColoredTableCellRenderer {

        @Override
        protected void customizeCellRenderer(@NotNull JTable table, Object value, boolean selected, boolean hasFocus, int rowIndex, int column) {
            Row row = ((PropertyTableModel) table.getModel()).row(rowIndex);
            if (row.decl() == null) {
                if (column == 0) {
                    append(row.category(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                }
                return;
            }
            boolean readOnly = row.decl().readOnly() || !table.getModel().isCellEditable(rowIndex, 1);
            SimpleTextAttributes attributes = readOnly ? SimpleTextAttributes.GRAYED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
            if (column == 0) {
                append(row.decl().label(), attributes);
                return;
            }
            if (row.mixed()) {
                append(DIFFERENT_VALUES, SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
                return;
            }
            String shown = row.value();
            switch (row.decl().editor()) {
            case EditorKind.Choice choice -> shown = choice.options().stream().filter(o -> o.value().equals(row.value())).map(Option::label).findFirst()
                    .orElse(row.value());
            case EditorKind.YesNo _ -> shown = row.value().isEmpty() ? "" : "true".equals(row.value()) ? "Yes" : "No";
            case EditorKind.Colour _ -> {
                Color colour = colour(row.value());
                if (colour != null) {
                    setIcon(new ColorIcon(JBUI.scale(12), colour));
                }
            }
            case EditorKind.Multiline _ -> shown = row.value().replace('\n', ' ');
            default -> {
                // text and numbers show as they are
            }
            }
            append(shown, attributes);
        }
    }
}
