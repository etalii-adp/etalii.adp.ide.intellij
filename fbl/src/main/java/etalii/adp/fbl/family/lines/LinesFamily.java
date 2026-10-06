package etalii.adp.fbl.family.lines;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.FindingCodes;
import etalii.adp.fbl.FindingSeverity;
import etalii.adp.fbl.SpliceOperation;
import etalii.adp.fbl.document.AttributeBinding;
import etalii.adp.fbl.document.BlockRule;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.HeaderSettings;
import etalii.adp.fbl.document.InsertSettings;
import etalii.adp.fbl.document.Rule;
import etalii.adp.fbl.document.Slot;
import etalii.adp.fbl.expression.BoundedRegex;
import etalii.adp.fbl.expression.CelMap;
import etalii.adp.fbl.expression.RegexMatch;
import etalii.adp.fbl.expression.RegexTimeoutException;
import etalii.adp.fbl.plan.EmitPart;
import etalii.adp.fbl.plan.NewText;
import etalii.adp.fbl.plan.Plan;
import etalii.adp.fbl.rule.BodyReading;
import etalii.adp.fbl.rule.Candidate;
import etalii.adp.fbl.rule.CelExtra;
import etalii.adp.fbl.rule.Entry;
import etalii.adp.fbl.rule.FamilyReader;
import etalii.adp.fbl.rule.InsertRequest;
import etalii.adp.fbl.rule.ReadElement;
import etalii.adp.fbl.rule.SlotChange;
import etalii.adp.fbl.rule.SlotRead;
import etalii.adp.fbl.rule.Unreadable;
import etalii.adp.fbl.rule.Word;
import etalii.adp.fbl.text.BodyText;
import etalii.adp.fbl.text.Span;
import etalii.adp.fbl.text.TextLine;

/** The {@code lines} and {@code blocks} families (FBL §4.6, §4.7). */
public final class LinesFamily extends FamilyReader {

    /** One rule's {@code line} on one statement: what a match is remembered by. */
    private record MatchKey(String expression, Statement statement) {
    }

    /** The braces of one line outside its strings: their balance, and whether the line ends with an opening one. */
    private record BraceCount(int net, boolean endsWithOpen) {
    }

    private record Pending(SpliceOperation operation, Span span, String text) {
    }

    private record InsertPoint(int offset, String text) {
    }

    private final boolean blocks;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Span> leaves = new ArrayList<>();
    private final List<Statement> statements = new ArrayList<>();
    private final Map<MatchKey, Map<String, Group>> matches = new LinkedHashMap<>();
    private final Set<Integer> commentLines = new HashSet<>();

    public LinesFamily(BodyText text, FblBinding binding, FblOptions options, boolean blocks) {
        super(text, binding, options);
        this.blocks = blocks;
    }

    @Override
    public String familyName() {
        return blocks ? "blocks" : "lines";
    }

    @Override
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    @Override
    public List<Span> leaves() {
        return Collections.unmodifiableList(leaves);
    }

    /** Between statements and comments there are only whitespace, line endings and the closing <code>}</code> of blocks. */
    @Override
    public boolean isTrivia(Span gap) {
        byte[] bytes = text().bytes();
        for (int i = gap.start(); i < gap.end(); i++) {
            byte b = bytes[i];
            if (!(b == ' ' || b == '\t' || b == '\r' || b == '\n' || b == '}')) {
                return false;
            }
        }
        return true;
    }

    List<Statement> statements() {
        return Collections.unmodifiableList(statements);
    }

