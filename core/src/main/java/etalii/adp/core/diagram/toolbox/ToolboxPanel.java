package etalii.adp.core.diagram.toolbox;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import org.jetbrains.annotations.NotNull;

import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.diagram.ConnectionType;
import etalii.adp.core.diagram.Dash;
import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.toolbox.ToolboxDragSource.ToolboxDrag;
import etalii.adp.core.diagram.view.DiagramFileEditor;
import etalii.adp.core.diagram.view.ToolboxContent;

/**
 * The ADP Toolbox's content (research R13, FR-016): the selected diagram's toolbox
 * entries in declared order, grouped into elements and connections, each with its type's outline
 * and tone, or line and dash, as its icon. It follows the selected editor. An element entry is
 * dragged onto the canvas, or added at the centre of the visible canvas with Enter; a connection
 * entry, clicked or entered, arms its type for the next drag between anchors.
 */
public final class ToolboxPanel extends JPanel implements ToolboxContent, Disposable {

    public static final String EMPTY_TEXT = "Open an ADP diagram to see its toolbox";

    static final String ELEMENTS = "Elements";
    static final String CONNECTIONS = "Connections";

    private static final String ACTIVATE = "etalii.adp.toolbox.activate";

    /** One listed type. */
    record Entry(String typeId, String label, String group, Icon icon) {
    }

    private final Project project;
    private final CollectionListModel<Entry> model = new CollectionListModel<>();
    private final JBList<Entry> list = new JBList<>(model);
    private final ToolboxDragSource dragSource;
    private DiagramFileEditor tool;

    public ToolboxPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        list.getEmptyText().setText(EMPTY_TEXT);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        add(ScrollPaneFactory.createScrollPane(list, true), BorderLayout.CENTER);

