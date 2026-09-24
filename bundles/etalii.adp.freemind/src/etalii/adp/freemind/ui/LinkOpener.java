package etalii.adp.freemind.ui;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.ILog;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import etalii.adp.freemind.model.NodeKey;

/**
 * Follows a node's {@code LINK} (FR-018): a URL in the workbench's browser, {@code #ID} to that node
 * in the same map, and anything else as a file path relative to the map, in its default editor.
 */
public final class LinkOpener {

    /** A scheme of two or more letters, so {@code C:\...} stays a path. */
    private static final String TEXT_EDITOR = "org.eclipse.ui.DefaultTextEditor";

    private static final Pattern URL = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]+:.*");

    private LinkOpener() {
    }

    /** The link as a URL when it names a scheme other than {@code file}, else {@code null}. */
    public static URL asUrl(String link) {
        if (link == null || !URL.matcher(link).matches() || link.regionMatches(true, 0, "file:", 0, 5)) {
            return null;
        }
        try {
            return URI.create(link.replace(" ", "%20")).toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            return null;
        }
    }

    /** Open the link's target. Failures are logged and shown on the status line. */
    public static void open(MindMapEditor editor, String link) {
        if (link == null || link.isBlank()) {
            return;
        }
        try {
            if (link.startsWith("#")) {
                editor.reveal(NodeKey.ofId(link.substring(1)));
                return;
            }
            URL url = asUrl(link);
            if (url != null) {
                PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(url);
                return;
            }
            openFile(editor, link);
        } catch (PartInitException e) {
            ILog.of(LinkOpener.class).log(e.getStatus());
            editor.getEditorSite().getActionBars().getStatusLineManager().setErrorMessage("Cannot open " + link);
        }
    }

    private static void openFile(MindMapEditor editor, String link) throws PartInitException {
        IWorkbenchPage page = editor.getSite().getPage();
        String path = decode(link);
        IEditorInput input = editor.getEditorInput();
        if (input instanceof IFileEditorInput fileInput && !new File(path).isAbsolute()) {
            IContainer folder = fileInput.getFile().getParent();
            IFile target = folder.getFile(new org.eclipse.core.runtime.Path(path));
            if (target.exists()) {
                IDE.openEditor(page, target, true);
                return;
            }
            if (fileInput.getFile().getLocation() != null) {
                path = new File(fileInput.getFile().getLocation().toFile().getParentFile(), path).getPath();
            }
        }
        File file = new File(path);
        if (!file.isFile()) {
            editor.getEditorSite().getActionBars().getStatusLineManager().setErrorMessage("The linked file " + link + " does not exist");
            return;
        }
        IEditorDescriptor descriptor = PlatformUI.getWorkbench().getEditorRegistry().getDefaultEditor(file.getName());
        IDE.openEditor(page, file.toURI(), descriptor != null ? descriptor.getId() : TEXT_EDITOR, true);
    }

    /** {@code file:} URLs and percent-encoded relative paths, as FreeMind writes them. */
    private static String decode(String link) {
        try {
            if (link.regionMatches(true, 0, "file:", 0, 5)) {
                return new File(URI.create(link)).getPath();
            }
            if (link.contains("%")) {
                return URI.create(link).getPath();
            }
        } catch (IllegalArgumentException e) {
            // not encoded after all
        }
        return link;
    }

}
