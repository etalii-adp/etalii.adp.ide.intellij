package etalii.adp.testing;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import com.intellij.ide.DataManager;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;

import etalii.adp.core.AdpDesignerEditor;
import etalii.adp.core.AdpEditorProvider;
import etalii.adp.core.NodeView;

/**
 * Drives a designer as a user would, inside a headless IDE (contracts/test-kit.md). The test case
 * must install a real file editor manager, as {@code FileEditorManagerTestCase} does, so files open
 * through the IDE's own providers and editor-opened events fire. Runs on the event dispatch thread
 * and knows nothing about any format.
 */
public final class DesignerDriver implements AutoCloseable {

    private final CodeInsightTestFixture fixture;
    private final Project project;
    private final VirtualFile file;
    private final Document document;
    private final Disposable disposable = Disposer.newDisposable("DesignerDriver");
    private final FileEditor opened;

    private DesignerDriver(CodeInsightTestFixture fixture, VirtualFile file) {
        this.fixture = fixture;
        this.project = fixture.getProject();
        this.file = file;
        FileEditor[] editors = FileEditorManager.getInstance(project).openFile(file, true);
        this.opened = editors.length == 0 ? null : FileEditorManager.getInstance(project).getSelectedEditor(file);
        this.document = FileDocumentManager.getInstance().getDocument(file);
        settle();
    }