        dragSource = new ToolboxDragSource(this::elementTypeAt);
        DnDManager.getInstance().registerSource(dragSource, list, this);
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ACTIVATE);
        list.getActionMap().put(ACTIVATE, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Entry entry = list.getSelectedValue();
                if (entry != null) {
                    activate(entry.typeId());
                }
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Entry entry = entryAt(e.getPoint());
                if (entry != null && entry.group().equals(CONNECTIONS)) {
                    activate(entry.typeId());
                }
            }
        });

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

    /** The diagram whose toolbox is listed, or {@code null}. */
    public DiagramFileEditor tool() {
        return tool;
    }

    /** True while no diagram is selected and the empty state is shown. */
    public boolean emptyStateShown() {
        return model.isEmpty() && EMPTY_TEXT.equals(list.getEmptyText().getText());
    }

    /** The icon listed for an entry, or {@code null} when it is not listed. */
    public Icon iconOf(String typeId) {
        Entry entry = entry(typeId);
        return entry == null ? null : entry.icon();
    }

    /** The group an entry is listed under, or {@code null} when it is not listed. */
    public String groupOf(String typeId) {
        Entry entry = entry(typeId);
        return entry == null ? null : entry.group();
    }

    JBList<Entry> entryList() {
        return list;
    }

    @Override
    public List<String> entries() {
        return model.getItems().stream().map(Entry::typeId).toList();
    }

    @Override
    public void activate(String typeId) {
        if (tool == null || entry(typeId) == null) {
            return;
        }
        if (tool.definition().connectionType(typeId) != null) {
            tool.armConnectionType(typeId);
            list.repaint();
        } else {
            ToolboxDropTarget.addAtCentre(tool, typeId);
        }
    }

    @Override
    public void dragTo(String typeId, JComponent target, Point point) {
        ToolboxDropTarget drop = ToolboxDropTarget.of(target);
        Entry entry = entry(typeId);
        if (drop == null || entry == null || !entry.group().equals(ELEMENTS)) {
            return;
        }
        DnDDragStartBean bean = dragSource.start(typeId);
        if (bean.getAttachedObject() instanceof ToolboxDrag drag) {
            drop.dropAt(drag.typeId(), point);
        }
    }

    @Override
    public void dispose() {
        tool = null;
    }

    private void follow(FileEditor editor) {
        DiagramFileEditor next = editor instanceof TextEditorWithPreview composite && composite.getPreviewEditor() instanceof DiagramFileEditor d ? d
                : editor instanceof DiagramFileEditor d ? d : null;
        if (next == tool && (next != null || model.isEmpty())) {
            return;
        }
        tool = next;
        model.replaceAll(next == null ? List.of() : entriesOf(next.definition()));
    }

    private static List<Entry> entriesOf(DiagramDefinition definition) {
        List<Entry> elements = new ArrayList<>();
        List<Entry> connections = new ArrayList<>();
        for (String typeId : definition.toolbox()) {
            ElementType element = definition.elementType(typeId);
            if (element != null) {
                elements.add(new Entry(typeId, element.label(), ELEMENTS, new TypeIcon(element, null)));
            } else {
                ConnectionType connection = definition.connectionType(typeId);
                connections.add(new Entry(typeId, connection.label(), CONNECTIONS, new TypeIcon(null, connection)));
            }
        }
        List<Entry> entries = new ArrayList<>(elements);
        entries.addAll(connections);
        return entries;
    }

    private Entry entry(String typeId) {
        return model.getItems().stream().filter(e -> e.typeId().equals(typeId)).findFirst().orElse(null);
    }

    private Entry entryAt(Point point) {
        int index = list.locationToIndex(point);
        Rectangle cell = index < 0 ? null : list.getCellBounds(index, index);
        return cell == null || !cell.contains(point) ? null : model.getElementAt(index);
    }

    private String elementTypeAt(Point point) {
        Entry entry = entryAt(point);
        return entry == null || !entry.group().equals(ELEMENTS) ? null : entry.typeId();
    }

    /** An entry's row, under a separator naming its group when it is the group's first. */
    private final class Renderer implements ListCellRenderer<Entry> {

        private final ColoredListCellRenderer<Entry> row = new ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends Entry> list, Entry entry, int index, boolean selected, boolean hasFocus) {
                setIcon(entry.icon());
                boolean armed = tool != null && entry.typeId().equals(tool.armedConnectionType());
                append(entry.label(), armed ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
                if (armed) {
                    append("  armed", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
            }
        };

        @Override
        public Component getListCellRendererComponent(JList<? extends Entry> list, Entry entry, int index, boolean selected, boolean hasFocus) {
            Component component = row.getListCellRendererComponent(list, entry, index, selected, hasFocus);
            boolean first = index == 0 || !model.getElementAt(index - 1).group().equals(entry.group());
            if (!first) {
                return component;
            }
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(list.getBackground());
            panel.add(new TitledSeparator(entry.group()), BorderLayout.NORTH);
            panel.add(component, BorderLayout.CENTER);
            return panel;
        }
    }

    /** An element type's outline in its tone, or a connection type's line in its tone and dash. */
    static final class TypeIcon implements Icon {

        private static final int SIZE = 16;

        private final ElementType element;
        private final ConnectionType connection;

        TypeIcon(ElementType element, ConnectionType connection) {
            this.element = element;
            this.connection = connection;
        }

        @Override
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double size = JBUI.scale(SIZE);
                if (element != null) {
                    var shape = element.outline().shape(new Rectangle2D.Double(x + 1.5, y + size * 0.2, size - 3, size * 0.6));
                    g.setColor(element.tone().fill());
                    g.fill(shape);
                    g.setColor(element.tone().border());
                    g.setStroke(new BasicStroke(JBUIScale.scale(1f)));
                    g.draw(shape);
                } else {
                    float width = Math.max(JBUIScale.scale(1.5f), JBUIScale.scale(connection.thickness()));
                    float[] pattern = connection.dash() == Dash.DASHED ? new float[] { 3 * width, 2 * width }
                            : connection.dash() == Dash.DOTTED ? new float[] { width, 2 * width } : null;
                    g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, pattern, 0f));
                    g.setColor(connection.tone().border());
                    g.draw(new Line2D.Double(x + 2, y + size - 3, x + size - 2, y + 3));
                }
            } finally {
                g.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return JBUI.scale(SIZE);
        }

        @Override
        public int getIconHeight() {
            return JBUI.scale(SIZE);
        }
    }
}
