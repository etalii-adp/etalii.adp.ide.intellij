package etalii.adp.fbl.family.xml;

import etalii.adp.fbl.text.Span;

/** An attribute as written (FBL §4.5): its own span from name to closing quote, and its value span between the quotes. */
record XmlAttribute(String name, Span own, Span value, String text) {
}