    @Override
    public void parse() {
        BoundedRegex comment = binding().comment() == null ? null : regex(binding().comment(), false);
        byte[] bytes = text().bytes();
        List<TextLine> lines = text().lines();
        Deque<Statement> stack = new ArrayDeque<>();
        for (int index = 0; index < lines.size(); index++) {
            TextLine line = lines.get(index);
            int start = index == 0 ? Math.max(line.start(), text().bomLength()) : line.start();
            int first = start;
            while (first < line.contentEnd() && (bytes[first] == ' ' || bytes[first] == '\t')) {
                first++;
            }
            if (first == line.contentEnd()) {
                continue;
            }
            String content = text().text(first, line.contentEnd());
            if (comment != null && matches(comment, text().text(start, line.contentEnd()), null)) {
                commentLines.add(index);
                leaves.add(new Span(first, line.contentEnd()));
                continue;
            }
            String trimmed = trimEnd(content);
            if (blocks && trimmed.equals("}")) {
                if (stack.isEmpty()) {
                    setUnreadable(new Unreadable(first, "The braces of the body do not balance: this '}' closes no block."));
                    return;
                }
                Statement opener = stack.pop();
                opener.setLastLine(index);
                opener.setLineSpan(new Span(opener.lineSpan().start(), line.end()));
                opener.setOwn(new Span(opener.own().start(), first + 1));
                continue;
            }
            Statement statement = new Statement(
                    index,
                    content,
                    byteOffsets(content, first),
                    new Span(first, first + trimmed.getBytes(UTF_8).length),
                    first - line.start());
            statement.setParent(stack.isEmpty() ? null : stack.peek());
            statement.setLineSpan(new Span(leadingCommentStart(index), line.end()));
            if (statement.parent() != null) {
                statement.parent().children().add(statement);
            }
            statements.add(statement);
            entries.add(statement);
            leaves.add(new Span(first, line.contentEnd()));
            if (blocks) {
                BraceCount braces = braces(content);
                if (braces.endsWithOpen() && braces.net() == 1) {
                    statement.setOpens(true);
                    stack.push(statement);
                } else if (braces.net() != 0) {
                    setUnreadable(new Unreadable(first, "The braces of the body do not balance on this line."));
                    return;
                }
            }
        }
        if (!stack.isEmpty()) {
            setUnreadable(new Unreadable(stack.peek().own().start(), "The braces of the body do not balance: this block is never closed."));
        }
    }

    /** {@code content} without the spaces and tabs at its end. */
    private static String trimEnd(String content) {
        int end = content.length();
        while (end > 0 && (content.charAt(end - 1) == ' ' || content.charAt(end - 1) == '\t')) {
            end--;
        }
        return content.substring(0, end);
    }

    private int leadingCommentStart(int index) {
        int start = text().lines().get(index).start();
        for (int above = index - 1; above >= 0 && commentLines.contains(above); above--) {
            start = text().lines().get(above).start();
        }
        return start;
    }

