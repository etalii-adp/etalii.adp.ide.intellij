package etalii.adp.fbl.document;

import java.util.List;

/**
 * Where a binding's model lives (FBL §4, §10).
 *
 * @param family the format family, or null for a folder subject or a plugin-read body without one
 * @param settle the milliseconds a folder subject waits for changes to settle; 400 unless the binding says
 */
public record BodySettings(
        boolean isFolder,
        Family family,
        List<Family> alsoRead,
        List<String> recogniseAll,
        List<String> recogniseAny,
        List<String> recogniseNone,
        List<FileRule> files,
        List<String> ignore,
        int settle) {

    public static final int DEFAULT_SETTLE = 400;

    public BodySettings {
        alsoRead = List.copyOf(alsoRead);
        recogniseAll = List.copyOf(recogniseAll);
        recogniseAny = List.copyOf(recogniseAny);
        recogniseNone = List.copyOf(recogniseNone);
        files = List.copyOf(files);
        ignore = List.copyOf(ignore);
    }
}
