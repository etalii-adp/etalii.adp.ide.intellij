package etalii.adp.fbl.routing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.FileRule;

/**
 * Folder subjects (FBL §10): whether a folder qualifies by {@code recognise}, and which of its files
 * the file rules select, in ordinal order of relative path. A symbolic link, or any other entry that
 * is neither a file nor a folder of its own, is neither followed nor read.
 */
public final class FolderSubject {

    private FolderSubject() {
    }

    /** An entry under a folder: its {@code /}-separated path relative to the folder, and its path. */
    private record Found(String relative, Path full) {
    }

    /** {@link #recognise(FblBinding, Path, boolean)}, ignoring case where the platform does. */
    public static boolean recognise(FblBinding binding, Path folder) {
        return recognise(binding, folder, Glob.platformIgnoresCase());
    }

    /**
     * Whether {@code folder} qualifies (FBL §10.1): every {@code all} glob matches an entry,
     * at least one {@code any} glob does when there are any, and no {@code none} glob does.
     */
    public static boolean recognise(FblBinding binding, Path folder, boolean ignoreCase) {
        Objects.requireNonNull(binding, "binding");
        if (!binding.body().isFolder() || !Files.isDirectory(folder)) {
            return false;
        }
        List<String> entries = entries(folder, true).stream().map(Found::relative).toList();
        Predicate<String> any = glob -> entries.stream().anyMatch(e -> Glob.isMatch(glob, e, ignoreCase));
        return binding.body().recogniseAll().stream().allMatch(any)
                && (binding.body().recogniseAny().isEmpty() || binding.body().recogniseAny().stream().anyMatch(any))
                && binding.body().recogniseNone().stream().noneMatch(any);
    }

    /** {@link #files(FblBinding, Path, boolean)}, ignoring case where the platform does. */
    public static List<FolderFile> files(FblBinding binding, Path folder) {
        return files(binding, folder, Glob.platformIgnoresCase());
    }

    /** The files the binding's file rules select (FBL §10.2), first matching rule each, {@code ignore} excluded. */
    public static List<FolderFile> files(FblBinding binding, Path folder, boolean ignoreCase) {
        Objects.requireNonNull(binding, "binding");
        List<FolderFile> files = new ArrayList<>();
        for (Found found : entries(folder, false)) {
            if (binding.body().ignore().stream().anyMatch(glob -> Glob.isMatch(glob, found.relative(), ignoreCase))) {
                continue;
            }
            for (FileRule rule : binding.body().files()) {
                if (Glob.isMatch(rule.glob(), found.relative(), ignoreCase)) {
                    files.add(new FolderFile(found.relative(), found.full(), rule));
                    break;
                }
            }
        }
        return List.copyOf(files);
    }

    /** Every entry under the folder, relative and {@code /}-separated, in ordinal order, never through a link. */
    private static List<Found> entries(Path folder, boolean includeDirectories) {
        List<Found> found = new ArrayList<>();
        try {
            walk(folder, "", includeDirectories, found);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        found.sort(Comparator.comparing(Found::relative));
        return found;
    }

    private static void walk(Path directory, String prefix, boolean includeDirectories, List<Found> found) throws IOException {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                BasicFileAttributes attributes = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                // A link of any kind is neither a regular file nor a directory when it is not followed.
                if (attributes.isSymbolicLink() || attributes.isOther()) {
                    continue;
                }
                String relative = prefix + entry.getFileName();
                if (attributes.isDirectory()) {
                    if (includeDirectories) {
                        found.add(new Found(relative, entry));
                    }
                    walk(entry, relative + "/", includeDirectories, found);
                } else {
                    found.add(new Found(relative, entry));
                }
            }
        }
    }
}