    private static BraceCount braces(String content) {
        int net = 0;
        boolean inString = false;
        char last = '\0';
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < content.length()) {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                last = c;
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                net++;
            } else if (c == '}') {
                net--;
            }
            if (!(c == ' ' || c == '\t')) {
                last = c;
            }
        }
        return new BraceCount(net, !inString && last == '{');
    }

    private static int[] byteOffsets(String content, int start) {
        int[] offsets = new int[content.length() + 1];
        int offset = start;
        for (int i = 0; i < content.length(); i++) {
            offsets[i] = offset;
            char c = content.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < content.length()) {
                offsets[i + 1] = offset;
                offset += 4;
                i++;
                continue;
            }
            // One UTF-16 unit outside a pair: one, two or three bytes of UTF-8.
            offset += c < 0x80 ? 1 : c < 0x800 ? 2 : 3;
        }
        offsets[content.length()] = offset;
        return offsets;
    }

    /**
     * Whether {@code regex} matches {@code input}; a match that takes too long reads as none.
     *
     * @param statement the statement the finding of a timeout is reported on, or null to report none
     */
    private boolean matches(BoundedRegex regex, String input, Statement statement) {
        try {
            return regex.isMatch(input);
        } catch (RegexTimeoutException e) {
            if (statement != null) {
                timedOut(regex, statement);
            }
            return false;
        }
    }

    private void timedOut(BoundedRegex regex, Statement statement) {
        report(FindingCodes.REGEX_TIMEOUT, FindingSeverity.WARNING,
                "The expression '" + regex.expression() + "' took too long on this statement; it is read as not matching.", statement.own());
    }

    /** The named groups {@code expression} binds on {@code statement}, in the expression's order; null when it does not match. */
    private Map<String, Group> match(String expression, boolean caseInsensitive, Statement statement) {
        MatchKey key = new MatchKey(expression, statement);
        if (matches.containsKey(key)) {
            return matches.get(key);
        }
        BoundedRegex regex = regex(expression, caseInsensitive);
        Map<String, Group> groups = null;
        try {
            RegexMatch match = regex.match(statement.content());
            if (match != null) {
                groups = new LinkedHashMap<>();
                for (String name : regex.groupNames()) {
                    RegexMatch.Group group = match.group(name);
                    if (!group.success()) {
                        continue;
                    }
                    groups.put(name, new Group(group.value(), new Span(statement.offsets()[group.start()], statement.offsets()[group.end()])));
                }
            }
        } catch (RegexTimeoutException e) {
            timedOut(regex, statement);
        }
        matches.put(key, groups);
        return groups;
    }

    @Override
    public boolean headerHolds(HeaderSettings header) {
        if (header.line() == null) {
            return true;
        }
        Statement first = statements.isEmpty() ? null : statements.get(0);
        if (first == null) {
            return false;
        }
        if (match(header.line(), false, first) == null) {
            return false;
        }
        first.setHeader(true);
        return true;
    }

    @Override
    public List<Candidate> candidates(Rule rule) {
        List<Candidate> candidates = new ArrayList<>();
        if (rule.line() == null) {
            return candidates;
        }
        for (Statement statement : statements) {
            if (statement.isHeader()) {
                continue;
            }
            Map<String, Group> groups = match(rule.line(), rule.caseInsensitive(), statement);
            if (groups != null) {
                candidates.add(new Candidate(rule, null, statement, values(groups)));
            }
        }
        return candidates;
    }

    @Override
    public List<Candidate> blockCandidates(BlockRule block) {
        List<Candidate> candidates = new ArrayList<>();
        for (Statement statement : statements) {
            if (statement.isHeader()) {
                continue;
            }
            Map<String, Group> groups = match(block.line(), block.caseInsensitive(), statement);
            if (groups != null) {
                candidates.add(new Candidate(null, block, statement, values(groups)));
            }
        }
        return candidates;
    }

    private static Map<String, String> values(Map<String, Group> groups) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, Group> group : groups.entrySet()) {
            values.put(group.getKey(), group.getValue().value());
        }
        return values;
    }

    @Override
    public boolean admits(List<String> within, Entry entry, Function<Entry, String> claimedBy) {
        if (!blocks || within == null) {
            return true;
        }
        Entry enclosing = entry.parent();
        String name = enclosing == null ? "^" : claimedBy.apply(enclosing);
        return name != null && within.contains(name);
    }

    private Map<String, Group> groups(Candidate candidate) {
        Rule rule = candidate.rule();
        String expression = rule != null && rule.line() != null ? rule.line() : candidate.block().line();
        boolean insensitive = rule != null ? rule.caseInsensitive() : candidate.block().caseInsensitive();
        Map<String, Group> groups = match(expression, insensitive, (Statement) candidate.entry());
        return groups != null ? groups : Map.of();
    }

    @Override
    public Object celValue(Candidate candidate) {
        CelMap map = new CelMap();
        for (Map.Entry<String, String> capture : candidate.captures().entrySet()) {
            map.put(capture.getKey(), capture.getValue());
        }
        return map;
    }

    @Override
    public CelExtra celExtra(Candidate candidate) {
        return new CelExtra("groups", celValue(candidate));
    }

    @Override
    public SlotRead read(Candidate candidate, Slot slot) {
        if (slot.group() == null) {
            return SlotRead.readOnlyAbsent("A " + familyName() + " statement has no " + slot + ".");
        }
        Map<String, Group> groups = groups(candidate);
        Group group = groups.get(slot.group());
        if (group == null) {
            return slot.flag() ? new SlotRead(false, null, false, true) : SlotRead.ABSENT;
        }
        if (slot.word() == null) {
            return new SlotRead(group.value(), group.span(), true, true).withNode(group).withWords(words(group)).withWire(group.value());
        }
        BoundedRegex expression = regex(slot.word(), false);
        for (Word word : words(group)) {
            String raw = text().text(word.span());
            RegexMatch match;
            try {
                match = expression.match(raw);
            } catch (RegexTimeoutException e) {
                timedOut(expression, (Statement) candidate.entry());
                continue;
            }
            if (match == null) {
                continue;
            }
            if (slot.flag()) {
                return new SlotRead(true, word.span(), true, true).withNode(word).withWire(raw);
            }
            RegexMatch.Group value = match.group("value");
            if (value.success()) {
                int offset = word.span().start() + raw.substring(0, value.start()).getBytes(UTF_8).length;
                Span span = new Span(offset, offset + value.value().getBytes(UTF_8).length);
                return new SlotRead(value.value(), span, true, true).withNode(word).withWire(value.value());
            }
            return new SlotRead(word.text(), word.span(), true, true).withNode(word).withQuote(word.quoted() ? "\"" : null).withWire(word.text());
        }
        return slot.flag() ? new SlotRead(false, null, false, true) : SlotRead.ABSENT;
    }

    /**
     * The words of a group (FBL §4.6): runs of non-whitespace, where a run starting with {@code "}
     * extends to the next {@code "} and includes both quotes.
     */
    List<Word> words(Group group) {
        List<Word> words = new ArrayList<>();
        byte[] bytes = text().bytes();
        int i = group.span().start();
        int end = group.span().end();
        while (i < end) {
            while (i < end && (bytes[i] == ' ' || bytes[i] == '\t')) {
                i++;
            }
            if (i >= end) {
                break;
            }
            int start = i;
            if (bytes[i] == '"') {
                i++;
                while (i < end && bytes[i] != '"') {
                    i++;
                }
                if (i < end) {
                    i++;
                }
                words.add(new Word(text().text(start + 1, Math.max(start + 1, i - 1)), new Span(start, i), true));
                continue;
            }
            while (i < end && !(bytes[i] == ' ' || bytes[i] == '\t')) {
                i++;
            }
            words.add(new Word(text().text(start, i), new Span(start, i), false));
        }
        return words;
    }

    @Override
    public SlotRead readRaw(Entry entry, String name) {
        for (Map.Entry<MatchKey, Map<String, Group>> match : matches.entrySet()) {
            Map<String, Group> groups = match.getValue();
            if (match.getKey().statement() == entry && groups != null) {
                Group group = groups.get(name);
                if (group != null) {
                    return new SlotRead(group.value(), group.span(), true, true).withNode(group);
                }
            }
        }
        return SlotRead.ABSENT;
    }

    @Override
    public void afterRead(Function<Entry, String> claimedBy) {
        if (!binding().reportUnmatched()) {
            return;
        }
        for (Statement statement : statements) {
            if (statement.isHeader() || claimedBy.apply(statement) != null) {
                continue;
            }
            report(FindingCodes.UNBOUND_STATEMENT, FindingSeverity.WARNING, "No rule of the binding reads this statement; it is kept as it is.", statement.firstLineSpan());
        }
    }

    // ---- writing ----

    @Override
    public String format(SlotRead read, AttributeBinding binding, Object value) {
        String written = binding == null ? null : NewText.wire(binding, value, read.wire());
        if (written == null) {
            written = NewText.plain(value, binding);
        }
        String quote = read.quote();
        return quote != null ? quote + written + quote : written;
    }

    /** The written form of a value for an emit placeholder: a map's first key, a flag's word, a number by FBL §6.3. */
    private String emitted(AttributeBinding binding, Object value) {
        if (binding == null) {
            return NewText.plain(value, null);
        }
        if (binding.flag()) {
            return Boolean.TRUE.equals(value) ? flagWord(binding) : "";
        }
        String wire = NewText.wire(binding, value, null);
        return wire != null ? wire : NewText.plain(value, binding);
    }

    private static String flagWord(Slot slot) {
        String word = slot.word() != null ? slot.word() : "";
        if (word.startsWith("^")) {
            word = word.substring(1);
        }
        if (word.endsWith("$")) {
            word = word.substring(0, word.length() - 1);
        }
        return unescape(word);
    }

    /**
     * The text an escaped expression stands for: every backslash escape replaced by its character.
     * A backslash at the very end is dropped.
     */
    private static String unescape(String input) {
        int i = input.indexOf('\\');
        if (i < 0) {
            return input;
        }
        StringBuilder output = new StringBuilder(input.length());
        output.append(input, 0, i);
        do {
            i++;
            if (i < input.length()) {
                i = escape(input, i, output);
            }
            int last = i;
            while (i < input.length() && input.charAt(i) != '\\') {
                i++;
            }
            output.append(input, last, i);
        } while (i < input.length());
        return output.toString();
    }

    /** Appends the character of the escape whose first character after the backslash is at {@code i}; returns the index after it. */
    private static int escape(String input, int i, StringBuilder output) {
        char ch = input.charAt(i++);
        if (ch >= '0' && ch <= '7') {
            i--;
            int value = 0;
            for (int count = 3; count > 0 && i < input.length() && input.charAt(i) >= '0' && input.charAt(i) <= '7'; count--) {
                value = value * 8 + (input.charAt(i) - '0');
                i++;
            }
            output.append((char) (value & 0xFF));
            return i;
        }
        switch (ch) {
            case 'x':
                return hex(input, i, 2, output);
            case 'u':
                return hex(input, i, 4, output);
            case 'a':
                output.append('\u0007');
                return i;
            case 'b':
                output.append('\b');
                return i;
            case 'e':
                output.append('\u001B');
                return i;
            case 'f':
                output.append('\f');
                return i;
            case 'n':
                output.append('\n');
                return i;
            case 'r':
                output.append('\r');
                return i;
            case 't':
                output.append('\t');
                return i;
            case 'v':
                output.append('\u000B');
                return i;
            case 'c':
                if (i >= input.length()) {
                    throw new IllegalArgumentException("Missing control character.");
                }
                char control = input.charAt(i++);
                if (control >= 'a' && control <= 'z') {
                    control -= 0x20;
                }
                control = (char) (control - '@');
                if (control < ' ') {
                    output.append(control);
                    return i;
                }
                throw new IllegalArgumentException("Unrecognized control character.");
            default:
                if (isWordCharacter(ch)) {
                    throw new IllegalArgumentException("Unrecognized escape sequence \\" + ch + ".");
                }
                output.append(ch);
                return i;
        }
    }

    private static int hex(String input, int i, int count, StringBuilder output) {
        int value = 0;
        int left = count;
        if (input.length() - i >= count) {
            for (; left > 0; left--) {
                int digit = hexDigit(input.charAt(i++));
                if (digit < 0) {
                    break;
                }
                value = value * 0x10 + digit;
            }
        }
        if (left > 0) {
            throw new IllegalArgumentException("Insufficient or invalid hexadecimal digits.");
        }
        output.append((char) value);
        return i;
    }

    private static int hexDigit(char ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        }
        if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        }
        if (ch >= 'A' && ch <= 'F') {
            return ch - 'A' + 10;
        }
        return -1;
    }

    /** Whether an escaped character is one an expression's word boundary counts as part of a word. */
    private static boolean isWordCharacter(char ch) {
        if (ch == '‍' || ch == '‌') {
            return true;
        }
        return switch (Character.getType(ch)) {
            case Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER, Character.MODIFIER_LETTER,
                    Character.OTHER_LETTER, Character.NON_SPACING_MARK, Character.DECIMAL_DIGIT_NUMBER, Character.CONNECTOR_PUNCTUATION -> true;
            default -> false;
        };
    }

    @Override
    public void write(Plan plan, ReadElement element, List<SlotChange> changes) {
        Statement statement = (Statement) element.entry();
        boolean reEmit = false;
        List<Pending> pending = new ArrayList<>();
        for (SlotChange change : changes) {
            SlotRead read = change.read();
            AttributeBinding binding = change.binding();
            if (binding.flag()) {
                if (Boolean.TRUE.equals(change.value()) == read.present()) {
                    continue;
                }
                if (read.present()) {
                    pending.add(new Pending(SpliceOperation.REMOVE_KEY, withSpaceBefore(read.span()), ""));
                } else {
                    InsertPoint at = insertPoint(element, change);
                    if (at != null) {
                        pending.add(new Pending(SpliceOperation.INSERT_KEY, new Span(at.offset(), at.offset()), at.text()));
                    } else {
                        reEmit = true;
                    }
                }
                continue;
            }
            if (change.isEmpty() && "remove".equals(binding.empty())) {
                if (read.present()) {
                    pending.add(new Pending(SpliceOperation.REMOVE_KEY, removable(read), ""));
                }
                continue;
            }
            if (read.present() && read.span() != null) {
                Span span = read.span();
                String text = format(read, binding, change.value());
                if (!text.equals(text().text(span))) {
                    pending.add(new Pending(SpliceOperation.REPLACE_VALUE, span, text));
                }
                continue;
            }
            InsertPoint point = insertPoint(element, change);
            if (point != null) {
                pending.add(new Pending(SpliceOperation.INSERT_KEY, new Span(point.offset(), point.offset()), point.text()));
            } else {
                reEmit = true;
            }
        }
        if (!reEmit) {
            for (Pending splice : pending) {
                plan.add(splice.operation(), splice.span(), splice.text());
            }
            return;
        }
        String emit = element.rule().insert() == null ? null : element.rule().insert().emit();
        if (emit == null) {
            Plan.refuse("This " + element.rule().type() + " has no place in its line for the new value, and its rule gives no form to write the line in.");
            return;
        }
        Map<String, Object> values = new LinkedHashMap<>(element.attributes());
        for (SlotChange change : changes) {
            values.put(change.attribute(), change.isEmpty() ? null : change.value());
        }
        String idText = textOf(element.idRead());
        String id = idText != null ? idText : element.id();
        String line = NewText.render(emit, name -> placeholder(element.rule(), name, values, id, textOf(element.sourceRead()), textOf(element.targetRead())));
        plan.add(SpliceOperation.RE_EMIT_LINE, reEmitSpan(statement), line);
    }

    /** The value of a read when it is text, else null. */
    private static String textOf(SlotRead read) {
        return read != null && read.value() instanceof String value ? value : null;
    }

    private Span reEmitSpan(Statement statement) {
        if (!statement.opens()) {
            return statement.firstLineSpan();
        }
        byte[] bytes = text().bytes();
        Span span = statement.firstLineSpan();
        int end = span.end() - 1;
        while (end > span.start() && (bytes[end - 1] == ' ' || bytes[end - 1] == '\t')) {
            end--;
        }
        return new Span(span.start(), end);
    }

    private String placeholder(Rule rule, String name, Map<String, Object> values, String id, String source, String target) {
        switch (name) {
            case "id":
                return id;
            case "source":
                return source;
            case "target":
                return target;
            default:
                break;
        }
        AttributeBinding binding = rule.attribute(name);
        Object value = values.get(name);
        return value != null ? emitted(binding, value) : null;
    }

    private Span withSpaceBefore(Span span) {
        byte[] bytes = text().bytes();
        int start = span.start();
        while (start > 0 && (bytes[start - 1] == ' ' || bytes[start - 1] == '\t')) {
            start--;
        }
        return new Span(start, span.end());
    }

    /** A removed value takes its quotes and the whitespace before it with it (FBL §6.1 {@code remove-key}). */
    private Span removable(SlotRead read) {
        byte[] bytes = text().bytes();
        Span span = read.span();
        if (span.start() > 0 && span.end() < text().length() && bytes[span.start() - 1] == '"' && bytes[span.end()] == '"') {
            span = new Span(span.start() - 1, span.end() + 1);
        }
        return withSpaceBefore(span);
    }

    /**
     * Where an absent value can be written by {@code insert-key} (FBL §6.3): when the rule's emit
     * places it right after a value the statement has, and nothing after it in the emit is present
     * in the statement. Null when the line must be re-emitted instead.
     */
    private InsertPoint insertPoint(ReadElement element, SlotChange change) {
        String emit = element.rule().insert() == null ? null : element.rule().insert().emit();
        if (emit == null) {
            return null;
        }
        List<EmitPart> parts = NewText.parts(emit);
        int index = -1;
        for (int i = 0; i < parts.size(); i++) {
            if (change.attribute().equals(parts.get(i).placeholder())) {
                index = i;
                break;
            }
        }
        if (index < 0 || parts.get(index).segment() < 0) {
            return null;
        }
        int segment = parts.get(index).segment();
        int first = -1;
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).segment() == segment) {
                first = i;
                break;
            }
        }
        int previous = -1;
        for (int i = first - 1; i >= 0; i--) {
            if (parts.get(i).placeholder() != null) {
                previous = i;
                break;
            }
        }
        if (previous < 0) {
            return null;
        }
        Span after = spanOf(element, parts.get(previous).placeholder());
        if (after == null) {
            return null;
        }
        for (int i = index + 1; i < parts.size(); i++) {
            String later = parts.get(i).placeholder();
            if (later != null && spanOf(element, later) != null) {
                return null;
            }
        }
        int offset = after.end();
        StringBuilder betweenText = new StringBuilder();
        for (int i = previous + 1; i < first; i++) {
            String literal = parts.get(i).literal();
            betweenText.append(literal != null ? literal : "");
        }
        String between = betweenText.toString();
        int betweenLength = between.getBytes(UTF_8).length;
        if (!between.isEmpty() && offset + betweenLength <= text().length() && text().text(offset, offset + betweenLength).equals(between)) {
            offset += betweenLength;
        }
        StringBuilder template = new StringBuilder("[");
        for (EmitPart part : parts) {
            if (part.segment() == segment) {
                template.append(part.literal() != null ? part.literal() : "{" + part.placeholder() + "}");
            }
        }
        template.append("]");
        String segmentText = NewText.render(template.toString(),
                name -> name.equals(change.attribute()) ? emitted(change.binding(), change.value()) : null);
        return segmentText.isEmpty() ? null : new InsertPoint(offset, segmentText);
    }

    private static Span spanOf(ReadElement element, String placeholder) {
        switch (placeholder) {
            case "id":
                return element.idRead() == null ? null : element.idRead().span();
            case "source":
                return element.sourceRead() == null ? null : element.sourceRead().span();
            case "target":
                return element.targetRead() == null ? null : element.targetRead().span();
            default:
                SlotRead read = element.slots().get(placeholder);
                return read != null && read.present() ? read.span() : null;
        }
    }

    @Override
    public void insert(Plan plan, InsertRequest request) {
        Rule rule = request.rule();
        String emit = rule.insert() == null ? null : rule.insert().emit();
        if (emit == null) {
            Plan.refuse("The binding gives no form to write a new " + rule.type() + " in.");
            return;
        }
        String line = NewText.render(emit, name -> placeholder(rule, name, request.values(), request.id(),
                request.source() == null ? null : request.source().key(), request.target() == null ? null : request.target().key()));
        InsertSettings insert = rule.insert();
        Statement container = null;
        if (blocks) {
            if (request.parent() != null) {
                container = (Statement) request.parent().entry();
            } else if (insert.container() != null) {
                container = findContainer(plan.reading(), insert.container());
            }
            if (container == null && (request.parent() != null || insert.container() != null)) {
                Plan.refuse("The file has no block to add the " + rule.type() + " to.");
            }
        }
        List<Statement> siblings = new ArrayList<>();
        if (container == null) {
            for (Statement statement : statements) {
                if (statement.parent() == null && !statement.isHeader()) {
                    siblings.add(statement);
                }
            }
        } else {
            for (Entry child : container.children()) {
                if (child instanceof Statement statement) {
                    siblings.add(statement);
                }
            }
        }
        Statement lastSibling = siblings.isEmpty() ? null : siblings.get(siblings.size() - 1);
        Statement firstSibling = siblings.isEmpty() ? null : siblings.get(0);
        Statement previous = null;
        String place = insert.place() == null ? "" : insert.place();
        switch (place) {
            case "after-last":
                for (int i = siblings.size() - 1; i >= 0; i--) {
                    if (rule.name().equals(plan.reading().claimedBy(siblings.get(i)))) {
                        previous = siblings.get(i);
                        break;
                    }
                }
                if (previous == null) {
                    previous = lastSibling;
                }
                break;
            case "end":
            case "last-child":
                previous = lastSibling;
                break;
            case "start":
                previous = null;
                break;
            case "end-of-document":
                previous = statements.isEmpty() ? null : statements.get(statements.size() - 1);
                break;
            default:
                Plan.refuse("A " + familyName() + " body cannot place a new entry '" + place + "'.");
                break;
        }
        if (container != null && !container.opens()) {
            openBlock(plan, container, line);
            return;
        }
        int offset;
        if (previous != null) {
            offset = previous.lineSpan().end();
        } else if (container != null) {
            offset = text().lines().get(container.firstLine()).end();
        } else {
            offset = firstSibling != null && firstSibling.lineSpan() != null ? firstSibling.lineSpan().start() : text().length();
        }
        int indent;
        if (previous != null) {
            indent = previous.indent();
        } else if (firstSibling != null) {
            indent = firstSibling.indent();
        } else {
            indent = container == null ? 0 : container.indent() + step();
        }
        plan.add(SpliceOperation.INSERT_ENTRY, offset, offset, newLine(offset, indentation(indent) + line));
    }

    /** The text of a new line at {@code offset}, a line start or the end of a body without a final newline (FBL §6.3). */
    private String newLine(int offset, String line) {
        String newline = newlineAt(offset);
        List<TextLine> lines = text().lines();
        if (offset == text().length() && text().length() > 0 && lines.get(lines.size() - 1).ending().isEmpty()) {
            return newline + line;
        }
        return line + newline;
    }

    private void openBlock(Plan plan, Statement parent, String line) {
        TextLine first = text().lines().get(parent.firstLine());
        String newline = newlineAt(first.start());
        plan.add(SpliceOperation.OPEN_BLOCK, parent.firstLineSpan().end(), parent.firstLineSpan().end(), " {");
        String childIndent = indentation(parent.indent() + step());
        String close = indentation(parent.indent()) + "}";
        if (first.ending().isEmpty()) {
            plan.add(SpliceOperation.INSERT_ENTRY, first.end(), first.end(), newline + childIndent + line);
            plan.add(SpliceOperation.OPEN_BLOCK, first.end(), first.end(), newline + close);
            return;
        }
        plan.add(SpliceOperation.INSERT_ENTRY, first.end(), first.end(), childIndent + line + newline);
        plan.add(SpliceOperation.OPEN_BLOCK, first.end(), first.end(), close + newline);
    }

    private static Statement findContainer(BodyReading reading, String name) {
        for (Entry entry : reading.family().entries()) {
            if (entry instanceof Statement statement && name.equals(reading.claimedBy(statement))) {
                return statement;
            }
        }
        return null;
    }

    @Override
    public void remove(Plan plan, ReadElement element, Set<ReadElement> removed) {
        plan.add(SpliceOperation.REMOVE_ENTRY, element.entry().removalSpan(), "");
    }
}
