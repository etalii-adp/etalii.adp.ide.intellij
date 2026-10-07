package etalii.adp.fbl.registration;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import etalii.adp.fbl.Edit;
import etalii.adp.fbl.Splice;
import etalii.adp.fbl.SpliceOperation;
import etalii.adp.fbl.history.SplicedFile;
import etalii.adp.fbl.plan.ModelChange;
import etalii.adp.fbl.plan.NewText;
import etalii.adp.fbl.plan.PlanResult;
import etalii.adp.fbl.text.BodyText;
import etalii.adp.fbl.text.Span;

/**
 * An open registration (FBL §8): its bytes, its line form, and its own history. Placing an element
 * writes its layout entry by splices, in the ordinal order of ids, with numbers in
 * {@code {decimals: 3}} form and the registration's own line ending (FBL §8.3).
 */
public final class OpenRegistration extends SplicedFile {

    private RegistrationDocument document;
    private Set<String> knownIds;
    private Predicate<String> isEphemeral;
    private Set<String> knownKeys;

    private OpenRegistration(byte[] bytes) {
        super(bytes);
        document = RegistrationDocument.read(bytes);
    }

    public RegistrationDocument document() {
        return document;
    }

    /**
     * The ids the reading has, when known; else null. A layout entry for any other id is stale and
     * is removed as part of the next placement (FBL §8.5).
     */
    public Set<String> knownIds() {
        return knownIds;
    }

    public void setKnownIds(Set<String> value) {
        knownIds = value;
    }

    /** Whether DISL marks an id ephemeral: such an element's position is never stored (FBL §8.5). Null when the caller has not said. */
    public Predicate<String> isEphemeral() {
        return isEphemeral;
    }

    public void setIsEphemeral(Predicate<String> value) {
        isEphemeral = value;
    }

    /** The natural keys the reading has, when known; else null: an {@code identities} entry for any other key is stale (FBL §8.6). */
    public Set<String> knownKeys() {
        return knownKeys;
    }

    public void setKnownKeys(Set<String> value) {
        knownKeys = value;
    }

    public static OpenRegistration open(byte[] bytes) {
        return new OpenRegistration(Objects.requireNonNull(bytes, "bytes"));
    }

    public PlanResult plan(ModelChange change) {
        switch (change) {
            case ModelChange.Save _:
                return new PlanResult.Planned(Edit.EMPTY);
            case ModelChange.Place place:
                if (isEphemeral != null && isEphemeral.test(place.id())) {
                    return new PlanResult.Refused(
                            "The position of '" + place.id() + "' is kept for this session only, because its id changes whenever the file is edited.");
                }
                return new PlanResult.Planned(new Edit(planPlace(place)));
            case ModelChange.Identify identify:
                return new PlanResult.Planned(new Edit(planIdentify(identify)));
            default:
                throw new IllegalArgumentException("A registration is changed only by placing elements and storing ids.");
        }
    }

    public PlanResult change(ModelChange change) {
        PlanResult result = plan(change);
        if (result instanceof PlanResult.Planned planned) {
            apply(planned.edit());
        }
        return result;
    }

    @Override
    protected void reread() {
        document = RegistrationDocument.read(bytes());
    }

    private List<Splice> planPlace(ModelChange.Place place) {
        String x = NewText.number(place.x(), 3);
        String y = NewText.number(place.y(), 3);
        return planEntry(document.layout(), "layout", document.afterHeaders(), knownIds, place.id(), x + " " + y, existing -> {
            BodyText text = document.text();
            List<Span> parts = numbers(text, existing.valueSpan());
            if (parts.size() != 2) {
                return List.of(new Splice(SpliceOperation.REPLACE_VALUE, existing.valueSpan().start(), existing.valueSpan().end(), x + " " + y));
            }
            List<Splice> splices = new ArrayList<>();
            if (!text.text(parts.get(0)).equals(x)) {
                splices.add(new Splice(SpliceOperation.REPLACE_VALUE, parts.get(0).start(), parts.get(0).end(), x));
            }
            if (!text.text(parts.get(1)).equals(y)) {
                splices.add(new Splice(SpliceOperation.REPLACE_VALUE, parts.get(1).start(), parts.get(1).end(), y));
            }
            return splices;
        });
    }

