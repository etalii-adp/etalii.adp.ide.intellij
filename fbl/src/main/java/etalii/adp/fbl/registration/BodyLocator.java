package etalii.adp.fbl.registration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.routing.FileNames;
import etalii.adp.fbl.routing.Glob;

/**
 * Finds a registration's body (FBL §8.2): the {@code body} header relative to the registration's
 * folder, else the sibling with the registration's base name and the first existing claimed
 * extension, else the registration's folder for a folder binding. A body outside the workspace root,
 * or reached through a symbolic link, is refused (FBL §16).
 */
public final class BodyLocator {

    private BodyLocator() {
    }

    /**
     * @param registrationPath the registration's own path
     * @param workspaceRoot the folder nothing is read or written outside of
     */
    public static BodyLocation locate(Path registrationPath, RegistrationDocument registration, FblBinding binding, Path workspaceRoot) {
        Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(binding, "binding");
        Path registrationFull = registrationPath.toAbsolutePath().normalize();
        Path folder = registrationFull.getParent();
        Path root = workspaceRoot.toAbsolutePath().normalize();
        boolean isFolder = binding.body().isFolder();
        Path candidate;
        String body = registration.body();
        if (body != null) {
            Path relative;
            try {
                relative = Path.of(body);
            } catch (InvalidPathException e) {
                // A name this platform cannot give a file is the name of no file: the body is missing.
                return new BodyLocation(null, false, isFolder, null);
            }
            if (body.startsWith("/") || relative.getRoot() != null) {
                return new BodyLocation(null, false, false, "The registration's body is an absolute path, which is never followed.");
            }
            candidate = folder.resolve(relative).normalize();
        } else if (isFolder) {
            candidate = folder;
        } else {
            String baseName = FileNames.baseName(registrationFull.getFileName().toString());
            candidate = null;
            for (String extension : binding.claims().extensions()) {
                Path sibling = folder.resolve(baseName + extension);
                if (Files.isRegularFile(sibling)) {
                    candidate = sibling;
                    break;
                }
            }
            if (candidate == null) {
                candidate = folder.resolve(baseName + (binding.claims().extensions().isEmpty() ? "" : binding.claims().extensions().get(0)));
            }
        }
        if (!within(root, candidate)) {
            return new BodyLocation(null, false, false, "The registration's body is outside the workspace, so it is not opened without the user's consent.");
        }
        if (throughLink(root, candidate)) {
            return new BodyLocation(null, false, false, "The registration's body is reached through a link, which is not followed without the user's consent.");
        }
        boolean exists = isFolder ? Files.isDirectory(candidate) : Files.isRegularFile(candidate);
        return new BodyLocation(candidate, exists, isFolder, null);
    }

    private static boolean within(Path root, Path path) {
        boolean ignoreCase = Glob.platformIgnoresCase();
        String rootText = root.toString();
        String pathText = path.toString();
        String prefix = rootText.endsWith(File.separator) ? rootText : rootText + File.separator;
        return (ignoreCase ? pathText.equalsIgnoreCase(rootText) : pathText.equals(rootText))
                || pathText.regionMatches(ignoreCase, 0, prefix, 0, prefix.length());
    }

    /**
     * Whether any existing component from below the root to the path itself is a symbolic link, or
     * what exists of the path is really somewhere outside the root, as it is behind a link of
     * another kind.
     */
    private static boolean throughLink(Path root, Path path) {
        for (Path current = path; current != null && current.getNameCount() > root.getNameCount(); current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        Path existing = path;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null || !within(root, existing)) {
            return false;
        }
        try {
            return !within(root.toRealPath(), existing.toRealPath());
        } catch (IOException e) {
            // What cannot be resolved cannot be shown to stay within the root.
            return true;
        }
    }
}
