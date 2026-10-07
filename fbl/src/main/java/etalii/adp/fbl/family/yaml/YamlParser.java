package etalii.adp.fbl.family.yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import etalii.adp.fbl.expression.CelMap;
import etalii.adp.fbl.rule.TreeEntry;
import etalii.adp.fbl.rule.TreeFamily;
import etalii.adp.fbl.rule.TreeValue;
import etalii.adp.fbl.rule.ValueKind;
import etalii.adp.fbl.rule.ValueStyle;
import etalii.adp.fbl.text.BodyText;
import etalii.adp.fbl.text.Span;
import etalii.adp.fbl.text.TextLine;

/**
 * The lossless reading of a YAML body's first document (FBL §4.3): block mappings and sequences,
 * plain, quoted and block scalars, flow collections as one value, anchors and aliases, every node
 * with its span as written. Whether the document is well-formed is this parser's to judge as far
 * as it reads it: what it cannot read as the structure of the first document it refuses with a
 * {@link YamlError}.
 */
public final class YamlParser {

    private final BodyText text;
    private final byte[] bytes;
    private final Map<String, TreeValue> anchors = new HashMap<>();
    private final List<Span> leaves = new ArrayList<>();
    private final List<Duplicate> duplicates = new ArrayList<>();
    private int line;
    private int end;
    private int documentEnd;

    public YamlParser(BodyText text) {
        this.text = text;
        this.bytes = text.bytes();
    }

    /** The leaves read so far, in the order they were read. The list is the parser's own. */
    public List<Span> leaves() {
        return leaves;
    }

    /** Duplicate keys found: the key spans of the second and later occurrences. */
    public List<Duplicate> duplicates() {
        return duplicates;
    }

    /** Where the first document ends; everything after it is unbound (FBL §4.3). */
    public int documentEnd() {
        return documentEnd;
    }

    /** A key that appears again in its mapping, with the span of the later occurrence. */
    public record Duplicate(String name, Span span) {
    }

    /**
     * A key of the root mapping.
     *
     * @param value the text of the key's value when it is a scalar, empty for a key without a value; null when the value is a collection
     */
    public record RootKey(String name, String value) {
    }

    /** Why and where the parser stopped. */
    public static final class YamlError extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int offset;
        private final boolean malformed;

        YamlError(int offset, String message) {
            this(offset, message, false);
        }

        YamlError(int offset, String message, boolean malformed) {
            super(message);
            this.offset = offset;
            this.malformed = malformed;
        }

        public int offset() {
            return offset;
        }

