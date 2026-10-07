package etalii.adp.core.diagram.properties;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;

import etalii.adp.core.diagram.EditorKind;
import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.properties.PropertyTableModel.Row;
import etalii.adp.core.diagram.view.DiagramFileEditor;
import etalii.adp.core.diagram.view.PropertiesContent;

/**
 * The ADP Properties content (research R14, FR-021 to FR-024): a two-column table of the selected
 * items' shared properties, the same for every diagram. It follows the selected editor, that
 * diagram's selection and its model, rebuilding its rows on every change; a cell editor that is
 * open when its rows change is cancelled, so a value is never applied to an item that is gone. An
 * edit applies to every selected item as one command, through the diagram's commands.
 */
public final class PropertyPanel extends JPanel implements PropertiesContent, Disposable {

    public static final String NO_TOOL = "No ADP tool is active";
    public static final String NOTHING_SELECTED = "Nothing selected";

    private final Project project;
    private final PropertyTableModel model = new PropertyTableModel(this::apply);
    private final TableCellRenderer renderer = new PropertyEditors.Renderer();
    private final JBTable table = new JBTable(model) {
        @Override
        public TableCellEditor getCellEditor(int row, int column) {
            Row shown = model.row(row);
            return shown.decl() == null ? super.getCellEditor(row, column) : PropertyEditors.editor(shown.decl().editor(), PropertyPanel.this::problem);
        }

        @Override
        public TableCellRenderer getCellRenderer(int row, int column) {
            return renderer;
        }
    };
    private final Runnable rebuild = this::rebuild;
    private DiagramFileEditor tool;
    private boolean applying;
    private String lastProblem;

    public PropertyPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setShowGrid(false);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.getEmptyText().setText(NO_TOOL);
        add(ScrollPaneFactory.createScrollPane(table, true), BorderLayout.CENTER);

        project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                // the editor selected now, not the event's: selection events can arrive late
                refreshFromSelectedEditor();
            }

            @Override
            public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                refreshFromSelectedEditor();
            }
        });
        refreshFromSelectedEditor();
    }

    /** Follow the editor selected now. */
    public void refreshFromSelectedEditor() {
        follow(project.isDisposed() ? null : FileEditorManager.getInstance(project).getSelectedEditor());
    }

    /** The diagram whose selection is shown, or {@code null}. */
    @Override
    public DiagramFileEditor tool() {
        return tool;
    }

    /** The text shown while there are no rows. */
    public String emptyText() {
        return table.getEmptyText().getText();
    }

    /** The reason the last input was refused, or {@code null}. */
    public String lastProblem() {
        return lastProblem;
    }

    @Override
    public JTable table() {
        return table;
    }

    @Override
    public String propertyId(int row) {
        Row shown = model.row(row);
        return shown.decl() == null ? null : shown.decl().id();
    }

    @Override
    public EditorKind editor(int row) {
        Row shown = model.row(row);
        return shown.decl() == null ? null : shown.decl().editor();
    }

    @Override
    public boolean mixed(int row) {
        return model.row(row).mixed();
    }

    @Override
    public void dispose() {
        follow(null);
    }

    private void follow(FileEditor editor) {
        DiagramFileEditor next = editor instanceof TextEditorWithPreview composite && composite.getPreviewEditor() instanceof DiagramFileEditor d ? d
                : editor instanceof DiagramFileEditor d ? d : null;
        if (next == tool && next != null) {
            return;
        }
        if (tool != null) {
            tool.viewState().removeSelectionListener(rebuild);
            tool.removeModelListener(rebuild);
        }
        tool = next;
        if (next != null) {
            next.viewState().addSelectionListener(rebuild);
            next.addModelListener(rebuild);
        }
        rebuild();
    }

    /** Show the rows for the diagram's selection as it is now. */
    private void rebuild() {
        if (table.isEditing() && !applying) {
            table.getCellEditor().cancelCellEditing();
        }
        if (tool == null) {
            table.getEmptyText().setText(NO_TOOL);
            model.clear();
            return;
        }
        table.getEmptyText().setText(NOTHING_SELECTED);
        model.show(tool.definition(), tool.diagram(), tool.selection(), tool.isEditable());
    }

    /** An edit of a row: one command over every item shown. */
    private void apply(String propertyId, String value) {
        if (tool == null) {
            return;
        }
        applying = true;
        try {
            lastProblem = null;
            Verdict verdict = tool.commands().setProperty(model.keys(), propertyId, value);
            if (!verdict.allowed()) {
                problem(verdict.reason());
            }
        } finally {
            applying = false;
        }
    }

    private void problem(String reason) {
        lastProblem = reason;
    }
}
