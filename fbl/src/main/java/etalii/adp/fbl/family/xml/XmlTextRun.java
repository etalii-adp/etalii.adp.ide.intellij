package etalii.adp.fbl.family.xml;

import etalii.adp.fbl.text.Span;

/** Character data of an element, with its span and its text with references decoded. */
record XmlTextRun(Span span, String text) {
}
