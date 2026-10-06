package etalii.adp.fbl.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.Finding;
import etalii.adp.fbl.FindingSeverity;
import etalii.adp.fbl.SourceLocation;
import etalii.adp.fbl.document.AttributeBinding;
import etalii.adp.fbl.document.BlockRule;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.HeaderSettings;
import etalii.adp.fbl.document.Rule;
import etalii.adp.fbl.document.Slot;
import etalii.adp.fbl.expression.BoundedRegex;
import etalii.adp.fbl.plan.Plan;
import etalii.adp.fbl.text.BodyText;
import etalii.adp.fbl.text.Span;
import etalii.adp.fbl.text.TextLine;

/**
 * One family's lossless reading of a body (FBL §4) and the splices it writes (FBL §6). The engine
 * does what is the same for every family: rule precedence, ids, references, containment, findings
 * and refusals; the planner does the shape of an edit.
 */
public abstract class FamilyReader {

    private final Map<String, BoundedRegex> regexes = new HashMap<>();
    private final BodyText text;
    private final FblBinding binding;
    private final FblOptions options;
    private final List<Finding> findings = new ArrayList<>();
    private Unreadable unreadable;

    protected FamilyReader(BodyText text, FblBinding binding, FblOptions options) {
        this.text = text;
        this.binding = binding;
        this.options = options;
    }

    public final BodyText text() {
        return text;
    }

    public final FblBinding binding() {
        return binding;
    }

    public final FblOptions options() {
        return options;
    }

    /** Where and why the body is unreadable (FBL §7.5), or null. */
    public final Unreadable unreadable() {
        return unreadable;
    }

    protected final void setUnreadable(Unreadable value) {
        unreadable = value;
    }

    /** The findings of the reading so far. The list is the reader's own: add to it, or use {@link #report}. */
    public final List<Finding> findings() {
        return findings;
    }

    public abstract String familyName();

    /** Every entry in document order (FBL §4.1.3). */
    public abstract List<Entry> entries();

    /** Builds the lossless reading; sets {@link #unreadable()} when the body is not well-formed. */
    public abstract void parse();

    /**
     * The leaf nodes of the reading (keys, scalars, statements, tags, comments), in body order.
     * Every byte outside them is trivia (FBL §4.1): the invariant every reader is held to, checked
     * with {@link #isTrivia} on each gap.
     */
    public abstract List<Span> leaves();

    /** Whether the bytes of {@code gap} are trivia of this family: whitespace, line endings, punctuation. */
    public abstract boolean isTrivia(Span gap);

    /** The gaps between leaves that are not trivia: empty when the reading accounts for every byte. */
    public final List<Span> unaccounted() {
        List<Span> gaps = new ArrayList<>();
        int position = text.bomLength();
        List<Span> ordered = new ArrayList<>(leaves());
        ordered.sort(Comparator.comparingInt(Span::start));
        for (Span leaf : ordered) {
            if (leaf.start() < position) {
                continue;
            }
            if (leaf.start() > position && !isTrivia(new Span(position, leaf.start()))) {
                gaps.add(new Span(position, leaf.start()));
            }
            position = Math.max(position, leaf.end());
        }
        if (position < text.length() && !isTrivia(new Span(position, text.length()))) {
            gaps.add(new Span(position, text.length()));
        }
        return Collections.unmodifiableList(gaps);
    }

    /** Whether a byte range holds only whitespace and line endings. */
    protected final boolean isWhitespace(Span span) {
        byte[] bytes = text.bytes();
        for (int i = span.start(); i < span.end(); i++) {
            byte b = bytes[i];
            if (!(b == ' ' || b == '\t' || b == '\r' || b == '\n')) {
                return false;
            }
        }
        return true;
    }

    /** The entries {@code rule} matches by its selector or line, in document order. */
    public abstract List<Candidate> candidates(Rule rule);

    public List<Candidate> blockCandidates(BlockRule block) {
        return List.of();
    }

    /**
     * Whether {@code within} admits {@code entry}, given what claimed the entries around it (blocks).
     *
     * @param within the rules or blocks the entry must lie within, or null
     * @param claimedBy the name of the rule or block that claimed an entry, or null for an unclaimed one
     */
    public boolean admits(List<String> within, Entry entry, Function<Entry, String> claimedBy) {
        return true;
    }

    /** The entries that structurally enclose {@code entry}, nearest first. */
    public List<Entry> enclosing(Entry entry) {
        List<Entry> enclosing = new ArrayList<>();
        for (Entry parent = entry.parent(); parent != null; parent = parent.parent()) {
            enclosing.add(parent);
        }
        return enclosing;
    }

    /** The entry as CEL sees it (FBL §4.1.4). May be null. */
    public abstract Object celValue(Candidate candidate);

