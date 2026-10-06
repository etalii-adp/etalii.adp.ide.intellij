package etalii.adp.fbl.family.xml;

import etalii.adp.fbl.text.Span;

/**
 * The text of an element as a slot reads it: where it is written, and whether it is html paragraphs.
 *
 * @param span where the text is written, or null when the element is self-closed
 */
record XmlTextNode(XmlElement element, Span span, boolean html) {
}
