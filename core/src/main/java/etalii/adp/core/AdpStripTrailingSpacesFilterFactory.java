package etalii.adp.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.StripTrailingSpacesFilter;
import com.intellij.openapi.editor.StripTrailingSpacesFilterFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/** Saving never strips trailing spaces from a file a tool claims, so its bytes stay its own (research R5). */
public final class AdpStripTrailingSpacesFilterFactory extends StripTrailingSpacesFilterFactory {

    @Override
    public @NotNull StripTrailingSpacesFilter createFilter(@Nullable Project project, @NotNull Document document) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        return file != null && AdpEditorProvider.acceptedByAny(file) ? StripTrailingSpacesFilter.NOT_ALLOWED
                : StripTrailingSpacesFilter.ALL_LINES;
    }
}
