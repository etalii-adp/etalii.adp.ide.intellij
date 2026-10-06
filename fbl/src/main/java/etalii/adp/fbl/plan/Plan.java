package etalii.adp.fbl.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import etalii.adp.fbl.Splice;
import etalii.adp.fbl.SpliceOperation;
import etalii.adp.fbl.rule.RefusedException;
import etalii.adp.fbl.text.Span;

/** The splices of one edit while it is being planned, against the body before the edit. */
public final class Plan {

    private final List<Splice> splices = new ArrayList<>();
    private boolean snapshot;

    public Plan() {
    }

    public List<Splice> splices() {
        return Collections.unmodifiableList(splices);
    }

    public boolean snapshot() {
        return snapshot;
    }

    public void setSnapshot(boolean value) {
        snapshot = value;
    }

    public void add(SpliceOperation operation, int start, int end, String text) {
        if (start == end && text.isEmpty()) {
            return;
        }
        splices.add(new Splice(operation, start, end, text));
    }

    public void add(SpliceOperation operation, Span span, String text) {
        add(operation, span.start(), span.end(), text);
    }

    /** Whether a splice already replaces bytes overlapping {@code span}. */
    public boolean touches(Span span) {
        return splices.stream().anyMatch(s -> s.start() < span.end() && span.start() < s.end());
    }

    /** Always throws {@link RefusedException}: the edit is refused with {@code reason}. */
    public static void refuse(String reason) {
        throw new RefusedException(reason);
    }

    /**
     * The splices in body order: sorted by start, those at one offset kept in the order they were
     * planned (FBL §6.5). Overlapping splices are a planning error.
     */
    public List<Splice> ordered() {
        List<Splice> ordered = new ArrayList<>(splices);
        // The sort is stable, which keeps the splices at one offset in planning order.
        ordered.sort(Comparator.comparingInt(Splice::start));
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i).start() < ordered.get(i - 1).end()) {
                throw new IllegalStateException("Two splices of one edit overlap: " + ordered.get(i - 1) + " and " + ordered.get(i) + ".");
            }
        }
        return Collections.unmodifiableList(ordered);
    }
}
