package etalii.adp.freemind.model;

/** A node's {@code font} element. {@code name} and {@code size} are {@code null} when absent. */
public record FontSpec(String name, Integer size, boolean bold, boolean italic) {
}
