package etalii.adp.fbl.document;

/**
 * How a missing container is created.
 *
 * @param at "end-of-document", "before", "after" or "under"
 * @param argument what {@code at} refers to, or null
 * @param text the container's text, or null
 */
public record CreateContainer(String at, String argument, String text) {
}
