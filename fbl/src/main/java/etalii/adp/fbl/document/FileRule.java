package etalii.adp.fbl.document;

/**
 * One kind of file of a folder subject (FBL §10.1).
 *
 * @param name the kind's name, or null
 * @param family the family the file is read by, or null
 */
public record FileRule(String name, String glob, Family family, boolean readOnly) {
}
