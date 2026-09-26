package etalii.adp.core;

import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.DocumentReferenceProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.components.BorderLayoutPanel;

import etalii.adp.core.ui.ProblemPanel;
import etalii.adp.core.ui.ReadOnlyBanner;

/**
 * The visual side of a designer (contracts/designer-framework.md). It never keeps its own copy of
 * the content: it parses the {@link Document} on open and after every change, and every visual
 * change is {@link TextChanges} run as one named command, which is one step in the IDE's Undo
 * (research R4, R7). The provider pairs it with the platform's text editor on the same document.
 *
 * @param <M> the format's parse result
 */
public abstract class AdpDesignerEditor<M> extends UserDataHolderBase implements FileEditor, DocumentReferenceProvider {

    private final Project project;
    private final VirtualFile file;
    private final Document document;
    private final ViewState viewState = new ViewState();
    private final List<Runnable> modelListeners = new CopyOnWriteArrayList<>();
    private final DesignerPanel root = new DesignerPanel();
    private final JPanel notices = new JPanel(new BorderLayout());
    private final ReadOnlyBanner readOnlyBanner = new ReadOnlyBanner();
    private final DocumentListener documentListener = new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
            scheduleRefresh();
        }
    };
    private final PropertyChangeListener writableListener = event -> {
        if (Document.PROP_WRITABLE.equals(event.getPropertyName())) {
            updateBanner();
        }
    };
    private JComponent view;
    private JBScrollPane scrollPane;
    private ProblemPanel problemPanel;
    private Runnable showText = () -> {
    };
    private M model;
    private boolean refreshScheduled;
    private boolean disposed;

    protected AdpDesignerEditor(Project project, VirtualFile file, Document document) {
        this.project = project;
        this.file = file;
        this.document = document;
    }

    /** Parse the whole text. Throw {@link FormatProblem} when it cannot be shown. Must not modify anything. */
    protected abstract M parse(CharSequence text) throws FormatProblem;

    /** The component that shows a model; called once. */
    protected abstract JComponent createView();

    /** Show a freshly parsed model, keeping selection and expansion by key. */
    protected abstract void modelChanged(M model);

    /** Make the item with this key visible and selected, changing view state only. */
    public abstract void reveal(Object key);

    /** The laid-out box of an item, or {@code null} when it is not shown. */
    public abstract NodeView viewOf(Object key);

    /** Every item's key in the current model, in document order, for Select All. */
    protected abstract List<?> allKeys();

    @Override
    public abstract StructureViewBuilder getStructureViewBuilder();

    /** Called once by the provider, after construction. */
    final void start(Runnable showText) {
        this.showText = showText;
        view = createView();
        scrollPane = new JBScrollPane(view);
        root.addToTop(notices);
        root.addToCenter(scrollPane);
        document.addDocumentListener(documentListener, this);
        document.addPropertyChangeListener(writableListener);
        refresh();
    }

    private void scheduleRefresh() {
        if (refreshScheduled || disposed) {
            return;
        }
        refreshScheduled = true;
        ApplicationManager.getApplication().invokeLater(() -> {
            if (refreshScheduled) {
                refresh();
            }
        }, ModalityState.any());
    }

    /** Re-parse the document now and update the view. */
    protected final void refresh() {
        refreshScheduled = false;
        if (disposed) {
            return;
        }
        try {
            M parsed = parse(document.getImmutableCharSequence());
            model = parsed;
            showProblem(null);
            modelChanged(parsed);
            modelListeners.forEach(Runnable::run);
        } catch (FormatProblem problem) {
            model = null;
            showProblem(problem);
            modelListeners.forEach(Runnable::run);
        }
        updateBanner();
    }

    private void showProblem(FormatProblem problem) {
        String before = problemMessage();
        if (problemPanel != null) {
            notices.remove(problemPanel);
            problemPanel = null;
        }
        if (problem != null) {
            int offset = Math.max(0, Math.min(problem.getOffset(), document.getTextLength()));
            int line = document.getLineNumber(offset);
            int column = offset - document.getLineStartOffset(line);
            problemPanel = new ProblemPanel(problem.getMessage(), line + 1, column + 1, showText);
            notices.add(problemPanel, BorderLayout.CENTER);
        }
        scrollPane.setVisible(problem == null);
        if (!Objects.equals(before, problemMessage())) {
            EditorNotifications.getInstance(project).updateNotifications(file);
        }
        root.revalidate();
        root.repaint();
    }

    private void updateBanner() {
        boolean readOnly = !document.isWritable();
        if (readOnly && readOnlyBanner.getParent() == null) {
            notices.add(readOnlyBanner, BorderLayout.NORTH);
        } else if (!readOnly && readOnlyBanner.getParent() != null) {
            notices.remove(readOnlyBanner);
        }
        root.revalidate();
    }

    /**
     * The parse of the document as it is now, or {@code null} while a problem is shown. A change
     * whose refresh is still queued is parsed first, so an edit is never built on stale offsets.
     */
    public M model() {
        if (refreshScheduled && ApplicationManager.getApplication().isDispatchThread()) {
            refresh();
        }
        return model;
    }

    /** False when the document is read-only or a problem is shown. */
    public boolean isEditable() {
        return model != null && document.isWritable();
    }

    /**
     * One command named {@code label} that applies {@code changes}: one step in the IDE's Undo.
     * A no-op when not editable.
     */
    public void execute(String label, TextChanges changes) {
        execute(label, changes, () -> {
        });
    }

    /**
     * As {@link #execute(String, TextChanges)}, then {@code andThen} (such as selecting the new
     * node) on the fresh parse, inside the same command. The IDE records the view state a command
     * leaves as that step's, and an Undo whose view state differs from the current one first only
     * restores it; a selection made after the command would take a Ctrl+Z of its own.
     */
    public void execute(String label, TextChanges changes, Runnable andThen) {
        // Input events arrive without the write-intent lock, which asking for write access needs (it consults the project file index).
        if (!isEditable() || changes.isEmpty()
                || !WriteIntentReadAction.compute(() -> FileDocumentManager.getInstance().requestWriting(document, project))) {
            return;
        }
        WriteCommandAction.writeCommandAction(project).withName(label).run(() -> {
            changes.applyTo(document);
            refresh();
            andThen.run();
        });
    }

    public Document document() {
        return document;
    }

    public Project project() {
        return project;
    }

    public ViewState viewState() {
        return viewState;
    }

    /** Select these keys, in order. Listeners (Structure view, actions) follow. */
    public void select(Collection<?> keys) {
        viewState.select(keys);
        view.repaint();
    }

    public void selectAll() {
        select(allKeys());
    }

    public List<Object> selection() {
        return viewState.selection();
    }

    /** Runs after every parse, successful or not. */
    public void addModelListener(Runnable listener) {
        modelListeners.add(listener);
    }

    public void removeModelListener(Runnable listener) {
        modelListeners.remove(listener);
    }

    public void zoomIn() {
        viewState.zoomIn();
        zoomChanged();
    }

    public void zoomOut() {
        viewState.zoomOut();
        zoomChanged();
    }

    public void resetZoom() {
        viewState.setZoom(1.0);
        zoomChanged();
    }

    /** The view shows the new {@link ViewState#zoom()}; by default it is laid out again. */
    protected void zoomChanged() {
        view.revalidate();
        view.repaint();
    }

    /** Switch the composite editor to the text view. */
    public void showText() {
        showText.run();
    }

    /** The problem panel's text, or {@code null} while the document can be shown. */
    public String problemMessage() {
        return problemPanel == null ? null : problemPanel.message();
    }

    public boolean readOnlyBannerShown() {
        return readOnlyBanner.getParent() != null;
    }

    public JComponent view() {
        return view;
    }

    /**
     * The context menu from the action group {@code groupId}, and each of its actions' shortcuts
     * while {@code component} has focus, so Tab, Enter, Space and Delete keep their meaning in text
     * editors. Nothing happens when the group is not registered.
     */
    public void installActions(JComponent component, String groupId) {
        if (ActionManager.getInstance().getAction(groupId) instanceof DefaultActionGroup group) {
            PopupHandler.installPopupMenu(component, group, "AdpDesignerPopup");
            List<AnAction> actions = new ArrayList<>();
            collect(group, actions);
            for (AnAction action : actions) {
                action.registerCustomShortcutSet(action.getShortcutSet(), component, this);
            }
        }
    }

    private static void collect(DefaultActionGroup group, List<AnAction> actions) {
        for (AnAction child : group.getChildren(ActionManager.getInstance())) {
            if (child instanceof DefaultActionGroup nested) {
                collect(nested, actions);
            } else if (!(child instanceof Separator)) {
                actions.add(child);
            }
        }
    }

    @Override
    public @NotNull Collection<DocumentReference> getDocumentReferences() {
        return List.of(DocumentReferenceManager.getInstance().create(document));
    }

    @Override
    public @NotNull JComponent getComponent() {
        return root;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return view;
    }

    @Override
    public @NotNull String getName() {
        return "Designer";
    }

    @Override
    public @NotNull VirtualFile getFile() {
        return file;
    }

    @Override
    public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
        return new DesignerState(viewState.zoom(), viewState.selection());
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
        if (state instanceof DesignerState designerState) {
            viewState.setZoom(designerState.zoom());
            select(designerState.selection());
            zoomChanged();
        }
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return !disposed && file.isValid();
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void dispose() {
        disposed = true;
        document.removePropertyChangeListener(writableListener);
        modelListeners.clear();
    }

    /** Zoom and selection, kept across tab switches; never written to the file. */
    public record DesignerState(double zoom, List<Object> selection) implements FileEditorState {

        @Override
        public boolean canBeMergedWith(@NotNull FileEditorState other, @NotNull FileEditorStateLevel level) {
            return other instanceof DesignerState;
        }
    }

    /** Publishes the designer and its selection to actions. */
    private final class DesignerPanel extends BorderLayoutPanel implements UiDataProvider {

        @Override
        public void uiDataSnapshot(@NotNull DataSink sink) {
            sink.set(AdpDataKeys.ADP_DESIGNER, AdpDesignerEditor.this);
            sink.set(PlatformCoreDataKeys.SELECTED_ITEMS, viewState.selection().toArray());
        }
    }
}