    private List<Splice> planIdentify(ModelChange.Identify identify) {
        int after = document.layout() != null ? document.layout().span().end() : document.afterHeaders();
        return planEntry(document.identities(), "identities", after, knownKeys, identify.key(), identify.id(), existing ->
                document.text().text(existing.valueSpan()).equals(identify.id())
                        ? List.of()
                        : List.of(new Splice(SpliceOperation.REPLACE_VALUE, existing.valueSpan().start(), existing.valueSpan().end(), identify.id())));
    }

    /**
     * Writes one entry of a block (FBL §8.3, §8.6): its value replaced in place when it exists, else
     * inserted in the ordinal order of keys, creating the block at {@code createAt} first; stale
     * entries (keys not in {@code known}) are removed in the same edit (FBL §8.5).
     *
     * @param block the block as it is read, or null when the registration has none
     * @param known the keys the reading has, or null when they are not known
     */
    private List<Splice> planEntry(RegistrationBlock block, String name, int createAt, Set<String> known, String key, String value,
            Function<RegistrationEntry, List<Splice>> replace) {
        BodyText text = document.text();
        List<Splice> splices = new ArrayList<>();
        List<RegistrationEntry> stale = block == null || known == null
                ? List.of()
                : block.entries().stream().filter(e -> !e.key().equals(key) && !known.contains(e.key())).toList();
        for (RegistrationEntry entry : stale) {
            splices.add(new Splice(SpliceOperation.REMOVE_ENTRY, entry.line().start(), entry.line().end(), ""));
        }
        RegistrationEntry existing = block == null ? null : block.entries().stream().filter(e -> e.key().equals(key)).findFirst().orElse(null);
        if (existing != null) {
            splices.addAll(replace.apply(existing));
            return ordered(splices);
        }
        String lastEnding = text.lines().get(text.lines().size() - 1).ending();
        if (block == null) {
            int offset = createAt;
            String newline = text.newlineAt(Math.max(0, offset - 1), "\n");
            String lead = offset == text.length() && lastEnding.isEmpty() && offset > 0 ? newline : "";
            if (!lead.isEmpty()) {
                splices.add(new Splice(SpliceOperation.ENSURE_CONTAINER, offset, offset, lead + name + ":"));
                splices.add(new Splice(SpliceOperation.INSERT_ENTRY, offset, offset, newline + "  " + key + ": " + value));
            } else {
                splices.add(new Splice(SpliceOperation.ENSURE_CONTAINER, offset, offset, name + ":" + newline));
                splices.add(new Splice(SpliceOperation.INSERT_ENTRY, offset, offset, "  " + key + ": " + value + newline));
            }
            return splices;
        }
        // An entry equal to a stale one is left out too, as a set difference leaves it out.
        List<RegistrationEntry> kept = block.entries().stream().filter(e -> !stale.contains(e)).distinct().toList();
        RegistrationEntry next = kept.stream().filter(e -> compareUtf8(e.key(), key) > 0).findFirst().orElse(null);
        String indent = " ".repeat(kept.isEmpty() ? 2 : kept.get(0).indent());
        int at = next != null ? next.line().start() : !kept.isEmpty() ? kept.get(kept.size() - 1).line().end() : block.nameLine().end();
        String ending = text.newlineAt(Math.max(0, at - 1), "\n");
        String entryText = at == text.length() && lastEnding.isEmpty()
                ? ending + indent + key + ": " + value
                : indent + key + ": " + value + ending;
        splices.add(new Splice(SpliceOperation.INSERT_ENTRY, at, at, entryText));
        return ordered(splices);
    }

    /** By start, those at one offset in the order they were planned: the sort is stable. */
    private static List<Splice> ordered(List<Splice> splices) {
        List<Splice> ordered = new ArrayList<>(splices);
        ordered.sort(Comparator.comparingInt(Splice::start));
        return ordered;
    }

    private static List<Span> numbers(BodyText text, Span value) {
        List<Span> spans = new ArrayList<>();
        byte[] bytes = text.bytes();
        int i = value.start();
        while (i < value.end()) {
            while (i < value.end() && bytes[i] == ' ') {
                i++;
            }
            int start = i;
            while (i < value.end() && bytes[i] != ' ') {
                i++;
            }
            if (i > start) {
                spans.add(new Span(start, i));
            }
        }
        return spans;
    }

    /** Ordinal order of ids: byte order of their UTF-8 encoding (FBL §8.3). */
    public static int compareUtf8(String a, String b) {
        return Arrays.compareUnsigned(a.getBytes(UTF_8), b.getBytes(UTF_8));
    }
}
