package etalii.adp.core.xml;

/**
 * One attribute of an {@link XmlElement}.
 *
 * @param start the offset of the whitespace before its name, so removing {@code start..end()} leaves no gap
 * @param valueRange the value as written, without the quotes
 * @param quote the quote character it is written with
 * @param value the value decoded as XML reads it
 */
public record XmlAttribute(String name, int start, Range valueRange, char quote, String value) {

    /** Just after the closing quote. */
    public int end() {
        return valueRange.end() + 1;
    }
}
