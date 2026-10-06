package etalii.adp.fbl.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.document.BindingReader;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.HeaderSettings;
import etalii.adp.fbl.document.Rule;
import etalii.adp.fbl.document.Slot;
import etalii.adp.fbl.expression.CelMap;
import etalii.adp.fbl.family.json.JsonValue;
import etalii.adp.fbl.plan.Plan;
import etalii.adp.fbl.text.BodyText;
import etalii.adp.fbl.text.Span;

/** What yaml and json share: selectors, CEL values and reading keys (FBL §4.2, §4.1.4, §5.2). */
public abstract class TreeFamily extends FamilyReader {

    /** Every entry in document order, the root first; filled by {@link #index}. */
    protected final List<Entry> allEntries = new ArrayList<>();

    private TreeEntry root;

    protected TreeFamily(BodyText text, FblBinding binding, FblOptions options) {
        super(text, binding, options);
    }

    /** The document root, or null before the body is parsed and when it is unreadable. */
    public final TreeEntry root() {
        return root;
    }

    protected final void setRoot(TreeEntry value) {
        root = value;
    }

    @Override
    public List<Entry> entries() {
        return Collections.unmodifiableList(allEntries);
    }

    @Override
    public List<Candidate> candidates(Rule rule) {
        List<Candidate> candidates = new ArrayList<>();
        if (rule.at() == null || root == null) {
            return candidates;
        }
        for (Selector.Match match : Selector.match(root, rule.at())) {
            candidates.add(new Candidate(rule, null, match.entry(), match.captures()));
        }
        return candidates;
    }

    @Override
    public List<Entry> enclosing(Entry entry) {
        List<Entry> enclosing = new ArrayList<>();
        for (Entry parent = entry.parent(); parent != null; parent = parent.parent()) {
            if (parent instanceof TreeEntry tree && tree.isRoot()) {
                break;
            }
            enclosing.add(parent);
        }
        return enclosing;
    }

    @Override
    public Object celValue(Candidate candidate) {
        return cel(((TreeEntry) candidate.entry()).value());
    }

    @Override
    public CelExtra celExtra(Candidate candidate) {
        CelMap path = new CelMap();
        path.putAll(candidate.captures());
        return new CelExtra("path", path);
    }

    /**
     * The value as CEL sees it (FBL §4.1.4): a {@link CelMap} for a mapping, a list for a sequence,
     * and for a scalar a String, a Long, a Double, a Boolean or null.
     */
    public static Object cel(TreeValue value) {
        switch (value.kind()) {
            case MAPPING -> {
                CelMap map = new CelMap();
                for (TreeValue merged : value.merged()) {
                    if (cel(merged) instanceof CelMap inherited) {
                        map.putAll(inherited);
                    }
                }
                for (TreeEntry member : value.entries()) {
                    if (member.name() != null && !member.name().equals("<<")) {
                        map.put(member.name(), cel(member.value()));
                    }
                }
                return map;
            }
            case SEQUENCE -> {
                List<Object> list = new ArrayList<>();
                for (TreeEntry item : value.entries()) {
                    list.add(cel(item.value()));
                }
                return list;
            }
            default -> {
                return value.flow() != null ? value.flow() : value.typed();
            }
        }
    }

    @Override
    public boolean headerHolds(HeaderSettings header) {
        if (header.key() == null) {
            return true;
        }
        TreeEntry member = root == null ? null : root.value().member(header.key());
        if (member == null) {
            return false;
        }
        JsonValue expected = header.value();
        if (expected == null) {
            return true;
        }
        return BindingReader.scalarText(expected).equals(member.value().text());
    }

    /**
     * The mapping a slot is read in: the entry's own, or the one {@code child} reaches from it. Null when there is none.
     *
     * @param child the selector of the slot's {@code child}, or null
     */
    protected final TreeValue mapping(TreeEntry entry, String child) {
        if (child == null) {
            return entry.value().kind() == ValueKind.MAPPING ? entry.value() : null;
        }
        List<Selector.Match> matches = Selector.match(entry, child);
        if (!matches.isEmpty() && matches.get(0).entry() instanceof TreeEntry reached && reached.value().kind() == ValueKind.MAPPING) {
            return reached.value();
        }
        return null;
    }

