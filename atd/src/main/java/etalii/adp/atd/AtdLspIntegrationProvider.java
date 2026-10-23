package etalii.adp.atd;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.lsp.api.LspIntegrationProvider;

/**
 * Starts the project's {@code atd-lsp} when an {@code .atd} file opens (etalii.adp spec 035, T117). Registered in
 * {@code adp-atd.xml}, which the plug-in loads only in an IDE with {@code com.intellij.modules.lsp}.
 */
public final class AtdLspIntegrationProvider implements LspIntegrationProvider {

    @Override
    public void fileOpened(@NotNull Project project, @NotNull VirtualFile file, @NotNull LspClientStarter clientStarter) {
        if ("atd".equals(file.getExtension()) && AtdServerBinary.path() != null) {
            clientStarter.ensureClientStarted(new AtdLspClientDescriptor(project));
        }
    }
}