    /** Copy an example file into the test project, byte for byte, and open it as the IDE would. */
    public static DesignerDriver open(CodeInsightTestFixture fixture, Path exampleFile) {
        try {
            return openBytes(fixture, exampleFile.getFileName().toString(), Files.readAllBytes(exampleFile));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Same, for an in-memory text (generated maps, edge cases), written as UTF-8. */
    public static DesignerDriver openText(CodeInsightTestFixture fixture, String fileName, String content) {
        return openBytes(fixture, fileName, content.getBytes(UTF_8));
    }

    /** Same, for exact bytes (line separator and encoding tests). */
    public static DesignerDriver openBytes(CodeInsightTestFixture fixture, String fileName, byte[] content) {
        VirtualFile file = createFile(fixture, fileName, content);
        return new DesignerDriver(fixture, file);
    }

    /** Create a file in the test project without opening it. */
    public static VirtualFile createFile(CodeInsightTestFixture fixture, String fileName, byte[] content) {
        try {
            VirtualFile file = fixture.getTempDirFixture().createFile(fileName);
            WriteAction.runAndWait(() -> file.setBinaryContent(content));
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The designer, or {@code null} when the IDE opened another editor. */
    public AdpDesignerEditor<?> designer() {
        return opened instanceof AdpEditorProvider.Composite composite ? composite.designer() : null;
    }

    /** The composite editor (text and designer), or {@code null} when the IDE opened another editor. */
    public AdpEditorProvider.Composite composite() {
        return opened instanceof AdpEditorProvider.Composite composite ? composite : null;
    }

    /** The editor the IDE shows for the file. */
    public FileEditor editor() {
        return opened;
    }

    public VirtualFile file() {
        return file;
    }

    public Document document() {
        return document;
    }

    /** The editor types the IDE offers for the file, in its order. */
    public List<String> editorTypeIdsOffered() {
        return Arrays.stream(FileEditorProviderManager.getInstance().getProviders(project, file)).map(p -> p.getEditorTypeId()).toList();
    }

    /** The editor type the file opened in by default. */
    public String editorTypeIdUsed() {
        var composite = FileEditorManagerEx.getInstanceEx(project).getComposite(file);
        FileEditor selected = FileEditorManager.getInstance(project).getSelectedEditor(file);
        if (composite == null || selected == null) {
            return null;
        }
        return composite.getAllProviders().get(composite.getAllEditors().indexOf(selected)).getEditorTypeId();
    }

    // Acting

    /** Select these items by the format's keys, as clicks would. */
    public DesignerDriver select(Object... keys) {
        designer().select(List.of(keys));
        return this;
    }

    /** Run an action through the action system with the designer's data context, as keys and menus do. */
    public DesignerDriver run(String actionId) {
        AnAction action = action(actionId);
        AnActionEvent event = event(action);
        ActionUtil.performDumbAwareUpdate(action, event, true);
        if (event.getPresentation().isEnabled()) {
            ActionUtil.performActionDumbAwareWithCallbacks(action, event);
        }
        settle();
        return this;
    }

    /** The action's presentation after an update in the designer's context: enablement and description. */
    public Presentation presentation(String actionId) {
        AnAction action = action(actionId);
        AnActionEvent event = event(action);
        ActionUtil.performDumbAwareUpdate(action, event, true);
        return event.getPresentation();
    }

    /**
     * A key press while the designer's view has focus: an action whose shortcut the view registered
     * runs; otherwise the view handles the key itself.
     */
    public DesignerDriver press(String keystroke) {
        KeyStroke stroke = KeyStroke.getKeyStroke(keystroke);
        if (stroke == null) {
            throw new IllegalArgumentException("Not a keystroke: " + keystroke);
        }
        for (AnAction action : ActionUtil.getActions(view())) {
            for (Shortcut shortcut : action.getShortcutSet().getShortcuts()) {
                if (shortcut instanceof KeyboardShortcut keyboard && keyboard.getFirstKeyStroke().equals(stroke)
                        && keyboard.getSecondKeyStroke() == null) {
                    AnActionEvent event = event(action);
                    ActionUtil.performDumbAwareUpdate(action, event, true);
                    if (event.getPresentation().isEnabled()) {
                        ActionUtil.performActionDumbAwareWithCallbacks(action, event);
                        settle();
                        return this;
                    }
                }
            }
        }
        long now = System.currentTimeMillis();
        view().dispatchEvent(new KeyEvent(view(), KeyEvent.KEY_PRESSED, now, stroke.getModifiers(), stroke.getKeyCode(), KeyEvent.CHAR_UNDEFINED));
        view().dispatchEvent(new KeyEvent(view(), KeyEvent.KEY_RELEASED, now, stroke.getModifiers(), stroke.getKeyCode(), KeyEvent.CHAR_UNDEFINED));
        settle();
        return this;
    }

    /** Click an item, as the mouse would; {@code clickCount} 2 is a double-click. */
    public DesignerDriver click(Object key, int clickCount, int modifiers) {
        Point at = center(key);
        mouse(MouseEvent.MOUSE_PRESSED, at, clickCount, modifiers);
        mouse(MouseEvent.MOUSE_RELEASED, at, clickCount, modifiers);
        mouse(MouseEvent.MOUSE_CLICKED, at, clickCount, modifiers);
        settle();
        return this;
    }

    /** Drag an item onto another with the mouse, dropping before, onto or after it. */
    public DesignerDriver dragOnto(Object key, Object targetKey, DropPosition where) {
        Point from = center(key);
        Rectangle target = zoomed(designer().viewOf(targetKey).bounds());
        int y = switch (where) {
        case BEFORE -> target.y + Math.max(1, target.height / 10);
        case ONTO -> target.y + target.height / 2;
        case AFTER -> target.y + target.height - Math.max(1, target.height / 10);
        };
        Point to = new Point(target.x + target.width / 2, y);
        mouse(MouseEvent.MOUSE_PRESSED, from, 1, InputEvent.BUTTON1_DOWN_MASK);
        mouse(MouseEvent.MOUSE_DRAGGED, new Point((from.x + to.x) / 2, (from.y + to.y) / 2), 0, InputEvent.BUTTON1_DOWN_MASK);
        mouse(MouseEvent.MOUSE_DRAGGED, to, 0, InputEvent.BUTTON1_DOWN_MASK);
        mouse(MouseEvent.MOUSE_RELEASED, to, 1, 0);
        settle();
        return this;
    }

    /** Type into the open in-place text field and press Enter, which finishes it. */
    public DesignerDriver typeInPlace(String text) {
        JTextField field = inPlaceField();
        if (field == null) {
            throw new IllegalStateException("No in-place editor is open");
        }
        field.setText(text);
        field.postActionEvent();
        settle();
        return this;
    }

    /** The open in-place text field, or {@code null}. */
    public JTextField inPlaceField() {
        return find(view(), JTextField.class);
    }

    /** The IDE's own Undo with the designer focused. */
    public DesignerDriver undo() {
        return run("$Undo");
    }

    /** The IDE's own Redo with the designer focused. */
    public DesignerDriver redo() {
        return run("$Redo");
    }

    /** Switch the layout as the editor's toolbar does. The choice is not remembered past this driver. */
    public DesignerDriver showLayout(Layout layout) {
        composite().setLayoutUnremembered(switch (layout) {
        case DESIGNER -> TextEditorWithPreview.Layout.SHOW_PREVIEW;
        case TEXT -> TextEditorWithPreview.Layout.SHOW_EDITOR;
        case SPLIT -> TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW;
        });
        settle();
        return this;
    }

    /** The composite's current layout. */
    public Layout layout() {
        return switch (composite().getLayout()) {
        case SHOW_PREVIEW -> Layout.DESIGNER;
        case SHOW_EDITOR -> Layout.TEXT;
        case SHOW_EDITOR_AND_PREVIEW -> Layout.SPLIT;
        };
    }

    /** A change typed in the text editor: only the changed middle of the text is replaced. */
    public DesignerDriver editText(UnaryOperator<String> change) {
        String before = document.getText();
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
        int start = prefix;
        int end = before.length() - suffix;
        String replacement = after.substring(prefix, after.length() - suffix);
        WriteCommandAction.writeCommandAction(project).withName("Typing").run(() -> document.replaceString(start, end, replacement));
        settle();
        return this;
    }

    /** Change the file behind the IDE's back, as version control does, then let the IDE notice. */
    public DesignerDriver changeOnDisk(String newContent) {
        try {
            WriteAction.runAndWait(() -> file.setBinaryContent(newContent.getBytes(UTF_8), -1, System.currentTimeMillis() + 2000, this));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        settle();
        return this;
    }

    public DesignerDriver setReadOnly(boolean readOnly) {
        try {
            WriteAction.runAndWait(() -> file.setWritable(!readOnly));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        settle();
        return this;
    }

    // Observing

    public String text() {
        return document.getText();
    }

    /** Save every document, then read the file's bytes. */
    public byte[] savedBytes() {
        FileDocumentManager.getInstance().saveAllDocuments();
        try {
            return file.contentsToByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The modified marker, as the editor tab shows it: cleared again when undo returns to the saved text. */
    public boolean isModified() {
        return FileDocumentManager.getInstance().isFileModified(file);
    }

    /** "Undo <label>" as the Edit menu shows it, or {@code null} when nothing can be undone. */
    public String undoLabel() {
        UndoManager undo = UndoManager.getInstance(project);
        if (!undo.isUndoAvailable(opened)) {
            return null;
        }
        Pair<String, String> name = undo.getUndoActionNameAndDescription(opened);
        return name.first.replace("_", "");
    }

    /** "Redo <label>", or {@code null}. */
    public String redoLabel() {
        UndoManager undo = UndoManager.getInstance(project);
        if (!undo.isRedoAvailable(opened)) {
            return null;
        }
        return undo.getRedoActionNameAndDescription(opened).first.replace("_", "");
    }

    public NodeView viewOf(Object key) {
        return designer().viewOf(key);
    }

    public List<Object> selectedKeys() {
        return designer().selection();
    }

    public boolean problemShown() {
        return designer().problemMessage() != null;
    }

    public boolean readOnlyBannerShown() {
        return designer().readOnlyBannerShown();
    }

    /** The Structure view model for the open designer; disposed with the driver. */
    public StructureViewModel structure() {
        StructureViewModel model = ((TreeBasedStructureViewBuilder) composite().getStructureViewBuilder()).createStructureViewModel(null);
        Disposer.register(disposable, model);
        return model;
    }

    /** The designer's data context, as actions see it. */
    public DataContext dataContext() {
        return SimpleDataContext.builder().setParent(DataManager.getInstance().getDataContext(view()))
                .add(CommonDataKeys.PROJECT, project).add(PlatformCoreDataKeys.FILE_EDITOR, opened)
                .add(CommonDataKeys.VIRTUAL_FILE, file).build();
    }

    /** The data context of the text side, as a plain text editor's actions see it. */
    public DataContext textDataContext() {
        TextEditor text = (TextEditor) composite().getTextEditor();
        return SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(CommonDataKeys.EDITOR, text.getEditor())
                .add(PlatformCoreDataKeys.FILE_EDITOR, text).add(CommonDataKeys.VIRTUAL_FILE, file).build();
    }

    /** Runs pending event-dispatch work: the coalesced re-parse, repaints, invokeLater. */
    public void settle() {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    /** Close the editor without saving. */
    @Override
    public void close() {
        Disposer.dispose(disposable);
        FileEditorManager.getInstance(project).closeFile(file);
        if (document != null && FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
            WriteAction.runAndWait(() -> FileDocumentManager.getInstance().reloadFromDisk(document));
        }
        settle();
    }

    private JComponent view() {
        return designer().view();
    }

    private AnAction action(String actionId) {
        AnAction action = ActionManager.getInstance().getAction(actionId);
        if (action == null) {
            throw new IllegalArgumentException("No action " + actionId);
        }
        return action;
    }

    private AnActionEvent event(AnAction action) {
        return AnActionEvent.createEvent(dataContext(), action.getTemplatePresentation().clone(), "AdpTest", ActionUiKind.NONE, null);
    }

    private Point center(Object key) {
        Rectangle box = zoomed(designer().viewOf(key).bounds());
        return new Point(box.x + box.width / 2, box.y + box.height / 2);
    }

    private Rectangle zoomed(Rectangle box) {
        double zoom = designer().viewState().zoom();
        return new Rectangle((int) Math.round(box.x * zoom), (int) Math.round(box.y * zoom), (int) Math.round(box.width * zoom),
                (int) Math.round(box.height * zoom));
    }

    private void mouse(int id, Point at, int clickCount, int modifiers) {
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        view().dispatchEvent(new MouseEvent(view(), id, System.currentTimeMillis(), modifiers, at.x, at.y, clickCount, false, button));
    }

    private static <T> T find(Component component, Class<T> type) {
        if (type.isInstance(component) && component.isVisible()) {
            return type.cast(component);
        }
        if (component instanceof Container container) {
            List<Component> children = new ArrayList<>(List.of(container.getComponents()));
            for (Component child : children) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
