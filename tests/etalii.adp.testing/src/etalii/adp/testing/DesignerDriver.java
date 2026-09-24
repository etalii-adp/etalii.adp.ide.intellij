package etalii.adp.testing;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.commands.operations.OperationHistoryFactory;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourceAttributes;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.RequestConstants;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.ui.actions.GEFActionConstants;
import org.eclipse.gef.requests.ChangeBoundsRequest;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Text;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.intro.IIntroPart;
import org.eclipse.ui.texteditor.ITextEditor;

import etalii.adp.core.AdpDesignerEditor;

/**
 * The one entry point for unattended visual tests (SC-007). It runs in the Tycho UI harness on the
 * UI thread, opens a file in a fresh workspace project, acts through the same routes a person
 * uses (commands, the viewer, the text page) and observes the document, the saved bytes and the
 * rendered figures. It knows nothing about any file format.
 */
public final class DesignerDriver implements AutoCloseable {

    private static final AtomicInteger PROJECTS = new AtomicInteger();
    private static final String[] GLOBAL_ACTION_IDS = { ActionFactory.UNDO.getId(), ActionFactory.REDO.getId(),
            ActionFactory.SELECT_ALL.getId(), ActionFactory.DELETE.getId(), ActionFactory.REVERT.getId(), GEFActionConstants.ZOOM_IN,
            GEFActionConstants.ZOOM_OUT };

    private final IProject project;
    private final IFile file;
    private final String requestedEditorId;
    private IEditorPart part;

    private DesignerDriver(IProject project, IFile file, String editorId) {
        this.project = project;
        this.file = file;
        this.requestedEditorId = editorId;
    }

