package etalii.adp.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Opens a format's files in its designer, paired with the platform's text editor on the same
 * document (research R2, R3). A file is claimed only when its extension is the format's and the
 * start of its content is the format, so other files with the same extension are left alone.
 */
public abstract class AdpEditorProvider implements FileEditorProvider, DumbAware {

    /** How much of a file the sniff reads, at most. */
    public static final int SNIFF_LIMIT = 4096;

    /** File extensions this format may claim, lower case, without the dot. */
    protected abstract Set<String> extensions();

    /** True when the start of the file (at most {@link #SNIFF_LIMIT} bytes) is this format. Must not throw. */
    protected abstract boolean sniff(byte[] head);

    /** The designer for one opened file. */
    protected abstract AdpDesignerEditor<?> createDesigner(Project project, VirtualFile file, Document document);

    /** The name shown on the composite editor. */
    protected abstract String editorName();

    @Override
    public abstract @NotNull String getEditorTypeId();

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return accepts(file);
    }

    /** {@link #accept} without a project, for callers that have none. */
    public boolean accepts(VirtualFile file) {
        String extension = file.getExtension();
        return !file.isDirectory() && extension != null && extensions().contains(extension.toLowerCase(Locale.ROOT))
                && sniff(head(file));
    }

    @Override
    public boolean acceptRequiresReadAction() {
        return false;
    }

    private static byte[] head(VirtualFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SNIFF_LIMIT);
        } catch (IOException | RuntimeException e) {
            return new byte[0];
        }
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        TextEditor text = (TextEditor) TextEditorProvider.getInstance().createEditor(project, file);
        AdpDesignerEditor<?> designer = createDesigner(project, file, text.getEditor().getDocument());
        Composite composite = new Composite(text, designer, editorName());
        designer.start(() -> composite.setLayoutUnremembered(TextEditorWithPreview.Layout.SHOW_EDITOR));
        if (designer.model() == null) {
            composite.setLayoutUnremembered(TextEditorWithPreview.Layout.SHOW_EDITOR);
        }
        return composite;
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }

    /** True when any registered ADP provider claims the file. */
    public static boolean acceptedByAny(VirtualFile file) {
        return FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getExtensionList().stream()
                .anyMatch(provider -> provider instanceof AdpEditorProvider adp && adp.accepts(file));
    }

    /** The text editor and the designer; the designer alone by default. */
    public static final class Composite extends TextEditorWithPreview {

        private final AdpDesignerEditor<?> designer;

        Composite(TextEditor text, AdpDesignerEditor<?> designer, String name) {
            super(text, designer, name, Layout.SHOW_PREVIEW);
            this.designer = designer;
        }

        public AdpDesignerEditor<?> designer() {
            return designer;
        }

        /**
         * Switch the layout without making it the one the next editor of this kind opens in, as a
         * user's own switch would. Used when a problem sends the user to the text.
         */
        public void setLayoutUnremembered(Layout layout) {
            // simplified: relies on the platform keeping the remembered layout under "<name>Layout";
            // if a platform release renames it, the switch is remembered, which is the platform's own default.
            PropertiesComponent properties = PropertiesComponent.getInstance();
            String key = getName() + "Layout";
            String remembered = properties.getValue(key);
            getComponent();
            setLayout(layout);
            properties.setValue(key, remembered);
        }

        /** The designer's Structure view, not the text's (research R8). */
        @Override
        public StructureViewBuilder getStructureViewBuilder() {
            return designer.getStructureViewBuilder();
        }
    }
}
