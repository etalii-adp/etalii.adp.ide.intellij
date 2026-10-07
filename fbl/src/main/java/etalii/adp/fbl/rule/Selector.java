package etalii.adp.fbl.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FBL §4.2 selectors over the entries of a tree (yaml, json) or of an xml document: keys and names,
 * {@code *}, {@code **}, {@code {capture}} and {@code name[@A='v']}, absolute from the root or relative
 * to an entry.
 */
public final class Selector {

    private Selector() {
    }

    /** An entry a selector reached, with the captures it bound on the way, by name. */
    public record Match(Entry entry, Map<String, String> captures) {
    }

    /** Whether an entry has an attribute with a value, for {@code name[@A='v']}. */
    @FunctionalInterface
    public interface AttributeEquals {

        boolean test(Entry entry, String attribute, String value);
    }

    /** The entries {@code selector} reaches from {@code from}, in document order, each with its captures. No attribute predicate holds. */
    public static List<Match> match(Entry from, String selector) {
        return match(from, selector, null);
    }

    /**
     * The entries {@code selector} reaches from {@code from}, in document order, each with its captures.
     *
     * @param attributeEquals decides an attribute predicate; null when none can hold
     */
    public static List<Match> match(Entry from, String selector, AttributeEquals attributeEquals) {
        List<Segment> segments = new ArrayList<>();
        for (String text : selector.split("/")) {
            if (!text.isEmpty()) {
                segments.add(Segment.parse(text));
            }
        }
        List<Match> results = new ArrayList<>();
        walk(from, 0, new LinkedHashMap<>(), segments, attributeEquals, results, new HashSet<>());
        // The sort is stable: an entry before the entries it contains, the rest as they were found.
        results.sort(Comparator.comparingInt((Match r) -> r.entry().own().start())
                .thenComparing(Comparator.comparingInt((Match r) -> r.entry().own().end()).reversed()));
        return Collections.unmodifiableList(results);
    }

    private static void walk(Entry entry, int index, Map<String, String> captures, List<Segment> segments, AttributeEquals attributeEquals,
            List<Match> results, Set<Entry> seen) {
        if (index == segments.size()) {
            if (seen.add(entry)) {
                results.add(new Match(entry, new LinkedHashMap<>(captures)));
            }
            return;
        }
        Segment segment = segments.get(index);
        if (segment.kind() == SegmentKind.DESCENDANTS) {
            walk(entry, index + 1, captures, segments, attributeEquals, results, seen);
            for (Entry child : entry.children()) {
                walk(child, index, captures, segments, attributeEquals, results, seen);
            }
            return;
        }
        for (Entry child : entry.children()) {
            switch (segment.kind()) {
                case ANY -> walk(child, index + 1, captures, segments, attributeEquals, results, seen);
                case CAPTURE -> {
                    if (child.name() != null) {
                        Map<String, String> inner = new LinkedHashMap<>(captures);
                        inner.put(segment.name(), child.name());
                        walk(child, index + 1, inner, segments, attributeEquals, results, seen);
                    }
                }
                case NAME -> {
                    if (!segment.name().equals(child.name())) {
                        break;
                    }
                    if (segment.attribute() != null && (attributeEquals == null || !attributeEquals.test(child, segment.attribute(), segment.value()))) {
                        break;
                    }
                    walk(child, index + 1, captures, segments, attributeEquals, results, seen);
                }
                case DESCENDANTS -> {
                }
            }
        }
    }

    /**
     * The root a selector starts from: the document root for an absolute selector, else {@code relativeTo}.
     *
     * @param relativeTo the entry a relative selector starts from, or null
     */
    public static Entry start(String selector, Entry root, Entry relativeTo) {
        return selector.startsWith("/") || relativeTo == null ? root : relativeTo;
    }

    private enum SegmentKind {
        NAME,
        ANY,
        DESCENDANTS,
        CAPTURE,
    }

    private record Segment(SegmentKind kind, String name, String attribute, String value) {

        static Segment parse(String text) {
            if (text.equals("*")) {
                return new Segment(SegmentKind.ANY, "*", null, null);
            }
            if (text.equals("**")) {
                return new Segment(SegmentKind.DESCENDANTS, "**", null, null);
            }
            if (text.startsWith("{") && text.endsWith("}")) {
                return new Segment(SegmentKind.CAPTURE, text.substring(1, text.length() - 1), null, null);
            }
            int bracket = text.indexOf("[@");
            if (bracket > 0 && text.endsWith("']")) {
                String predicate = text.substring(bracket + 2, text.length() - 1);
                int equals = predicate.indexOf("='");
                if (equals > 0) {
                    return new Segment(SegmentKind.NAME, text.substring(0, bracket), predicate.substring(0, equals),
                            predicate.substring(equals + 2, predicate.length() - 1));
                }
            }
            return new Segment(SegmentKind.NAME, text, null, null);
        }
    }
}