    /**
     * Copy an example file into a fresh workspace project and open it with the given editor, or with
     * the editor the workbench chooses when {@code editorId} is {@code null}.
     */
    public static DesignerDriver open(Path exampleFile, String editorId) {
        try {
            return openBytes(exampleFile.getFileName().toString(), Files.readAllBytes(exampleFile), editorId);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Same, for an in-memory text (generated maps, edge cases), written as UTF-8. */
    public static DesignerDriver openText(String fileName, String content, String editorId) {
        return openBytes(fileName, content.getBytes(UTF_8), editorId);
    }

    private static DesignerDriver openBytes(String fileName, byte[] bytes, String editorId) {
        try {
            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("adp-test-" + PROJECTS.incrementAndGet());
            if (project.exists()) {
                project.delete(true, true, null);
            }
            project.create(null);
            project.open(null);
            IFile file = project.getFile(fileName);
            file.create(new ByteArrayInputStream(bytes), IResource.FORCE, null);
            DesignerDriver driver = new DesignerDriver(project, file, editorId);
            driver.openEditor();
            return driver;
        } catch (CoreException e) {
            throw new IllegalStateException(e);
        }
    }

    private void openEditor() {
        IWorkbenchWindow window = window();
        IIntroPart intro = PlatformUI.getWorkbench().getIntroManager().getIntro();
        if (intro != null) {
            PlatformUI.getWorkbench().getIntroManager().closeIntro(intro);
        }
        window.getShell().forceActive();
        IWorkbenchPage page = window.getActivePage();
        try {
            part = requestedEditorId == null ? IDE.openEditor(page, file, true) : IDE.openEditor(page, file, requestedEditorId, true);
        } catch (PartInitException e) {
            throw new IllegalStateException(e);
        }
        page.activate(part);
        settle();
    }

    /** The designer, or {@code null} when the workbench opened another editor. */
    public AdpDesignerEditor<?> editor() {
        return part instanceof AdpDesignerEditor<?> designer ? designer : null;
    }

    /** The id of the editor the file is open in. */
    public String editorIdUsed() {
        return part.getSite().getId();
    }

    /** The workspace file under test. */
    public IFile file() {
        return file;
    }

    // Acting

    /** Select the edit parts of these GEF models, for example node keys. */
    public DesignerDriver select(Object... models) {
        List<EditPart> parts = new ArrayList<>();
        for (Object model : models) {
            parts.add(partOf(model));
        }
        viewer().setSelection(new StructuredSelection(parts));
        settle();
        return this;
    }

    /**
     * Run a command through the handler service, as key bindings and menus do. A command that is
     * not enabled or not handled throws {@link IllegalStateException} with that cause.
     *
     * <p>
     * The workbench activates an editor's global action handlers (undo, redo, zoom, select all)
     * only while its window has the operating system's focus, which an unattended run cannot
     * promise. When the handler service finds no enabled handler, the editor's global action
     * handler for the command runs instead, as the Edit menu's retargeted actions run it.
     */
    public DesignerDriver run(String commandId) {
        part.getSite().getPage().activate(part);
        settle();
        IHandlerService handlers = window().getService(IHandlerService.class);
        try {
            handlers.executeCommand(commandId, null);
        } catch (NotEnabledException | NotHandledException e) {
            IAction action = globalActionFor(commandId);
            if (action == null || !action.isEnabled()) {
                throw new IllegalStateException("Command " + commandId + " did not run; the editor's global action for it is "
                        + (action == null ? "absent" : "disabled"), e);
            }
            action.run();
        } catch (Exception e) {
            throw new IllegalStateException("Command " + commandId + " did not run", e);
        }
        settle();
        return this;
    }

    private IAction globalActionFor(String commandId) {
        if (!(part.getSite() instanceof IEditorSite site)) {
            return null;
        }
        IActionBars bars = site.getActionBars();
        for (String id : GLOBAL_ACTION_IDS) {
            IAction action = bars.getGlobalActionHandler(id);
            if (action != null && commandId.equals(action.getActionDefinitionId())) {
                return action;
            }
        }
        return null;
    }

    /**
     * Drag the model's figure onto the target's and drop it at {@code where}, as GEF's drag tracker
     * does: the edit part under the drop point gets a move request, and its command is executed.
     */
    public DesignerDriver dragOnto(Object model, Object targetModel, DropPosition where) {
        GraphicalEditPart dragged = partOf(model);
        GraphicalEditPart target = partOf(targetModel);
        Rectangle bounds = absoluteBounds(target.getFigure());
        int y = switch (where) {
        case BEFORE -> bounds.y + bounds.height / 10;
        case ONTO -> bounds.y + bounds.height / 2;
        case AFTER -> bounds.bottom() - 1 - bounds.height / 10;
        };
        Point location = new Point(bounds.x + bounds.width / 2, y);
        Point origin = absoluteBounds(dragged.getFigure()).getCenter();

        ChangeBoundsRequest request = new ChangeBoundsRequest(RequestConstants.REQ_MOVE);
        request.setEditParts(dragged);
        request.setLocation(location);
        request.setMoveDelta(new Point(location.x - origin.x, location.y - origin.y));
        EditPart found = viewer().findObjectAtExcluding(location, List.of(dragged.getFigure()),
                editPart -> editPart.getTargetEditPart(request) != null);
        EditPart container = found == null ? null : found.getTargetEditPart(request);
        if (container != null && container != dragged.getParent()) {
            request.setType(RequestConstants.REQ_ADD);
        }
        Command command = container == null ? null : container.getCommand(request);
        if (command != null && command.canExecute()) {
            viewer().getEditDomain().getCommandStack().execute(command);
        }
        settle();
        return this;
    }

    /** Type into the open in-place editor and press Enter, which finishes it. */
    public DesignerDriver typeInPlace(String text) {
        Text editorText = findText(viewer().getControl());
        if (editorText == null) {
            throw new IllegalStateException("No in-place editor is open");
        }
        editorText.setText(text);
        editorText.notifyListeners(SWT.DefaultSelection, new Event());
        settle();
        return this;
    }

    /** Undo through the workbench's standard command, as Edit > Undo does. */
    public DesignerDriver undo() {
        return run("org.eclipse.ui.edit.undo");
    }

    /** Redo through the workbench's standard command, as Edit > Redo does. */
    public DesignerDriver redo() {
        return run("org.eclipse.ui.edit.redo");
    }

    public DesignerDriver showPage(Page page) {
        if (page == Page.VISUAL) {
            editor().showVisualPage();
        } else {
            editor().showTextPage();
        }
        settle();
        return this;
    }

    /** Edit through the text page: activates it and replaces only the changed middle of the text. */
    public DesignerDriver editText(UnaryOperator<String> change) {
        if (editor() != null) {
            editor().showTextPage();
        }
        IDocument document = document();
        String before = document.get();
        String after = change.apply(before);
        int prefix = 0;
        while (prefix < before.length() && prefix < after.length() && before.charAt(prefix) == after.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < before.length() - prefix && suffix < after.length() - prefix
                && before.charAt(before.length() - 1 - suffix) == after.charAt(after.length() - 1 - suffix)) {
            suffix++;
        }
        try {
            document.replace(prefix, before.length() - prefix - suffix, after.substring(prefix, after.length() - suffix));
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        settle();
        return this;
    }

    /** Change the file on disk behind the workspace's back, as version control does, then refresh. */
    public DesignerDriver changeOnDisk(String newContent) {
        try {
            Path path = file.getLocation().toFile().toPath();
            long stamp = Files.getLastModifiedTime(path).toMillis();
            Files.write(path, newContent.getBytes(UTF_8));
            path.toFile().setLastModified(Math.max(System.currentTimeMillis(), stamp + 2000));
            file.refreshLocal(IResource.DEPTH_ZERO, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (CoreException e) {
            throw new IllegalStateException(e);
        }
        settle();
        return this;
    }

    /**
     * Make the file read-only, or writable again, and reopen it, since editors read the state on
     * opening (FR-008).
     */
    public DesignerDriver setReadOnly(boolean readOnly) {
        part.getSite().getPage().closeEditor(part, false);
        setReadOnlyAttribute(readOnly);
        openEditor();
        return this;
    }

    // Observing

    /** The current document. */
    public String text() {
        return document().get();
    }

    /** Save, then read the file from disk. */
    public byte[] savedBytes() {
        part.doSave(new NullProgressMonitor());
        settle();
        try {
            return Files.readAllBytes(file.getLocation().toFile().toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean isDirty() {
        return part.isDirty();
    }

    /** The label of the operation Edit > Undo would undo now, or {@code null}. */
    public String undoLabel() {
        IDocumentUndoManager manager = DocumentUndoManagerRegistry.getDocumentUndoManager(document());
        IUndoableOperation operation = OperationHistoryFactory.getOperationHistory().getUndoOperation(manager.getUndoContext());
        return operation == null ? null : operation.getLabel();
    }

    /** The figure drawn for a GEF model, or {@code null} when it is not drawn. */
    public IFigure figureOf(Object model) {
        EditPart editPart = viewer().getEditPartRegistry().get(model);
        return editPart instanceof GraphicalEditPart graphical ? graphical.getFigure() : null;
    }

    public List<Object> selectedModels() {
        return viewer().getSelectedEditParts().stream().map(EditPart::getModel).toList();
    }

    public boolean problemShown() {
        return editor().model() == null;
    }

    /** Run pending UI work, such as the coalesced re-parse after a document change. */
    public void settle() {
        Display display = Display.getCurrent();
        for (int round = 0; round < 3; round++) {
            while (display.readAndDispatch()) {
                // keep dispatching
            }
        }
    }

    /** Close without saving and delete the project. */
    @Override
    public void close() {
        IWorkbenchPage page = window().getActivePage();
        if (part != null && page != null && List.of(page.getEditorReferences()).stream().anyMatch(r -> r.getPart(false) == part)) {
            page.closeEditor(part, false);
            settle();
        }
        try {
            setReadOnlyAttribute(false);
            project.delete(true, true, null);
        } catch (CoreException e) {
            throw new IllegalStateException(e);
        }
    }

    private void setReadOnlyAttribute(boolean readOnly) {
        ResourceAttributes attributes = file.getResourceAttributes();
        if (attributes != null && attributes.isReadOnly() != readOnly) {
            attributes.setReadOnly(readOnly);
            try {
                file.setResourceAttributes(attributes);
            } catch (CoreException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private IDocument document() {
        if (editor() != null) {
            return editor().document();
        }
        ITextEditor text = part.getAdapter(ITextEditor.class);
        return text.getDocumentProvider().getDocument(text.getEditorInput());
    }

    private GraphicalViewer viewer() {
        return editor().viewer();
    }

    private GraphicalEditPart partOf(Object model) {
        EditPart editPart = viewer().getEditPartRegistry().get(model);
        if (!(editPart instanceof GraphicalEditPart graphical)) {
            throw new IllegalArgumentException("Nothing is drawn for " + model);
        }
        return graphical;
    }

    private static Rectangle absoluteBounds(IFigure figure) {
        Rectangle bounds = figure.getBounds().getCopy();
        figure.translateToAbsolute(bounds);
        return bounds;
    }

    private static Text findText(Control control) {
        if (control instanceof Text text && !text.isDisposed() && text.isVisible()) {
            return text;
        }
        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                Text found = findText(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static IWorkbenchWindow window() {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        return window != null ? window : PlatformUI.getWorkbench().getWorkbenchWindows()[0];
    }
}
