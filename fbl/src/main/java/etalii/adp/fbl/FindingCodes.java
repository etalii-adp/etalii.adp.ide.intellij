package etalii.adp.fbl;

/** The finding codes FBL defines (FBL §7.4) and the DISL ones it uses (DISL §8.6). */
public final class FindingCodes {

    public static final String UNBOUND_STATEMENT = "fbl.unbound-statement";
    public static final String DANGLING_REFERENCE = "fbl.dangling-reference";
    public static final String HEADER_MISMATCH = "fbl.header-mismatch";
    public static final String DUPLICATE_KEY = "fbl.duplicate-key";
    public static final String MISSING_BODY = "fbl.missing-body";
    public static final String STALE_VIEW_DATA = "fbl.stale-view-data";
    public static final String UNKNOWN_HEADER = "fbl.unknown-header";
    public static final String REGEX_TIMEOUT = "fbl.regex-timeout";
    public static final String UNPARSEABLE = "std.unparseable";
    public static final String UNREADABLE_ENTRY = "std.unreadableEntry";
    public static final String MISSING_ID = "std.missingId";
    public static final String DUPLICATE_ID = "std.duplicateId";
    public static final String PLUGIN_MISSING = "std.pluginMissing";

    private FindingCodes() {
    }
}
