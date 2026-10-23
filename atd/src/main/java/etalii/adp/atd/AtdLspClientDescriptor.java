package etalii.adp.atd;

import java.nio.file.Path;

import org.jetbrains.annotations.NotNull;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor;

/**
 * One {@code atd-lsp} per project, over stdio, for every {@code .atd} file of it (etalii.adp spec 035, T117): the
 * server keeps the descriptions the open files belong to and checks each again when one of its files changes.
 */
final class AtdLspClientDescriptor extends ProjectWideLspClientDescriptor {

    AtdLspClientDescriptor(@NotNull Project project) {
        super(project, "ATD");
    }

    @Override
    public boolean isSupportedFile(@NotNull VirtualFile file) {
        return "atd".equals(file.getExtension());
    }

    @Override
    public @NotNull GeneralCommandLine createCommandLine() throws ExecutionException {
        Path binary = AtdServerBinary.path();
        if (binary == null) {
            throw new ExecutionException("The ADP plug-in has no atd-lsp for this platform.");
        }
        return new GeneralCommandLine(binary.toString()).withWorkDirectory(getProject().getBasePath());
    }
}