    @Override
    public SlotRead read(Candidate candidate, Slot slot) {
        TreeEntry entry = (TreeEntry) candidate.entry();
        String capture = slot.capture();
        if (capture != null) {
            String key = candidate.captures().get(capture);
            if (key == null) {
                return SlotRead.ABSENT;
            }
            for (Entry e = entry; e instanceof TreeEntry tree; e = e.parent()) {
                if (key.equals(tree.name()) && tree.keySpan() != null) {
                    return new SlotRead(key, tree.keySpan(), true, true).withNode(tree);
                }
            }
            return new SlotRead(key, null, true, false, "The captured key cannot be found to rewrite.");
        }
        String name = slot.key();
        if (name == null) {
            return SlotRead.readOnlyAbsent("A " + familyName() + " entry has no " + slot + ".");
        }
        TreeValue mapping = mapping(entry, slot.child());
        if (mapping == null) {
            return SlotRead.ABSENT;
        }
        return readMember(mapping, name);
    }

    protected static SlotRead readMember(TreeValue mapping, String name) {
        TreeEntry member = mapping.member(name);
        if (member != null) {
            TreeValue value = member.value();
            boolean writable = !value.viaAlias() && value.kind() == ValueKind.SCALAR;
            String reason = value.viaAlias() ? "The value is reached through an alias, so it is read-only."
                    : writable ? null : "The value is a block collection, which a slot does not write.";
            return new SlotRead(cel(value), value.span(), true, writable, reason, member, null, null, value.kind() == ValueKind.SCALAR ? value.text() : null);
        }
        for (TreeValue merged : mapping.merged()) {
            TreeEntry inherited = merged.member(name);
            if (inherited != null) {
                return new SlotRead(cel(inherited.value()), inherited.value().span(), true, false, "The value is reached through a merge key, so it is read-only.")
                        .withNode(inherited);
            }
        }
        return SlotRead.ABSENT.withNode(mapping);
    }

    @Override
    public SlotRead readRaw(Entry entry, String name) {
        return entry instanceof TreeEntry tree && tree.value().kind() == ValueKind.MAPPING ? readMember(tree.value(), name) : SlotRead.ABSENT;
    }

    /**
     * The container entry an insert's {@code container} selector names, from the root or the parent entry. Null when there is none.
     *
     * @param parent the entry a relative selector starts from, or null
     * @param captures the captures that resolve the selector's {@code {capture}} segments
     */
    protected final TreeEntry container(String selector, Entry parent, Map<String, String> captures) {
        if (root == null) {
            return null;
        }
        String[] segments = selector.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            String s = segments[i];
            if (s.startsWith("{") && s.endsWith("}")) {
                String v = captures.get(s.substring(1, s.length() - 1));
                if (v != null) {
                    segments[i] = v;
                }
            }
        }
        String resolved = String.join("/", segments);
        if (resolved.startsWith("/") && resolved.chars().allMatch(c -> c == '/')) {
            return root;
        }
        Entry start = Selector.start(resolved, root, parent);
        List<Selector.Match> matches = Selector.match(start, resolved);
        return !matches.isEmpty() && matches.get(0).entry() instanceof TreeEntry tree ? tree : null;
    }

    /** The captures the binding's existing entries bound, so {@code {capture}} segments of a container resolve (FBL §6.2). */
    protected static Map<String, String> capturesOf(Plan plan) {
        for (ReadElement element : plan.reading().elements()) {
            Map<String, String> captures = element.candidate().captures();
            if (!captures.isEmpty()) {
                return captures;
            }
        }
        return Map.of();
    }

    /** Adds {@code entry} and every entry under it to the entries, in document order, each with its parent and children set. */
    protected final void index(TreeEntry entry) {
        allEntries.add(entry);
        for (TreeEntry child : entry.value().entries()) {
            child.setParent(entry);
            entry.children().add(child);
            index(child);
        }
    }
}
