package etalii.adp.freemind.ui;

import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import etalii.adp.core.AdpDesignerEditor;
import etalii.adp.core.AdpEditorProvider;
import etalii.adp.freemind.FreeMindSniffer;

/**
 * Opens FreeMind maps in the designer (FR-002, FR-003): a {@code .mm} file whose start
 * {@link FreeMindSniffer} recognises. Other {@code .mm} files are left to the editor the IDE would
 * use without the plug-in.
 */
public final class MindMapEditorProvider extends AdpEditorProvider {

    public static final String EDITOR_TYPE_ID = "etalii.adp.freemind.editor";

    @Override
    protected Set<String> extensions() {
        return Set.of("mm");
    }

    @Override
    protected boolean sniff(byte[] head) {
        return FreeMindSniffer.isFreeMind(head);
    }

    @Override
    protected AdpDesignerEditor<?> createDesigner(Project project, VirtualFile file, Document document) {
        return new MindMapDesigner(project, file, document);
    }

    @Override
    protected String editorName() {
        return "FreeMind Mind Map";
    }

    @Override
    public @NotNull String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }
}
