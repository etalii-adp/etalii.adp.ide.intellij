package etalii.adp.freemind.ui;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;

import etalii.adp.freemind.edit.FreeMindConventions;

/**
 * New > FreeMind Mind Map (FR-014): asks for a name, writes FreeMind 1.0.1's new-map text with a
 * fresh {@code ID} and timestamps in the project's line separator, and opens it in the designer.
 */
public final class NewMindMapAction extends AnAction implements DumbAware {

    public static final String ID = "etalii.adp.freemind.NewMindMap";

    private static final String TITLE = "New FreeMind Mind Map";

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null && targetDirectory(event, false) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        VirtualFile directory = targetDirectory(event, true);
        if (project == null || directory == null) {
            return;
        }
        String name = Messages.showInputDialog(project, "Name:", TITLE, null, "mindmap", new NameValidator(directory));
        if (name == null) {
            return;
        }
        String text = FreeMindConventions.newMapText(FreeMindConventions.newId(id -> false, ThreadLocalRandom.current()),
                FreeMindConventions.now(), CodeStyle.getProjectOrDefaultSettings(project).getLineSeparator());
        VirtualFile file = WriteCommandAction.writeCommandAction(project).withName(TITLE).compute(() -> {
            try {
                VirtualFile created = directory.createChildData(this, fileName(name.trim()));
                VfsUtil.saveText(created, text);
                return created;
            } catch (IOException e) {
                Messages.showErrorDialog(project, e.getMessage(), TITLE);
                return null;
            }
        });
        if (file != null) {
            FileEditorManager.getInstance(project).openFile(file, true);
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /** The name with {@code .mm} appended when it has no such extension. */
    public static String fileName(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".mm") ? name : name + ".mm";
    }

    /** The directory chosen in the Project view, or the one holding the selected file. */
    private static @Nullable VirtualFile targetDirectory(AnActionEvent event, boolean mayAsk) {
        IdeView view = event.getData(LangDataKeys.IDE_VIEW);
        if (view != null) {
            PsiDirectory directory = mayAsk ? view.getOrChooseDirectory() : firstOrNull(view.getDirectories());
            if (directory != null) {
                return directory.getVirtualFile();
            }
        }
        VirtualFile selected = event.getData(CommonDataKeys.VIRTUAL_FILE);
        return selected == null || selected.isDirectory() ? selected : selected.getParent();
    }

    private static @Nullable PsiDirectory firstOrNull(PsiDirectory[] directories) {
        return directories.length == 0 ? null : directories[0];
    }

    /** Refuses an empty name and one whose file already exists. */
    public static final class NameValidator implements InputValidatorEx {

        private final VirtualFile directory;

        public NameValidator(VirtualFile directory) {
            this.directory = directory;
        }

        @Override
        public @Nullable String getErrorText(@NotNull String input) {
            String name = input.trim();
            if (name.isEmpty()) {
                return "Enter a name";
            }
            return directory.findChild(fileName(name)) != null ? "A file named '" + fileName(name) + "' already exists" : null;
        }

        @Override
        public boolean checkInput(@NotNull String input) {
            return getErrorText(input) == null;
        }

        @Override
        public boolean canClose(@NotNull String input) {
            return checkInput(input);
        }
    }
}
