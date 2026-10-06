package etalii.adp.fbl.support;

import java.util.function.Function;

import etalii.adp.fbl.IdRequest;

/**
 * The DISL id derivations the conformance fixtures name their elements by, standing in for DISL,
 * which this library does not implement. Each mirrors the {@code persistence.ids} of the
 * specification in etalii-adp/etalii.adp {@code definitions/diagrams/}: natural ids with the
 * type's prefix (databricks-job.dis, databricks-pipeline.dis), and the causal loop's links and
 * loops by their ends and identifier as its fixtures address them. The counterpart of standalone's
 * {@code Support/NaturalIds.cs}; it lives in test code because it names bindings.
 */
public final class NaturalIds {

    private NaturalIds() {
    }

    /**
     * The id strategy of one binding, by the binding's name: the derived id of an entry, or null
     * to address it by its place.
     */
    public static Function<IdRequest, String> forBinding(String binding) {
        return request -> switch (binding) {
            case "cld" -> switch (request.rule()) {
                case "link" -> "link:" + text(request.source()) + "|" + text(request.target());
                case "loop" -> "loop:" + attribute(request, "identifier");
                default -> null;
            };
            case "job" -> switch (request.rule()) {
                case "task" -> "task:" + attribute(request, "key");
                case "cluster" -> "cluster:" + attribute(request, "key");
                case "dependency" -> "edge:" + text(unprefixed(request.source())) + "->" + text(unprefixed(request.target()));
                default -> null;
            };
            case "settings" -> switch (request.rule()) {
                case "pipeline" -> "pipeline";
                case "library" -> "library:" + attribute(request, "path");
                default -> null;
            };
            case "freeplane" -> switch (request.rule()) {
                case "branch" -> "branch:" + text(request.source()) + "->" + text(request.target());
                default -> null;
            };
            case "workspace" -> switch (request.rule()) {
                case "relationship" -> text(request.source()) + "->" + text(request.target());
                default -> null;
            };
            default -> null;
        };
    }

    /** An attribute's value as text, empty when the request has none or it is null. */
    private static String attribute(IdRequest request, String name) {
        Object value = request.attributes().get(name);
        return switch (value) {
            case null -> "";
            case String string -> string;
            // The source writes a boolean with a capital, and a whole number held as a double without a fraction.
            case Boolean flag -> flag ? "True" : "False";
            case Double number when number == Math.rint(number) && Math.abs(number) < 1e15 -> Long.toString(number.longValue());
            default -> value.toString();
        };
    }

    /** The id without its type prefix: everything after the first colon, or all of it when it has none. */
    private static String unprefixed(String id) {
        return id == null ? null : id.substring(id.indexOf(':') + 1);
    }

    /** An absent end is written as nothing, as the source's string interpolation writes it. */
    private static String text(String value) {
        return value == null ? "" : value;
    }
}
