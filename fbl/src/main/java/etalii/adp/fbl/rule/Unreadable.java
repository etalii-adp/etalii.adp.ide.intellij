package etalii.adp.fbl.rule;

/** Where and why a body is unreadable (FBL §7.5): the byte offset and the message. */
public record Unreadable(int offset, String message) {
}
