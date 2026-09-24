package etalii.adp.core;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.ILog;
import org.eclipse.gef.DefaultEditDomain;
import org.eclipse.gef.EditDomain;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPartFactory;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.editparts.ScalableFreeformRootEditPart;
import org.eclipse.gef.editparts.ZoomManager;
import org.eclipse.gef.ui.actions.GEFActionConstants;
import org.eclipse.gef.ui.actions.SelectAllAction;
import org.eclipse.gef.ui.actions.ZoomInAction;
import org.eclipse.gef.ui.actions.ZoomOutAction;
import org.eclipse.gef.ui.parts.GraphicalViewerKeyHandler;
import org.eclipse.gef.ui.parts.ScrollingGraphicalViewer;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.contexts.IContextActivation;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.operations.RedoActionHandler;
import org.eclipse.ui.operations.UndoActionHandler;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.part.MultiPageSelectionProvider;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.IDocumentProviderExtension;

import etalii.adp.core.ui.MessagePanel;

/**
 * A visual designer for a text file, as a proper Eclipse editor (research R1). Page 0 is a GEF
 * viewer; page 1 is the platform's own {@link TextEditor}, which owns the document, saving, dirty
 * state, revert and external changes. The visual page never keeps its own copy of the content: it
 * re-parses the document after every change, and every visual change is a {@link TextEdit} run as
 * one labelled operation in the document's undo context (research R2).
 *
 * @param <M> the format's parse result
 */
public abstract class AdpDesignerEditor<M> extends MultiPageEditorPart {

    private static final int VISUAL_PAGE = 0;
    private static final int TEXT_PAGE = 1;

    private final IDocumentListener documentListener = new IDocumentListener() {
        @Override
        public void documentAboutToBeChanged(DocumentEvent event) {
        }

        @Override
        public void documentChanged(DocumentEvent event) {
            scheduleRefresh();
        }
    };

    private final Map<String, IAction> visualActions = new HashMap<>();
    private TextEditor textEditor;
    private ScrollingGraphicalViewer viewer;
    private Composite visualPage;
    private MessagePanel banner;
    private MessagePanel problemPanel;
    private IDocument document;
    private M model;
    private boolean refreshScheduled;
    private IContextActivation contextActivation;
    private boolean siteReady;

    /** Parse the whole document. Throw {@link FormatProblem} when it cannot be shown. Must not modify it. */
    protected abstract M parse(IDocument document) throws FormatProblem;

    /** Edit parts for this format's model. Called once when the viewer is created. */
    protected abstract EditPartFactory createEditPartFactory();

    /** The object handed to {@link GraphicalViewer#setContents(Object)} for a freshly parsed model. */
    protected abstract Object contentsFor(M model);

    /** The context id to activate while the visual page has focus. */
    protected abstract String visualContextId();

    @Override
    public void init(IEditorSite site, IEditorInput input) throws PartInitException {
        super.init(site, input);
        site.setSelectionProvider(new DesignerSelectionProvider(this));
        setPartName(input.getName());
    }

    @Override
    protected void createPages() {
        createVisualPage();
        textEditor = new TextEditor();
        try {
            addPage(textEditor, getEditorInput());
        } catch (PartInitException e) {
            ILog.of(getClass()).log(e.getStatus());
            return;
        }
        setPageText(VISUAL_PAGE, "Design");
        setPageText(TEXT_PAGE, "Text");
        hookDocument();
        refresh();
        if (model == null) {
            setActivePage(TEXT_PAGE);
        }
    }

    private void createVisualPage() {
        visualPage = new Composite(getContainer(), SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.verticalSpacing = 0;
        visualPage.setLayout(layout);
        banner = new MessagePanel(visualPage);
        problemPanel = new MessagePanel(visualPage);

        viewer = new ScrollingGraphicalViewer();
        viewer.createControl(visualPage);
        viewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        ScalableFreeformRootEditPart root = new ScalableFreeformRootEditPart();
        viewer.setRootEditPart(root);
        EditDomain editDomain = new DefaultEditDomain(this);
        editDomain.setCommandStack(new OperationHistoryCommandStack(this::execute));
        editDomain.addViewer(viewer);
        viewer.setEditPartFactory(createEditPartFactory());
        viewer.setKeyHandler(new GraphicalViewerKeyHandler(viewer));

        MenuManager menu = new MenuManager();
        menu.setRemoveAllWhenShown(true);
        menu.addMenuListener(m -> m.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS)));
        viewer.setContextMenu(menu);
        getSite().registerContextMenu(getSite().getId() + ".context", menu, viewer);
        viewer.addSelectionChangedListener(this::visualSelectionChanged);

