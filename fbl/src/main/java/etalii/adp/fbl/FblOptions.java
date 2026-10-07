package etalii.adp.fbl;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/**
 * The caller's settings for reading and writing one body.
 *
 * @param fileName the body's file name, relative to the subject, for findings
 * @param regexTimeout the bound on one regular expression match (FBL §16)
 * @param maxBodyBytes a body larger than this is unreadable rather than read in part (FBL §16)
 * @param maxEntries a body with more entries than this is unreadable rather than read in part (FBL §16)
 * @param deriveId the DISL id strategy for rules that store no id: returns the derived id, or null to
 *        address the element by its place. Null when the caller has none. FBL defines no derivation
 *        of its own (FBL §5.3)
 * @param registrationHeaders the registration's headers, visible to CEL as {@code registration}
 * @param resource the registration's {@code resource} header (FBL §8.2), or null
 * @param identities the registration's {@code identities} block, for rules with {@code id.sidecar}
 */
public record FblOptions(
        String fileName,
        Duration regexTimeout,
        int maxBodyBytes,
        int maxEntries,
        Function<IdRequest, String> deriveId,
        Map<String, String> registrationHeaders,
        String resource,
        Map<String, String> identities) {

    public static final FblOptions DEFAULT = new FblOptions("body", Duration.ofMillis(250), 32 * 1024 * 1024, 500_000, null, Map.of(), null, Map.of());

    public FblOptions withFileName(String value) {
        return new FblOptions(value, regexTimeout, maxBodyBytes, maxEntries, deriveId, registrationHeaders, resource, identities);
    }

    public FblOptions withRegexTimeout(Duration value) {
        return new FblOptions(fileName, value, maxBodyBytes, maxEntries, deriveId, registrationHeaders, resource, identities);
    }

    public FblOptions withMaxBodyBytes(int value) {
        return new FblOptions(fileName, regexTimeout, value, maxEntries, deriveId, registrationHeaders, resource, identities);
    }

    public FblOptions withMaxEntries(int value) {
        return new FblOptions(fileName, regexTimeout, maxBodyBytes, value, deriveId, registrationHeaders, resource, identities);
    }

    public FblOptions withDeriveId(Function<IdRequest, String> value) {
        return new FblOptions(fileName, regexTimeout, maxBodyBytes, maxEntries, value, registrationHeaders, resource, identities);
    }

    public FblOptions withRegistrationHeaders(Map<String, String> value) {
        return new FblOptions(fileName, regexTimeout, maxBodyBytes, maxEntries, deriveId, value, resource, identities);
    }

    public FblOptions withResource(String value) {
        return new FblOptions(fileName, regexTimeout, maxBodyBytes, maxEntries, deriveId, registrationHeaders, value, identities);
    }

    public FblOptions withIdentities(Map<String, String> value) {
        return new FblOptions(fileName, regexTimeout, maxBodyBytes, maxEntries, deriveId, registrationHeaders, resource, value);
    }
}
