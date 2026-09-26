package etalii.adp.freemind.model;

import etalii.adp.core.xml.Range;

import java.util.Map;

/**
 * Where a node sits in the text it was parsed from. Valid only against that text.
 *
 * @param element             the whole element; when it is alone on its lines, it also spans the
 *                            leading whitespace and the trailing line separator, so delete and
 *                            move take whole lines
 * @param startTag            the {@code <node ...>} tag
 * @param attributes          each attribute of the start tag, in file order
 * @param attributeInsertPoint just after the last attribute, where a new last attribute goes
 * @param selfClosing         the start tag ends in {@code />}
 * @param childInsertPoint    where a new last child goes: the {@code />} of a self-closing node,
 *                            else the start of the closing tag's line when the tag begins its
 *                            line, else the closing tag itself
 * @param childInsertOwnLine  the closing tag begins its line, so children go in as whole lines
 * @param richNode            the {@code richcontent TYPE="NODE"} element, widened like
 *                            {@code element}, or {@code null}
 * @param indent              the whitespace before the start tag on its line, when it begins it
 * @param ownLine             the element is alone on its lines
 */
public record NodeRanges(Range element, Range startTag, Map<String, AttributeRange> attributes, int attributeInsertPoint,
        boolean selfClosing, int childInsertPoint, boolean childInsertOwnLine, Range richNode, String indent,
        boolean ownLine) {

    public AttributeRange attribute(String name) {
        return attributes.get(name);
    }
}
