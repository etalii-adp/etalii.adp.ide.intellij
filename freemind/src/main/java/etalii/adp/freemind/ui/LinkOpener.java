package etalii.adp.freemind.ui;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;

import etalii.adp.freemind.model.NodeKey;

/**
 * Follows a node's {@code LINK} (spec 001 FR-018): {@code #ID} to that node in the same map, a URL
 * in the user's browser through {@link BrowserUtil}, and anything else as a file path relative to
 * the map, opened in the IDE. This is the plug-in's only way out to the network, and only on an
 * explicit click (FR-021).
 */
public final class LinkOpener {

    /** A scheme of two or more letters, so {@code C:\...} stays a path. */
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

    /** Open the link's target. A target that cannot be found is reported on the status bar. */
    public static void open(MindMapFileEditor tool, String link) {
        if (link == null || link.isBlank()) {
            return;
        }
        if (link.startsWith("#")) {
            tool.reveal(NodeKey.ofId(link.substring(1)));
            return;
        }
        URL url = asUrl(link);
        if (url != null) {
            BrowserUtil.browse(link.replace(" ", "%20"), tool.project());
            return;
        }
        openFile(tool.project(), tool.getFile(), link);
    }

    private static void openFile(Project project, VirtualFile map, String link) {
        String path = decode(link);
        VirtualFile target = null;
        VirtualFile folder = map.getParent();
        if (folder != null && !new File(path).isAbsolute()) {
            target = folder.findFileByRelativePath(path.replace('\\', '/'));
        }
        if (target == null && new File(path).isAbsolute()) {
            target = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.replace('\\', '/'));
        }
        if (target == null || target.isDirectory()) {
            StatusBar.Info.set("The linked file " + link + " does not exist", project);
            return;
        }
        FileEditorManager.getInstance(project).openFile(target, true);
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