        ZoomManager zoom = root.getZoomManager();
        IAction zoomIn = new ZoomInAction(zoom);
        IAction zoomOut = new ZoomOutAction(zoom);
        IAction selectAll = new SelectAllAction(this);
        selectAll.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_SELECT_ALL);
        visualActions.put(GEFActionConstants.ZOOM_IN, zoomIn);
        visualActions.put(GEFActionConstants.ZOOM_OUT, zoomOut);
        visualActions.put(ActionFactory.SELECT_ALL.getId(), selectAll);

        addPage(visualPage);
    }

    private void hookDocument() {
        IDocumentProvider provider = textEditor.getDocumentProvider();
        document = provider.getDocument(textEditor.getEditorInput());
        document.addDocumentListener(documentListener);
        disposeUndoActions();
        IUndoContext context = undoContext();
        UndoActionHandler undo = new UndoActionHandler(getSite(), context);
        undo.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_UNDO);
        RedoActionHandler redo = new RedoActionHandler(getSite(), context);
        redo.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_REDO);
        visualActions.put(ActionFactory.UNDO.getId(), undo);
        visualActions.put(ActionFactory.REDO.getId(), redo);
    }

    private void unhookDocument() {
        if (document != null) {
            document.removeDocumentListener(documentListener);
            document = null;
        }
    }

    private void disposeUndoActions() {
        for (String id : new String[] { ActionFactory.UNDO.getId(), ActionFactory.REDO.getId() }) {
            IAction action = visualActions.remove(id);
            if (action instanceof UndoActionHandler undo) {
                undo.dispose();
            } else if (action instanceof RedoActionHandler redo) {
                redo.dispose();
            }
        }
    }

    private IUndoContext undoContext() {
        IDocumentUndoManager manager = DocumentUndoManagerRegistry.getDocumentUndoManager(document);
        return manager.getUndoContext();
    }

    private void scheduleRefresh() {
        if (refreshScheduled || visualPage == null || visualPage.isDisposed()) {
            return;
        }
        refreshScheduled = true;
        visualPage.getDisplay().asyncExec(() -> {
            if (refreshScheduled) {
                refresh();
            }
        });
    }

    /** Re-parse the document and update the visual page. */
    private void refresh() {
        refreshScheduled = false;
        if (viewer == null || viewer.getControl().isDisposed() || document == null) {
            return;
        }
        try {
            M parsed = parse(document);
            model = parsed;
            Object contents = contentsFor(parsed);
            EditPart current = viewer.getContents();
            if (current != null && current.getModel() == contents) {
                refreshDeep(current);
            } else {
                viewer.setContents(contents);
            }
            problemPanel.hidePanel();
            showViewer(true);
        } catch (FormatProblem problem) {
            model = null;
            int offset = Math.max(0, Math.min(problem.getOffset(), document.getLength()));
            int line = 0;
            int column = 0;
            try {
                line = document.getLineOfOffset(offset);
                column = offset - document.getLineOffset(line);
            } catch (BadLocationException e) {
                // the offset was clamped to the document, so it is always valid
            }
            problemPanel.showProblem(problem.getMessage(), line + 1, column + 1, this::showTextPage);
            showViewer(false);
        }
        updateBanner();
    }

    private static void refreshDeep(EditPart part) {
        part.refresh();
        for (EditPart child : part.getChildren()) {
            refreshDeep(child);
        }
    }

    private void showViewer(boolean shown) {
        Control control = viewer.getControl();
        ((GridData) control.getLayoutData()).exclude = !shown;
        control.setVisible(shown);
        visualPage.layout(true, true);
    }

    private void updateBanner() {
        if (banner == null || banner.isDisposed() || textEditor == null) {
            return;
        }
        if (!isInputReadOnly()) {
            banner.hidePanel();
        } else if (!banner.isVisible()) {
            banner.showBanner("This file is read-only, so it can be viewed but not edited.");
        }
    }

    /**
     * The text editor treats a read-only file as editable until the first change is validated, so
     * the provider's read-only state is asked as well.
     */
    private boolean isInputReadOnly() {
        IDocumentProvider provider = textEditor.getDocumentProvider();
        return !textEditor.isEditable()
                || provider instanceof IDocumentProviderExtension extension && extension.isReadOnly(textEditor.getEditorInput());
    }

    /** The latest successful parse, or {@code null} while a {@link FormatProblem} is shown. */
    public M model() {
        return model;
    }

    /** False when the input is read-only or a {@link FormatProblem} is shown. */
    public boolean isEditable() {
        return model != null && textEditor != null && !isInputReadOnly();
    }

    /** Run one labelled, undoable change against the document (research R2). No-op when not editable. */
    public void execute(String label, TextEdit edit) {
        if (!isEditable() || !textEditor.validateEditorInputState()) {
            return;
        }
        IOperationHistory history = PlatformUI.getWorkbench().getOperationSupport().getOperationHistory();
        IStatus status = new DocumentEditOperation(label, document, edit).runIn(history);
        if (status.isOK()) {
            refresh();
        } else {
            ILog.of(getClass()).log(status);
        }
    }

    /**
     * Redraw the visual page from the current document now, for a change of view state only, such
     * as expanding a branch without writing it. Document changes redraw on their own.
     */
    protected void refreshView() {
        refresh();
    }

    public GraphicalViewer viewer() {
        return viewer;
    }

    public IDocument document() {
        return document;
    }

    /** The nested platform text editor that owns the document. */
    public TextEditor textEditor() {
        return textEditor;
    }

    public boolean isVisualPageActive() {
        return getActivePage() == VISUAL_PAGE;
    }

    public void showVisualPage() {
        setActivePage(VISUAL_PAGE);
    }

    public void showTextPage() {
        setActivePage(TEXT_PAGE);
    }

    /** The problem panel's text, or {@code null} when the document can be shown. */
    public String problemMessage() {
        return problemPanel == null ? null : problemPanel.message();
    }

    /** The read-only banner's text, or {@code null} when the file can be edited. */
    public String readOnlyMessage() {
        return banner == null ? null : banner.message();
    }

    /** The visual page's action for a global action or command id, for {@link AdpActionBarContributor}. */
    IAction visualAction(String id) {
        return visualActions.get(id);
    }

    @Override
    protected void pageChange(int newPageIndex) {
        super.pageChange(newPageIndex);
        if (newPageIndex == VISUAL_PAGE) {
            if (refreshScheduled) {
                refresh();
            }
            updateBanner();
            if (getSite().getSelectionProvider() instanceof DesignerSelectionProvider provider) {
                provider.fireSelectionChanged(new SelectionChangedEvent(provider, viewer.getSelection()));
            }
        }
        updateVisualContext();
    }

    @Override
    public void setFocus() {
        siteReady = true;
        updateVisualContext();
        if (getActivePage() == VISUAL_PAGE && viewer != null) {
            updateBanner();
            viewer.getControl().setFocus();
        } else {
            super.setFocus();
        }
    }

    /**
     * Activates the format's context while the visual page shows, so its key bindings apply there
     * and the text page keeps the text editor's. The site's services exist once the part has had
     * focus, not while its pages are being created.
     */
    private void updateVisualContext() {
        if (!siteReady) {
            return;
        }
        IContextService contexts = getSite().getService(IContextService.class);
        if (getActivePage() == VISUAL_PAGE && contextActivation == null) {
            contextActivation = contexts.activateContext(visualContextId());
        } else if (getActivePage() != VISUAL_PAGE && contextActivation != null) {
            contexts.deactivateContext(contextActivation);
            contextActivation = null;
        }
    }

    @Override
    protected void handlePropertyChange(int propertyId) {
        if (propertyId == IEditorPart.PROP_INPUT && textEditor != null) {
            unhookDocument();
            setInputWithNotify(textEditor.getEditorInput());
            setPartName(getEditorInput().getName());
            hookDocument();
            refresh();
        }
        super.handlePropertyChange(propertyId);
    }

    @Override
    public void doSave(IProgressMonitor monitor) {
        textEditor.doSave(monitor);
    }

    @Override
    public void doSaveAs() {
        textEditor.doSaveAs();
    }

    @Override
    public boolean isSaveAsAllowed() {
        return true;
    }

    /** Revert to the saved file, as the text editor's Revert does. */
    public void doRevertToSaved() {
        textEditor.doRevertToSaved();
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        // The text editor checks for external changes only when the activated part adapts to it.
        // MultiPageEditorPart adapts only while the text page shows, so a dirty designer on its
        // visual page would never ask whether to replace a file changed on disk (FR-005).
        if (adapter == AbstractTextEditor.class && textEditor != null) {
            return adapter.cast(textEditor);
        }
        if (adapter == GraphicalViewer.class) {
            return adapter.cast(viewer);
        }
        if (adapter == ZoomManager.class && viewer != null) {
            return adapter.cast(((ScalableFreeformRootEditPart) viewer.getRootEditPart()).getZoomManager());
        }
        return super.getAdapter(adapter);
    }

    @Override
    public void dispose() {
        unhookDocument();
        disposeUndoActions();
        super.dispose();
    }

    private void visualSelectionChanged(SelectionChangedEvent event) {
        if (getActivePage() != TEXT_PAGE && getSite().getSelectionProvider() instanceof DesignerSelectionProvider provider) {
            SelectionChangedEvent forwarded = new SelectionChangedEvent(provider, event.getSelection());
            provider.fireSelectionChanged(forwarded);
            provider.firePostSelectionChanged(forwarded);
        }
    }

    /** Publishes the viewer's selection on the visual page and the text editor's on the text page (FR-027). */
    private static final class DesignerSelectionProvider extends MultiPageSelectionProvider {

        private final AdpDesignerEditor<?> editor;

        DesignerSelectionProvider(AdpDesignerEditor<?> editor) {
            super(editor);
            this.editor = editor;
        }

        private boolean visual() {
            return editor.getActivePage() != TEXT_PAGE && editor.viewer != null;
        }

        @Override
        public ISelection getSelection() {
            return visual() ? editor.viewer.getSelection() : super.getSelection();
        }

        @Override
        public void setSelection(ISelection selection) {
            if (visual()) {
                editor.viewer.setSelection(selection);
            } else {
                super.setSelection(selection);
            }
        }
    }
}
