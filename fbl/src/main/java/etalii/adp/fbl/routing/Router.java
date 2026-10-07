package etalii.adp.fbl.routing;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import etalii.adp.fbl.document.Claims;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.ReadingClaim;

/**
 * Routing (FBL §12.3) and offering readings (FBL §9.4). The router returns every candidate and
 * never chooses between several on the caller's behalf.
 */
public final class Router {

    /** The part of a body {@code suggest} looks at (FBL §12.1). */
    public static final int SUGGEST_BYTES = 64 * 1024;

    private Router() {
    }

    /**
     * The bindings a bare file routes to (FBL §12.3): those whose {@code names} match the file name or
     * whose {@code extensions} include its extension, ignoring case; never a {@code registrationOnly}
     * binding; and a binding with a marker only when the marker matches, which FBL §12.1 calls the
     * mark a file needs before the binding claims it.
     */
    public static List<FblBinding> candidates(String fileName, byte[] bytes, Iterable<FblBinding> bindings) {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(bindings, "bindings");
        String name = FileNames.name(fileName);
        String extension = FileNames.extension(name);
        List<FblBinding> candidates = new ArrayList<>();
        for (FblBinding binding : bindings) {
            Claims claims = binding.claims();
            if (claims.registrationOnly()) {
                continue;
            }
            boolean named = claims.names().stream().anyMatch(glob -> Glob.isMatch(glob, name, Glob.platformIgnoresCase()));
            boolean extended = !extension.isEmpty() && claims.extensions().stream().anyMatch(e -> e.equalsIgnoreCase(extension));
            if (!named && !extended) {
                continue;
            }
            if (claims.marker() != null && !MarkerEvaluator.matches(claims.marker(), bytes)) {
                continue;
            }
            if (claims.shared() && claims.marker() == null) {
                continue;
            }
            candidates.add(binding);
        }
        return List.copyOf(candidates);
    }

    /** Whether the binding's {@code suggest} matches the body's first 64 KiB (FBL §12.3). */
    public static boolean suggests(FblBinding binding, byte[] bytes) {
        Objects.requireNonNull(binding, "binding");
        return contains(bytes, binding.claims().suggest());
    }

    /**
     * The readings a binding offers for a body (FBL §9.4): each origin in {@code claims.origins} order,
     * those whose reading's {@code suggest} matches the body first.
     */
    public static List<String> readings(FblBinding binding, byte[] bytes) {
        Objects.requireNonNull(binding, "binding");
        List<String> origins = binding.claims().origins();
        Map<String, ReadingClaim> claimed = binding.claims().readings();
        List<String> suggested = origins.stream().filter(o -> claimed.containsKey(o) && contains(bytes, claimed.get(o).suggest())).toList();
        // The other origins once each, as a set difference gives them.
        Set<String> others = new LinkedHashSet<>(origins);
        others.removeAll(suggested);
        List<String> readings = new ArrayList<>(suggested);
        readings.addAll(others);
        return List.copyOf(readings);
    }

    /**
     * The reading a bare file opens as (FBL §12.3): the one marked {@code bare}, else the binding's only origin, else none.
     *
     * @return the origin, or null when a bare file opens as none
     */
    public static String bare(FblBinding binding) {
        Objects.requireNonNull(binding, "binding");
        for (Map.Entry<String, ReadingClaim> reading : binding.claims().readings().entrySet()) {
            if (reading.getValue().bare()) {
                return reading.getKey();
            }
        }
        return binding.claims().origins().size() == 1 ? binding.claims().origins().get(0) : null;
    }

    /** Whether a reading's {@code suggest} matches the body (FBL §9.4). */
    public static boolean suggestsReading(FblBinding binding, String origin, byte[] bytes) {
        Objects.requireNonNull(binding, "binding");
        ReadingClaim reading = binding.claims().readings().get(origin);
        return reading != null && contains(bytes, reading.suggest());
    }

    private static boolean contains(byte[] bytes, List<String> substrings) {
        if (substrings.isEmpty()) {
            return false;
        }
        String head = new String(bytes, 0, Math.min(bytes.length, SUGGEST_BYTES), UTF_8);
        return substrings.stream().anyMatch(head::contains);
    }
}
