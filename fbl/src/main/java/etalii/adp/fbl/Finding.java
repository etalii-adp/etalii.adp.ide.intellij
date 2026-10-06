package etalii.adp.fbl;

/**
 * A problem found while reading a body or a registration (FBL §7.4). Reading never fails on content:
 * whatever it cannot read becomes one of these. {@code location} is null when the finding has no place.
 */
public record Finding(String code, FindingSeverity severity, String message, SourceLocation location) {

    @Override
    public String toString() {
        return location == null ? code + ": " + message : code + " at " + location + ": " + message;
    }
}