        /** Whether the body is not YAML at this place, rather than YAML this parser does not read. */
        public boolean malformed() {
            return malformed;
        }
    }

    /**
     * The keys of the root mapping of a body's first document, in document order, each with its
     * scalar value: what a marker asks of a body before any binding reads it (FBL §12.2). A json
     * body is read as the yaml it also is.
     *
     * @return null when the body is not valid UTF-8, when this parser cannot read it, when a
     *         mapping repeats a key, or when the first document's root is not a mapping
     */
    public static List<RootKey> rootKeys(byte[] bytes) {
        BodyText text = new BodyText(bytes);
        if (!text.isValidUtf8()) {
            return null;
        }
        YamlParser parser = new YamlParser(text);
        TreeValue root;
        try {
            root = parser.parseDocument();
        } catch (YamlError error) {
            return null;
        }
        if (!parser.duplicates.isEmpty()) {
            return null;
        }
        List<RootKey> keys = new ArrayList<>();
        if (root.kind() == ValueKind.MAPPING) {
            for (TreeEntry member : root.entries()) {
                TreeValue value = member.value();
                boolean scalar = value.kind() == ValueKind.SCALAR && value.flow() == null
                        && value.style() != ValueStyle.FLOW_SEQUENCE && value.style() != ValueStyle.FLOW_MAPPING;
                keys.add(new RootKey(member.name(), scalar ? value.text() : null));
            }
            return keys;
        }
        if (root.style() == ValueStyle.FLOW_MAPPING && new FlowReader(root.text(), true).read() instanceof CelMap map) {
            for (Map.Entry<String, Object> member : map.entrySet()) {
                Object value = member.getValue();
                keys.add(new RootKey(member.getKey(), value == null ? "" : value instanceof String s ? s : null));
            }
            return keys;
        }
        return null;
    }

    public TreeValue parseDocument() {
        List<TextLine> lines = text.lines();
        line = 0;
        int directive = -1;
        boolean started = false;
        while (line < lines.size()) {
            int p = firstNonSpace(line);
            if (p < lines.get(line).contentEnd() && bytes[p] == '%') {
                if (directive < 0) {
                    directive = p;
                }
                line++;
                continue;
            }
            if (isMarker(line, "---")) {
                line++;
                started = true;
                break;
            }
            if (isSignificant(line)) {
                break;
            }
            line++;
        }
        if (directive >= 0 && !started) {
            throw new YamlError(line < lines.size() ? firstNonSpace(line) : text.length(), "A directive is not followed by the start of a document.", true);
        }
        end = lines.size();
        for (int i = line; i < lines.size(); i++) {
            if (isMarker(i, "---") || isMarker(i, "...")) {
                end = i;
                break;
            }
        }
        documentEnd = end < lines.size() ? lines.get(end).start() : text.length();
        if (documentEnd < text.length()) {
            leaves.add(new Span(documentEnd, text.length()));
        }
        TreeValue root = parseBlockNode(-1, false);
        if (root == null) {
            root = empty(text.bomLength());
        }
        int rest = nextSignificant(line);
        if (rest < end) {
            throw new YamlError(firstNonSpace(rest), "This line does not continue the structure above it.", true);
        }
        return root;
    }

    // ---- lines ----

    private int lineStart(int line) {
        return line == 0 ? Math.max(text.lines().get(0).start(), text.bomLength()) : text.lines().get(line).start();
    }

    private int contentEnd(int line) {
        return text.lines().get(line).contentEnd();
    }

    private static boolean isBlank(byte b) {
        return b == ' ' || b == '\t';
    }

    private int firstNonSpace(int line) {
        int p = lineStart(line);
        int end = contentEnd(line);
        while (p < end && isBlank(bytes[p])) {
            p++;
        }
        return p;
    }

    private boolean isSignificant(int line) {
        int p = firstNonSpace(line);
        return p < contentEnd(line) && bytes[p] != '#';
    }

    private int nextSignificant(int from) {
        int line = from;
        while (line < end && !isSignificant(line)) {
            line++;
        }
        return line;
    }

    private boolean isMarker(int line, String marker) {
        int start = lineStart(line);
        int end = contentEnd(line);
        if (end - start < 3 || bytes[start] != marker.charAt(0) || bytes[start + 1] != marker.charAt(1) || bytes[start + 2] != marker.charAt(2)) {
            return false;
        }
        return start + 3 == end || isBlank(bytes[start + 3]);
    }

    private int column(int line, int offset) {
        return offset - lineStart(line);
    }

    private boolean isDash(int line, int p) {
        return bytes[p] == '-' && (p + 1 == contentEnd(line) || isBlank(bytes[p + 1]));
    }

    private boolean atLineEnd(int line, int p) {
        while (p < contentEnd(line) && isBlank(bytes[p])) {
            p++;
        }
        return p >= contentEnd(line) || (bytes[p] == '#' && (p == lineStart(line) || isBlank(bytes[p - 1])));
    }

    private int skipSpaces(int line, int p) {
        while (p < contentEnd(line) && isBlank(bytes[p])) {
            p++;
        }
        return p;
    }

    private static TreeValue empty(int offset) {
        return new TreeValue(ValueKind.SCALAR, ValueStyle.EMPTY, new Span(offset, offset), "", null);
    }

    /** Refuses a tab among the white space before {@code p}: a key or a sequence entry is indented with spaces only. */
    private void spacesOnly(int line, int p) {
        for (int i = lineStart(line); i < p; i++) {
            if (bytes[i] == '\t') {
                throw new YamlError(i, "A tab is used for indentation.", true);
            }
        }
    }

    // ---- block structure ----

    /** The node that starts on the next significant line, or null when that line belongs to an enclosing node. */
    private TreeValue parseBlockNode(int parentIndent, boolean sequenceAtParent) {
        int line = nextSignificant(this.line);
        if (line >= end) {
            return null;
        }
        int p = firstNonSpace(line);
        int column = column(line, p);
        if (column < parentIndent) {
            return null;
        }
        if (column == parentIndent && !(sequenceAtParent && isDash(line, p))) {
            return null;
        }
        this.line = line;
        if (isDash(line, p)) {
            spacesOnly(line, p);
            return parseSequence(line, p, column);
        }
        if (keyAt(line, p) != null) {
            spacesOnly(line, p);
            return parseMapping(line, p, column);
        }
        return parseInline(line, p, parentIndent, false);
    }

    private TreeValue parseSequence(int line, int p, int column) {
        TreeValue value = new TreeValue(ValueKind.SEQUENCE, ValueStyle.BLOCK, new Span(p, p));
        while (true) {
            int dash = p;
            int q = skipSpaces(line, p + 1);
            TreeValue item;
            if (atLineEnd(line, q)) {
                this.line = line + 1;
                item = parseBlockNode(column, false);
                if (item == null) {
                    item = empty(dash + 1);
                }
            } else {
                this.line = line;
                item = parseContentAfterIndicator(line, q, column);
            }
            value.entries().add(TreeEntry.item(new Span(dash, end(item, dash + 1)), column, item));
            int next = nextSignificant(this.line);
            if (next >= end) {
                break;
            }
            int np = firstNonSpace(next);
            if (column(next, np) != column || !isDash(next, np)) {
                break;
            }
            spacesOnly(next, np);
            line = next;
            p = np;
            this.line = next;
        }
        List<TreeEntry> entries = value.entries();
        value.setSpan(new Span(entries.get(0).own().start(), entries.get(entries.size() - 1).own().end()));
        return value;
    }

    private TreeValue parseContentAfterIndicator(int line, int q, int parentIndent) {
        int column = column(line, q);
        if (isDash(line, q)) {
            return parseSequence(line, q, column);
        }
        if (keyAt(line, q) != null) {
            return parseMapping(line, q, column);
        }
        return parseInline(line, q, parentIndent, false);
    }

    private TreeValue parseMapping(int line, int p, int column) {
        TreeValue value = new TreeValue(ValueKind.MAPPING, ValueStyle.BLOCK, new Span(p, p));
        Set<String> names = new HashSet<>();
        while (true) {
            Key key = keyAt(line, p);
            if (key == null) {
                throw new YamlError(p, "A mapping key was expected here.");
            }
            leaves.add(key.span());
            int q = skipSpaces(line, key.colon() + 1);
            TreeValue member;
            if (atLineEnd(line, q)) {
                this.line = line + 1;
                member = parseBlockNode(column, true);
                if (member == null) {
                    member = empty(key.colon() + 1);
                }
            } else {
                this.line = line;
                member = parseInline(line, q, column, true);
            }
            TreeEntry entry = TreeEntry.member(key.name(), key.span(), new Span(key.span().start(), end(member, key.colon() + 1)), column, member);
            if (key.name().equals("<<") && !member.merged().isEmpty()) {
                value.merged().addAll(member.merged());
            }
            if (names.add(key.name())) {
                value.entries().add(entry);
            } else {
                duplicates.add(new Duplicate(key.name(), key.span()));
            }
            int next = nextSignificant(this.line);
            if (next >= end) {
                break;
            }
            int np = firstNonSpace(next);
            int nextColumn = column(next, np);
            if (nextColumn > column) {
                throw new YamlError(np, "This line is indented deeper than the mapping it is in allows.", true);
            }
            if (nextColumn < column || keyAt(next, np) == null) {
                break;
            }
            spacesOnly(next, np);
            line = next;
            p = np;
            this.line = next;
        }
        List<TreeEntry> entries = value.entries();
        value.setSpan(new Span(entries.size() > 0 ? entries.get(0).own().start() : p, entries.size() > 0 ? entries.get(entries.size() - 1).own().end() : p));
        return value;
    }

    private static int end(TreeValue value, int fallback) {
        return value.style() == ValueStyle.EMPTY ? fallback : value.span().end();
    }

    private record Key(String name, Span span, int colon) {
    }

    /** A mapping key at {@code p}: plain, or quoted on one line, followed by {@code :} and a space or the line's end. Null when there is none. */
    private Key keyAt(int line, int p) {
        int end = contentEnd(line);
        byte b = bytes[p];
        if (b == '"' || b == '\'') {
            int k = p + 1;
            while (k < end) {
                if (bytes[k] == '\\' && b == '"') {
                    k += 2;
                    continue;
                }
                if (bytes[k] == b) {
                    if (b == '\'' && k + 1 < end && bytes[k + 1] == '\'') {
                        k += 2;
                        continue;
                    }
                    break;
                }
                k++;
            }
            if (k >= end) {
                return null;
            }
            int colon = skipSpaces(line, k + 1);
            if (colon >= end || bytes[colon] != ':' || !(colon + 1 == end || isBlank(bytes[colon + 1]))) {
                return null;
            }
            if (b == '"') {
                for (int e = p + 1; e < k; e++) {
                    if (bytes[e] == '\\') {
                        knownEscape(line, e);
                        e++;
                    }
                }
            }
            String inner = text.text(p + 1, k);
            String name = b == '"' ? YamlScalars.decodeDouble(inner) : YamlScalars.decodeSingle(inner);
            return new Key(name, new Span(p, k + 1), colon);
        }
        if (b == '[' || b == '{' || b == '#' || b == '&' || b == '*' || b == '!' || b == '|' || b == '>' || b == '%' || b == '@' || b == '`') {
            return null;
        }
        if (b == '?' && (p + 1 == end || isBlank(bytes[p + 1]))) {
            throw new YamlError(p, "Explicit keys ('? ') are not read by this host.");
        }
        if (isDash(line, p)) {
            return null;
        }
        for (int k = p; k < end; k++) {
            byte c = bytes[k];
            if (c == '#' && k > p && isBlank(bytes[k - 1])) {
                return null;
            }
            if (c != ':' || !(k + 1 == end || isBlank(bytes[k + 1]))) {
                continue;
            }
            int keyEnd = k;
            while (keyEnd > p && isBlank(bytes[keyEnd - 1])) {
                keyEnd--;
            }
            if (keyEnd == p) {
                return null;
            }
            return new Key(text.text(p, keyEnd), new Span(p, keyEnd), k);
        }
        return null;
    }

    // ---- values ----

    /**
     * The value that starts at {@code q}, after its anchor and tag.
     *
     * @param afterKey whether the value follows its key on the key's line, where neither a second key nor a sequence entry may start
     */
    private TreeValue parseInline(int line, int q, int parentIndent, boolean afterKey) {
        String anchor = null;
        while (bytes[q] == '&' || bytes[q] == '!') {
            int start = q;
            while (q < contentEnd(line) && !isBlank(bytes[q])) {
                q++;
            }
            if (bytes[start] == '&') {
                if (q == start + 1) {
                    throw new YamlError(start, "An anchor has no name.", true);
                }
                anchor = text.text(start + 1, q);
            }
            q = skipSpaces(line, q);
            if (atLineEnd(line, q)) {
                this.line = line + 1;
                TreeValue block = parseBlockNode(parentIndent, true);
                if (block == null) {
                    block = empty(q);
                }
                if (anchor != null) {
                    anchors.put(anchor, block);
                }
                return block;
            }
        }
        TreeValue value = switch (bytes[q]) {
            case '*' -> parseAlias(line, q);
            case '"', '\'' -> parseQuoted(line, q);
            case '|', '>' -> parseBlockScalar(line, q, parentIndent);
            case '[', '{' -> parseFlow(line, q);
            case '@', '`', '%', ',', ']', '}' -> throw new YamlError(q, "This character cannot start a value.", true);
            default -> {
                if (afterKey && isDash(line, q)) {
                    throw new YamlError(q, "A sequence entry cannot start after a key on the key's line.", true);
                }
                yield parsePlain(line, q, parentIndent, afterKey);
            }
        };
        if (anchor != null) {
            anchors.put(anchor, value);
        }
        return value;
    }

    private void expectLineEnd(int line, int p) {
        if (!atLineEnd(line, p)) {
            throw new YamlError(p, "Unexpected content after a value.");
        }
    }

    private TreeValue parseAlias(int line, int q) {
        int p = q + 1;
        while (p < contentEnd(line) && !(isBlank(bytes[p]) || bytes[p] == ',' || bytes[p] == ']' || bytes[p] == '}')) {
            p++;
        }
        String name = text.text(q + 1, p);
        TreeValue target = anchors.get(name);
        if (target == null) {
            throw new YamlError(q, "The alias *" + name + " names no anchor.");
        }
        expectLineEnd(line, p);
        this.line = line + 1;
        Span span = new Span(q, p);
        leaves.add(span);
        TreeValue value = new TreeValue(ValueKind.SCALAR, ValueStyle.PLAIN, span, target.text(), target.typed(),
                target.kind() == ValueKind.SCALAR ? target.flow() : TreeFamily.cel(target), true);
        if (target.kind() == ValueKind.MAPPING) {
            value.merged().add(target);
        }
        return value;
    }

    /** Refuses the escape at {@code p}, a backslash, when a double-quoted scalar has no such escape or its code is cut short. */
    private void knownEscape(int line, int p) {
        int end = contentEnd(line);
        if (p + 1 >= end) {
            // An escaped line break: the scalar goes on with the next line.
            return;
        }
        int escape = bytes[p + 1] & 0xFF;
        int digits = switch (escape) {
            case 'x' -> 2;
            case 'u' -> 4;
            case 'U' -> 8;
            default -> 0;
        };
        if (digits == 0) {
            if ("0abt\tnvfre \"/\\N_LP".indexOf(escape) < 0) {
                throw new YamlError(p, "A double-quoted scalar holds an escape that YAML does not have.", true);
            }
            return;
        }
        for (int i = p + 2; i < p + 2 + digits; i++) {
            if (i >= end || Character.digit(bytes[i] & 0xFF, 16) < 0 || (bytes[i] & 0xFF) > 'f') {
                throw new YamlError(p, "A double-quoted scalar holds an escape whose code is cut short.", true);
            }
        }
    }

    private TreeValue parseQuoted(int line, int q) {
        byte quote = bytes[q];
        int p = q + 1;
        int current = line;
        while (true) {
            if (p >= contentEnd(current)) {
                current++;
                if (current >= end) {
                    throw new YamlError(q, "A quoted scalar is never closed.", true);
                }
                p = lineStart(current);
                continue;
            }
            byte b = bytes[p];
            if (quote == '"' && b == '\\') {
                knownEscape(current, p);
                p += 2;
                continue;
            }
            if (b == quote) {
                if (quote == '\'' && p + 1 < contentEnd(current) && bytes[p + 1] == '\'') {
                    p += 2;
                    continue;
                }
                break;
            }
            p++;
        }
        Span span = new Span(q, p + 1);
        expectLineEnd(current, p + 1);
        this.line = current + 1;
        leaves.add(span);
        String inner = text.text(q + 1, p);
        String decoded = quote == '"' ? YamlScalars.decodeDouble(inner) : YamlScalars.decodeSingle(inner);
        return new TreeValue(ValueKind.SCALAR, quote == '"' ? ValueStyle.DOUBLE : ValueStyle.SINGLE, span, decoded, decoded);
    }

    private TreeValue parsePlain(int line, int q, int parentIndent, boolean afterKey) {
        int end = plainEnd(line, q);
        if (afterKey) {
            for (int k = q; k < end; k++) {
                if (bytes[k] == ':' && (k + 1 == end || isBlank(bytes[k + 1]))) {
                    throw new YamlError(k, "A second key cannot start after a key on the key's line.", true);
                }
            }
        }
        StringBuilder plain = new StringBuilder(text.text(q, end));
        int last = end;
        int current = line;
        int blank = 0;
        for (int next = line + 1; next < this.end; next++) {
            int p = firstNonSpace(next);
            if (p >= contentEnd(next)) {
                blank++;
                continue;
            }
            if (bytes[p] == '#' || column(next, p) <= parentIndent) {
                break;
            }
            if (keyAt(next, p) != null || isDash(next, p) && column(next, p) <= parentIndent + 1) {
                break;
            }
            int partEnd = plainEnd(next, p);
            if (blank > 0) {
                plain.repeat('\n', blank);
            } else {
                plain.append(' ');
            }
            plain.append(text.text(p, partEnd));
            blank = 0;
            last = partEnd;
            current = next;
        }
        this.line = current + 1;
        Span span = new Span(q, last);
        leaves.add(span);
        String value = plain.toString();
        return new TreeValue(ValueKind.SCALAR, ValueStyle.PLAIN, span, value, YamlScalars.typed(value));
    }

    private int plainEnd(int line, int q) {
        int end = contentEnd(line);
        int p = q;
        while (p < end) {
            if (bytes[p] == '#' && p > q && isBlank(bytes[p - 1])) {
                break;
            }
            p++;
        }
        while (p > q && isBlank(bytes[p - 1])) {
            p--;
        }
        return p;
    }

    private TreeValue parseBlockScalar(int line, int q, int parentIndent) {
        boolean literal = bytes[q] == '|';
        int p = q + 1;
        char chomp = 'c';
        int explicitIndent = 0;
        boolean chomped = false;
        boolean indented = false;
        while (p < contentEnd(line) && !isBlank(bytes[p])) {
            char c = (char) (bytes[p] & 0xFF);
            if ((c == '+' || c == '-') && !chomped) {
                chomp = c;
                chomped = true;
            } else if (c >= '1' && c <= '9' && !indented) {
                explicitIndent = c - '0';
                indented = true;
            } else {
                // A zero, and an indicator given twice, are no indicators either.
                throw new YamlError(p, "A block scalar's header holds an unknown indicator.", true);
            }
            p++;
        }
        expectLineEnd(line, p);
        int headerEnd = p;
        int indent = explicitIndent > 0 ? Math.max(parentIndent, 0) + explicitIndent : -1;
        List<Integer> contentLines = new ArrayList<>();
        int trailingBlank = 0;
        int lastContent = -1;
        for (int next = line + 1; next < end; next++) {
            int first = firstNonSpace(next);
            boolean blankLine = first >= contentEnd(next);
            int column = column(next, first);
            if (blankLine) {
                contentLines.add(next);
                trailingBlank++;
                continue;
            }
            int spaces = lineStart(next);
            while (bytes[spaces] == ' ') {
                spaces++;
            }
            if (bytes[spaces] == '\t' && (indent < 0 || spaces - lineStart(next) < indent)) {
                throw new YamlError(spaces, "A tab is used for indentation.", true);
            }
            if (indent < 0) {
                if (column <= parentIndent) {
                    break;
                }
                indent = column;
            }
            if (column < indent) {
                break;
            }
            contentLines.add(next);
            trailingBlank = 0;
            lastContent = next;
        }
        if (lastContent < 0) {
            this.line = line + 1;
            Span empty = new Span(q, headerEnd);
            leaves.add(empty);
            return new TreeValue(ValueKind.SCALAR, literal ? ValueStyle.LITERAL : ValueStyle.FOLDED, empty, "", "");
        }
        List<String> texts = new ArrayList<>();
        for (int l : contentLines) {
            if (l <= lastContent) {
                int start = Math.min(lineStart(l) + indent, contentEnd(l));
                texts.add(text.text(start, contentEnd(l)));
            }
        }
        String body = literal ? String.join("\n", texts) : foldBlock(texts);
        body = switch (chomp) {
            case '-' -> body;
            case '+' -> body + "\n" + "\n".repeat(trailingBlank);
            default -> body + "\n";
        };
        this.line = lastContent + 1;
        Span span = new Span(q, contentEnd(lastContent));
        leaves.add(span);
        return new TreeValue(ValueKind.SCALAR, literal ? ValueStyle.LITERAL : ValueStyle.FOLDED, span, body, body);
    }

    private static String foldBlock(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i > 0) {
                String previous = lines.get(i - 1);
                boolean moreIndented = line.startsWith(" ") || previous.startsWith(" ");
                builder.append(line.isEmpty() || previous.isEmpty() || moreIndented ? "\n" : " ");
            }
            builder.append(line);
        }
        return builder.toString();
    }

    private TreeValue parseFlow(int line, int q) {
        // The brackets still open, innermost last: a collection is closed by the bracket of its own kind.
        StringBuilder open = new StringBuilder();
        int current = line;
        int p = q;
        while (true) {
            if (p >= contentEnd(current)) {
                current++;
                if (current >= end) {
                    throw new YamlError(q, "A flow collection is never closed.", true);
                }
                p = lineStart(current);
                continue;
            }
            byte b = bytes[p];
            if (b == '"' || b == '\'') {
                byte quote = b;
                p++;
                while (true) {
                    if (p >= contentEnd(current)) {
                        current++;
                        if (current >= end) {
                            throw new YamlError(q, "A quoted scalar in a flow collection is never closed.", true);
                        }
                        p = lineStart(current);
                        continue;
                    }
                    if (quote == '"' && bytes[p] == '\\') {
                        p += 2;
                        continue;
                    }
                    if (bytes[p] == quote) {
                        if (quote == '\'' && p + 1 < contentEnd(current) && bytes[p + 1] == '\'') {
                            p += 2;
                            continue;
                        }
                        break;
                    }
                    p++;
                }
                p++;
                continue;
            }
            if (b == '#' && p > q && isBlank(bytes[p - 1])) {
                p = contentEnd(current);
                continue;
            }
            if (b == '[' || b == '{') {
                open.append((char) b);
            }
            if (b == ']' || b == '}') {
                char opened = open.charAt(open.length() - 1);
                if ((opened == '[') != (b == ']')) {
                    throw new YamlError(p, "A flow collection is closed by the bracket of the other kind.", true);
                }
                open.setLength(open.length() - 1);
                if (open.isEmpty()) {
                    p++;
                    break;
                }
            }
            p++;
        }
        expectLineEnd(current, p);
        this.line = current + 1;
        Span span = new Span(q, p);
        leaves.add(span);
        String raw = text.text(span);
        FlowReader parser = new FlowReader(raw);
        Object flow = parser.read();
        return new TreeValue(ValueKind.SCALAR, bytes[q] == '[' ? ValueStyle.FLOW_SEQUENCE : ValueStyle.FLOW_MAPPING, span, raw, null, flow, false);
    }
}
