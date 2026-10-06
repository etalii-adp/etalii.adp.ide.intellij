package etalii.adp.fbl.document;

/** The five format families of FBL §4. */
public enum Family {
    YAML("yaml"),
    JSON("json"),
    XML("xml"),
    LINES("lines"),
    BLOCKS("blocks");

    private final String fblName;

    Family(String fblName) {
        this.fblName = fblName;
    }

    /** The family's name as FBL writes it. */
    public String fblName() {
        return fblName;
    }

    /** The family of that name, or null. */
    public static Family parse(String name) {
        for (Family family : values()) {
            if (family.fblName.equals(name)) {
                return family;
            }
        }
        return null;
    }
}