    /** The variable a rule's CEL has besides {@code entry}, {@code parent}, {@code line} and {@code registration}: {@code path} or {@code groups}. */
    public abstract CelExtra celExtra(Candidate candidate);

    /** Reads a slot that is the family's own: a key, an attribute, text, a group, a capture. */
    public abstract SlotRead read(Candidate candidate, Slot slot);

    /** Reads the slot named {@code name} of an enclosing entry, for a {@code parent} slot naming no attribute. */
    public abstract SlotRead readRaw(Entry entry, String name);

    /** The header check (FBL §5.6): whether the mark is there with the right value. */
    public abstract boolean headerHolds(HeaderSettings header);

    /**
     * Findings about entries no rule claimed, once reading is done.
     *
     * @param claimedBy the name of the rule or block that claimed an entry, or null for an unclaimed one
     */
    public void afterRead(Function<Entry, String> claimedBy) {
    }

    // ---- writing (FBL §6) ----

    /**
     * The text that replaces a value's span: the new value in the value's own style when it fits (FBL §6.3).
     *
     * @param binding the attribute's binding, or null
     * @param value the new value, or null
     */
    public abstract String format(SlotRead read, AttributeBinding binding, Object value);

    /** Writes {@code changes} to {@code element}; the engine has already refused what must be refused. */
    public abstract void write(Plan plan, ReadElement element, List<SlotChange> changes);

    public abstract void insert(Plan plan, InsertRequest request);

    /**
     * Removes {@code element}.
     *
     * @param removed every element the edit removes, this one among them
     */
    public abstract void remove(Plan plan, ReadElement element, Set<ReadElement> removed);

    // ---- shared helpers ----

    public final String newlineAt(int offset) {
        return text.newlineAt(offset, binding.text().newline());
    }

    /** The expression compiled once per reader, bounded by the options' timeout. */
    public final BoundedRegex regex(String expression, boolean caseInsensitive) {
        String key = (caseInsensitive ? "i:" : "s:") + expression;
        BoundedRegex regex = regexes.get(key);
        if (regex == null) {
            regex = new BoundedRegex(expression, caseInsensitive, options.regexTimeout());
            regexes.put(key, regex);
        }
        return regex;
    }

    public final SourceLocation locate(Span span) {
        BodyText.Position position = text.position(span.start());
        return new SourceLocation(options.fileName(), position.line(), position.column(), text.codePoints(span.start(), span.end()));
    }

    /**
     * Adds a finding.
     *
     * @param span where the finding is, or null when it has no place
     */
    public final void report(String code, FindingSeverity severity, String message, Span span) {
        findings.add(new Finding(code, severity, message, span != null ? locate(span) : null));
    }

    /** {@code columns} indentation characters: the indentation of that many columns. */
    public final String indentation(int columns) {
        return String.valueOf(indentCharacter()).repeat(columns);
    }

    public char indentCharacter() {
        return binding.text().indent() == 0 ? '\t' : ' ';
    }

    /**
     * The indentation step (FBL §6.3): the difference between the indentation of the first parent
     * and child pair in document order, else the binding's {@code text.indent}.
     */
    public int step() {
        for (Entry entry : entries()) {
            Entry parent = entry.parent();
            if (parent != null && entry.indent() > parent.indent() && entry.lineSpan() != null && parent.lineSpan() != null) {
                return entry.indent() - parent.indent();
            }
        }
        return binding.text().indent() == 0 ? 1 : binding.text().indent();
    }

    /** Whether the bytes of the line holding {@code offset} before it are all whitespace. */
    public final boolean onlyWhitespaceBefore(int offset) {
        byte[] bytes = text.bytes();
        TextLine line = text.lines().get(text.lineIndexAt(offset));
        for (int i = Math.max(line.start(), text.bomLength()); i < offset; i++) {
            if (!(bytes[i] == ' ' || bytes[i] == '\t')) {
                return false;
            }
        }
        return true;
    }

    /** Whether only whitespace follows {@code offset} on its line. */
    public final boolean onlyTriviaAfter(int offset) {
        return onlyTriviaAfter(offset, null);
    }

    /**
     * Whether only whitespace (and a comment, when {@code comment} is given) follows {@code offset} on its line.
     *
     * @param comment the byte that starts a comment, or null
     */
    public final boolean onlyTriviaAfter(int offset, Byte comment) {
        byte[] bytes = text.bytes();
        TextLine line = text.lines().get(text.lineIndexAt(offset));
        for (int i = offset; i < line.contentEnd(); i++) {
            byte b = bytes[i];
            if (comment != null && b == comment && (i == offset || bytes[i - 1] == ' ' || bytes[i - 1] == '\t')) {
                return true;
            }
            if (!(b == ' ' || b == '\t')) {
                return false;
            }
        }
        return true;
    }
}
