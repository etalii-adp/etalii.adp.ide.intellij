package etalii.adp.fbl.registration;

import java.nio.file.Path;

/**
 * Where a registration's body is (FBL §8.2), or why it is not opened.
 *
 * @param path the body's path, or null when it is refused
 * @param refusal why the body is not opened, or null when it is
 */
public record BodyLocation(Path path, boolean exists, boolean isFolder, String refusal) {

    /** A missing body opens empty with {@code fbl.missing-body}, and nothing is written until the registration names a file. */
    public boolean isMissing() {
        return refusal == null && !exists;
    }
}
