package etalii.adp.fbl.family.xml;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.Finding;
import etalii.adp.fbl.FindingCodes;
import etalii.adp.fbl.FindingSeverity;
import etalii.adp.fbl.SpliceOperation;
import etalii.adp.fbl.document.AttributeBinding;
import etalii.adp.fbl.document.CreateChild;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.HeaderSettings;
import etalii.adp.fbl.document.InsertSettings;
import etalii.adp.fbl.document.Rule;
import etalii.adp.fbl.document.Slot;
import etalii.adp.fbl.expression.CelMap;
import etalii.adp.fbl.plan.NewText;
import etalii.adp.fbl.plan.Plan;
import etalii.adp.fbl.rule.Candidate;
import etalii.adp.fbl.rule.CelExtra;
import etalii.adp.fbl.rule.Entry;
import etalii.adp.fbl.rule.FamilyReader;
import etalii.adp.fbl.rule.InsertRequest;
import etalii.adp.fbl.rule.OverrideNode;
import etalii.adp.fbl.rule.ReadElement;
import etalii.adp.fbl.rule.Selector;
import etalii.adp.fbl.rule.SlotChange;
import etalii.adp.fbl.rule.SlotRead;
import etalii.adp.fbl.rule.Unreadable;
import etalii.adp.fbl.text.BodyText;
import etalii.adp.fbl.text.Span;
import etalii.adp.fbl.text.TextLine;

/**
 * The xml family (FBL §4.5): a lossless reading of an XML 1.0 document over its bytes. The prolog,
 * comments, processing instructions and a document type declaration are unbound content; carriage
 * returns are kept, never normalised; entity declarations are not processed.
 */
public final class XmlFamily extends FamilyReader {

    /** A run of white space as Unicode counts it: the separators and the white space controls. */
    private static final Pattern WHITESPACE = Pattern.compile("[\\p{Z}\\t-\\r\\x85]+");

    private final List<Entry> entries = new ArrayList<>();
    private final List<Span> leaves = new ArrayList<>();
    private final Map<Integer, Span> commentLines = new HashMap<>();
    private XmlElement root;
    private XmlElement document;

    public XmlFamily(BodyText text, FblBinding binding, FblOptions options) {
        super(text, binding, options);
    }

    @Override
    public String familyName() {
        return "xml";
    }

    @Override
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    @Override
    public List<Span> leaves() {
        return Collections.unmodifiableList(leaves);
    }

    @Override
    public boolean isTrivia(Span gap) {
        return isWhitespace(gap);
    }

    // ---- reading ----

    private static final class XmlError extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int offset;

        XmlError(int offset, String message) {
            super(message);
            this.offset = offset;
        }

        int offset() {
            return offset;
        }
    }

    @Override
    public void parse() {
        try {
            parseDocument();
        } catch (XmlError error) {
            setUnreadable(new Unreadable(error.offset(), "The body is not well-formed XML: " + error.getMessage()));
        }
    }

    private void parseDocument() {
        byte[] bytes = text().bytes();
        root = new XmlElement(new Span(0, bytes.length), null, 0, true, 0);
        Deque<XmlElement> stack = new ArrayDeque<>();
        int position = text().bomLength();
        while (position < bytes.length) {
            if (bytes[position] != '<') {
                position = readText(position, stack.peek());
                continue;
            }
            if (startsWith(position, "<?")) {
                position = skip(position, "?>", "processing instruction");
            } else if (startsWith(position, "<!--")) {
                int start = position;
                position = skip(position, "-->", "comment");
                rememberComment(new Span(start, position));
            } else if (startsWith(position, "<![CDATA[")) {
                if (stack.isEmpty()) {
                    throw new XmlError(position, "A CDATA section must be inside the document element.");
                }
                int start = position;
                position = skip(position, "]]>", "CDATA section");
                String content = text().text(start + 9, position - 3);
                stack.peek().content().add(new XmlTextRun(new Span(start, position), content));
            } else if (startsWith(position, "<!")) {
                if (document != null) {
                    throw new XmlError(position, "A document type declaration must come before the document element.");
                }
                position = skipDeclaration(position);
            } else if (startsWith(position, "</")) {
                position = readEndTag(position, stack);
            } else {
                position = readStartTag(position, stack);
            }
        }
        if (!stack.isEmpty()) {
            throw new XmlError(bytes.length, "The element '" + stack.peek().name() + "' is not closed.");
        }
        if (document == null) {
            throw new XmlError(text().bomLength(), "The body has no document element.");
        }
        for (Entry entry : entries) {
            entry.setLineSpan(lineSpanOf(entry));
        }
    }

    private boolean startsWith(int position, String literal) {
        if (position + literal.length() > text().length()) {
            return false;
        }
        byte[] bytes = text().bytes();
        for (int i = 0; i < literal.length(); i++) {
            if (bytes[position + i] != (byte) literal.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private int find(int position, String literal) {
        for (int i = position; i + literal.length() <= text().length(); i++) {
            if (startsWith(i, literal)) {
                return i;
            }
        }
        return -1;
    }

    private int skip(int position, String terminator, String what) {
        int end = find(position + 2, terminator);
        if (end < 0) {
            throw new XmlError(position, "The " + what + " is not closed.");
        }
        end += terminator.length();
        leaves.add(new Span(position, end));
        return end;
    }

    private int skipDeclaration(int position) {
        byte[] bytes = text().bytes();
        int depth = 0;
        byte quote = 0;
        for (int i = position + 2; i < text().length(); i++) {
            byte b = bytes[i];
            if (quote != 0) {
                if (b == quote) {
                    quote = 0;
                }
                continue;
            }
            if (b == '"' || b == '\'') {
                quote = b;
            } else if (b == '[') {
                depth++;
            } else if (b == ']') {
                depth--;
            } else if (b == '>' && depth == 0) {
                leaves.add(new Span(position, i + 1));
                return i + 1;
            }
        }
        throw new XmlError(position, "The declaration is not closed.");
    }

    private void rememberComment(Span comment) {
        int line = text().lineIndexAt(comment.start());
        if (text().lineIndexAt(comment.end() - 1) == line && onlyWhitespaceBefore(comment.start()) && onlyTriviaAfter(comment.end())) {
            commentLines.put(line, comment);
        }
    }

    /**
     * @param parent the element the text is in, or null outside the document element
     */
    private int readText(int position, XmlElement parent) {
        byte[] bytes = text().bytes();
        int end = position;
        while (end < text().length() && bytes[end] != '<') {
            end++;
        }
        Span span = new Span(position, end);
        if (isWhitespace(span)) {
            if (parent != null) {
                parent.content().add(new XmlTextRun(span, text().text(span)));
            }
            return end;
        }
        if (parent == null) {
            throw new XmlError(position, "Text must be inside the document element.");
        }
        leaves.add(span);
        parent.content().add(new XmlTextRun(span, decode(span, parent)));
        return end;
    }

    /**
     * Decodes the five predefined entities and character references; any other reference makes {@code owner} unreadable.
     *
     * @param owner the element the text or attribute value belongs to, or null
     */
    private String decode(Span span, XmlElement owner) {
        String raw = text().text(span);
        if (raw.indexOf('&') < 0) {
            return raw;
        }
        StringBuilder builder = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) != '&') {
                builder.append(raw.charAt(i));
                continue;
            }
            int semicolon = raw.indexOf(';', i + 1);
            if (semicolon < 0) {
                throw new XmlError(span.start(), "An '&' must start a reference ending in ';'.");
            }
            String name = raw.substring(i + 1, semicolon);
            switch (name) {
                case "amp" -> builder.append('&');
                case "lt" -> builder.append('<');
                case "gt" -> builder.append('>');
                case "quot" -> builder.append('"');
                case "apos" -> builder.append('\'');
                default -> {
                    if (name.startsWith("#")) {
                        boolean hex = name.startsWith("#x");
                        String digits = hex ? name.substring(2) : name.substring(1);
                        int code = characterCode(digits, hex);
                        if (code < 1 || code > 0x10FFFF || (code >= 0xD800 && code <= 0xDFFF)) {
                            throw new XmlError(span.start(), "'&" + name + ";' is not a valid character reference.");
                        }
                        builder.appendCodePoint(code);
                    } else {
                        if (owner != null && owner.unreadable() == null) {
                            owner.setUnreadable("It refers to the entity '&" + name + ";', which is not one of XML's five predefined entities.");
                        }
                        builder.append('&').append(name).append(';');
                    }
                }
            }
            i = semicolon;
        }
        return builder.toString();
    }

    /**
     * The number the digits of a character reference write, or -1 when they are no number: ASCII
     * digits only, at least one, and no sign. A number past the last code point is returned as one
     * past it, however long it is.
     */
    private static int characterCode(String digits, boolean hex) {
        if (digits.isEmpty()) {
            return -1;
        }
        int code = 0;
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            int digit;
            if (c >= '0' && c <= '9') {
                digit = c - '0';
            } else if (hex && c >= 'a' && c <= 'f') {
                digit = c - 'a' + 10;
            } else if (hex && c >= 'A' && c <= 'F') {
                digit = c - 'A' + 10;
            } else {
                return -1;
            }
            code = Math.min(code * (hex ? 16 : 10) + digit, 0x110000);
        }
        return code;
    }

    private static boolean isNameByte(byte b, boolean first) {
        int c = b & 0xFF;
        return c >= 0x80 || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == ':'
                || (!first && ((c >= '0' && c <= '9') || c == '-' || c == '.'));
    }

    private int readName(int position) {
        byte[] bytes = text().bytes();
        int end = position;
        while (end < text().length() && isNameByte(bytes[end], end == position)) {
            end++;
        }
        if (end == position) {
            throw new XmlError(position, "A name was expected here.");
        }
        return end;
    }

    private static boolean isSpace(byte b) {
        return b == ' ' || b == '\t' || b == '\r' || b == '\n';
    }

    private int skipSpace(int position) {
        byte[] bytes = text().bytes();
        while (position < text().length() && isSpace(bytes[position])) {
            position++;
        }
        return position;
    }

    private int readStartTag(int start, Deque<XmlElement> stack) {
        byte[] bytes = text().bytes();
        int length = text().length();
        XmlElement parent = stack.peek();
        if (parent == null && document != null) {
            throw new XmlError(start, "The body has a second document element.");
        }
        int nameEnd = readName(start + 1);
        XmlElement element = new XmlElement(new Span(start, nameEnd), text().text(start + 1, nameEnd),
                start - text().lines().get(text().lineIndexAt(start)).start(), false, nameEnd);
        element.setParent(parent != null ? parent : root);
        int position = nameEnd;
        while (true) {
            int spaced = skipSpace(position);
            if (spaced >= length) {
                throw new XmlError(start, "The start tag of '" + element.name() + "' is not closed.");
            }
            if (startsWith(spaced, "/>")) {
                element.setSelfClosed(true);
                element.setClose(new Span(spaced, spaced + 2));
                position = spaced + 2;
                break;
            }
            if (bytes[spaced] == '>') {
                element.setClose(new Span(spaced, spaced + 1));
                position = spaced + 1;
                break;
            }
            if (spaced == position) {
                throw new XmlError(spaced, "Attributes must be separated by whitespace.");
            }
            int attributeEnd = readName(spaced);
            String name = text().text(spaced, attributeEnd);
            int equals = skipSpace(attributeEnd);
            if (equals >= length || bytes[equals] != '=') {
                throw new XmlError(equals, "The attribute '" + name + "' has no value.");
            }
            int open = skipSpace(equals + 1);
            if (open >= length || !(bytes[open] == '"' || bytes[open] == '\'')) {
                throw new XmlError(open, "The value of '" + name + "' must be quoted.");
            }
            byte quote = bytes[open];
            int close = open + 1;
            while (close < length && bytes[close] != quote) {
                if (bytes[close] == '<') {
                    throw new XmlError(close, "An attribute value cannot contain '<'.");
                }
                close++;
            }
            if (close >= length) {
                throw new XmlError(open, "The value of '" + name + "' is not closed.");
            }
            if (element.attribute(name) != null) {
                throw new XmlError(spaced, "The attribute '" + name + "' is written twice.");
            }
            Span value = new Span(open + 1, close);
            element.attributes().add(new XmlAttribute(name, new Span(spaced, close + 1), value, decode(value, element)));
            position = close + 1;
        }
        element.setStartTag(new Span(start, position));
        element.setOwn(element.startTag());
        leaves.add(element.startTag());
        entries.add(element);
        if (parent == null) {
            document = element;
            root.children().add(element);
        } else {
            parent.children().add(element);
            parent.content().add(element);
        }
        if (!element.selfClosed()) {
            stack.push(element);
        }
        return position;
    }

    private int readEndTag(int start, Deque<XmlElement> stack) {
        int nameEnd = readName(start + 2);
        String name = text().text(start + 2, nameEnd);
        int close = skipSpace(nameEnd);
        if (close >= text().length() || text().bytes()[close] != '>') {
            throw new XmlError(start, "The end tag of '" + name + "' is not closed.");
        }
        if (stack.isEmpty() || !stack.peek().name().equals(name)) {
            throw new XmlError(start, stack.isEmpty()
                    ? "The end tag '" + name + "' closes nothing."
                    : "The end tag '" + name + "' does not close '" + stack.peek().name() + "'.");
        }
        XmlElement element = stack.pop();
        element.setEndTag(new Span(start, close + 1));
        element.setOwn(new Span(element.startTag().start(), close + 1));
        leaves.add(element.endTag());
        return close + 1;
    }

    /** The line span (FBL §4.1.1): whole lines, extended upwards over comment lines at the same indentation. Null when the entry shares a line. */
    private Span lineSpanOf(Entry entry) {
        if (!onlyWhitespaceBefore(entry.own().start()) || !onlyTriviaAfter(entry.own().end())) {
            return null;
        }
        List<TextLine> lines = text().lines();
        int first = text().lineIndexAt(entry.own().start());
        int last = text().lineIndexAt(entry.own().end() - 1);
        while (first > 0) {
            Span comment = commentLines.get(first - 1);
            if (comment == null || comment.start() - lines.get(first - 1).start() != entry.indent()) {
                break;
            }
            first--;
        }
        return new Span(lines.get(first).start(), lines.get(last).end());
    }

    @Override
    public int step() {
        for (Entry entry : entries) {
            if (entry.parent() instanceof XmlElement parent && !parent.isRoot() && entry.lineSpan() != null && parent.lineSpan() != null
                    && entry.indent() >= parent.indent()) {
                return entry.indent() - parent.indent();
            }
        }
        return binding().text().indent() == 0 ? 1 : binding().text().indent();
    }

    private boolean attributeEquals(Entry entry, String attribute, String value) {
        XmlAttribute found = ((XmlElement) entry).attribute(attribute);
        return found != null && found.text().equals(value);
    }

    /** The first element {@code selector} reaches from {@code element}, or null. */
    private XmlElement child(XmlElement element, String selector) {
        List<Selector.Match> matches = Selector.match(element, selector, this::attributeEquals);
        return !matches.isEmpty() && matches.get(0).entry() instanceof XmlElement found ? found : null;
    }

    @Override
    public List<Candidate> candidates(Rule rule) {
        List<Candidate> candidates = new ArrayList<>();
        if (rule.at() == null || root == null) {
            return candidates;
        }
        for (Selector.Match match : Selector.match(root, rule.at(), this::attributeEquals)) {
            XmlElement element = (XmlElement) match.entry();
            String reason = element.unreadable();
            if (reason != null) {
                int line = locate(element.own()).line();
                boolean reported = false;
                for (Finding finding : findings()) {
                    if (finding.code().equals(FindingCodes.UNREADABLE_ENTRY) && finding.location() != null && finding.location().line() == line) {
                        reported = true;
                        break;
                    }
                }
                if (!reported) {
                    report(FindingCodes.UNREADABLE_ENTRY, FindingSeverity.WARNING, "This entry cannot be read: " + reason, element.startTag());
                }
                continue;
            }
            candidates.add(new Candidate(rule, null, match.entry(), match.captures()));
        }
        return candidates;
    }

    @Override
    public List<Entry> enclosing(Entry entry) {
        List<Entry> enclosing = new ArrayList<>();
        for (Entry parent = entry.parent(); parent instanceof XmlElement element && !element.isRoot(); parent = parent.parent()) {
            enclosing.add(parent);
        }
        return enclosing;
    }

    /** An element's own character data, references decoded. */
    private static String ownText(XmlElement element) {
        StringBuilder builder = new StringBuilder();
        for (Object item : element.content()) {
            if (item instanceof XmlTextRun run) {
                builder.append(run.text());
            }
        }
        return builder.toString();
    }

    /** All character data inside an element, its descendants' included. */
    private static String allText(XmlElement element) {
        StringBuilder builder = new StringBuilder();
        for (Object item : element.content()) {
            builder.append(item instanceof XmlTextRun run ? run.text() : allText((XmlElement) item));
        }
        return builder.toString();
    }

    @Override
    public Object celValue(Candidate candidate) {
        XmlElement element = (XmlElement) candidate.entry();
        CelMap map = new CelMap();
        for (XmlAttribute attribute : element.attributes()) {
            map.put(attribute.name(), attribute.text());
        }
        map.put("text", ownText(element));
        return map;
    }

    @Override
    public CelExtra celExtra(Candidate candidate) {
        CelMap path = new CelMap();
        for (Map.Entry<String, String> capture : candidate.captures().entrySet()) {
            path.put(capture.getKey(), capture.getValue());
        }
        return new CelExtra("path", path);
    }

    @Override
    public SlotRead read(Candidate candidate, Slot slot) {
        XmlElement element = (XmlElement) candidate.entry();
        if (slot.capture() != null) {
            String key = candidate.captures().get(slot.capture());
            return candidate.captures().containsKey(slot.capture())
                    ? new SlotRead(key, null, true, false, "An element's name is not rewritten in this file.")
                    : SlotRead.ABSENT;
        }
        XmlElement target = slot.child() == null ? element : child(element, slot.child());
        if (slot.xmlAttribute() != null) {
            if (target == null) {
                return SlotRead.ABSENT;
            }
            return readAttribute(target, slot.xmlAttribute());
        }
        if (slot.text()) {
            if (target == null) {
                return SlotRead.ABSENT;
            }
            boolean html = slot instanceof AttributeBinding attributeBinding && attributeBinding.htmlParagraphs();
            boolean writable = target.unreadable() == null;
            if (html) {
                XmlElement found = child(target, "html/body");
                XmlElement body = found != null ? found : target;
                String paragraphs = paragraphs(body);
                Span bodySpan = body.selfClosed() ? null : new Span(body.contentStart(), body.contentEnd());
                return new SlotRead(paragraphs, bodySpan, true, writable && !body.selfClosed())
                        .withNode(new XmlTextNode(target, body.selfClosed() ? null : new Span(body.contentStart(), body.contentEnd()), true));
            }
            if (target.selfClosed()) {
                return new SlotRead("", null, true, writable).withNode(new XmlTextNode(target, null, false));
            }
            List<XmlElement> elements = target.elements();
            Span span = new Span(target.contentStart(), !elements.isEmpty() ? elements.get(0).own().start() : target.contentEnd());
            StringBuilder value = new StringBuilder();
            for (Object item : target.content()) {
                if (!(item instanceof XmlTextRun run)) {
                    break;
                }
                value.append(run.text());
            }
            return new SlotRead(value.toString(), span, true, writable).withNode(new XmlTextNode(target, span, false)).withWire(value.toString());
        }
        return SlotRead.readOnlyAbsent("An xml entry has no " + slot + ".");
    }

    private static SlotRead readAttribute(XmlElement element, String name) {
        XmlAttribute attribute = element.attribute(name);
        if (attribute != null) {
            return new SlotRead(attribute.text(), attribute.value(), true, element.unreadable() == null).withNode(attribute).withWire(attribute.text());
        }
        return SlotRead.ABSENT.withNode(element);
    }

    /** An html body as plain text (FBL §4.5): one line per {@code p} element, whitespace inside a paragraph collapsed. */
    private static String paragraphs(XmlElement body) {
        List<String> lines = new ArrayList<>();
        List<XmlElement> descendants = new ArrayList<>();
        descendants(body, descendants);
        boolean any = false;
        for (XmlElement element : descendants) {
            if ("p".equals(element.name())) {
                any = true;
                lines.add(collapse(allText(element)));
            }
        }
        if (!any) {
            return collapse(allText(body));
        }
        return String.join("\n", lines);
    }

    private static void descendants(XmlElement element, List<XmlElement> into) {
        for (XmlElement child : element.elements()) {
            into.add(child);
            descendants(child, into);
        }
    }

    private static String collapse(String text) {
        // Every run of white space is one space by now, so a space is all there is to trim.
        String collapsed = WHITESPACE.matcher(text).replaceAll(" ");
        int start = 0;
        int end = collapsed.length();
        while (start < end && collapsed.charAt(start) == ' ') {
            start++;
        }
        while (end > start && collapsed.charAt(end - 1) == ' ') {
            end--;
        }
        return collapsed.substring(start, end);
    }

    @Override
    public SlotRead readRaw(Entry entry, String name) {
        return entry instanceof XmlElement element ? readAttribute(element, name) : SlotRead.ABSENT;
    }

    @Override
    public boolean headerHolds(HeaderSettings header) {
        if (header.line() == null) {
            return true;
        }
        return document != null && regex(header.line(), false).isMatch(text().text(document.startTag()));
    }

    // ---- writing (FBL §6) ----

    /** Text escaping (FBL §4.5): {@code &}, {@code <} and {@code >}. */
    public static String escapeText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Attribute escaping (FBL §4.5): the text escapes, and {@code "}, LF and CR. */
    public static String escapeAttribute(String value) {
        return escapeText(value).replace("\"", "&quot;").replace("\n", "&#xa;").replace("\r", "&#xd;");
    }

    /** Plain text as html paragraphs (FBL §4.5): one {@code <p>line</p>} per line. */
    public static String htmlParagraphs(String value) {
        if (value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String line : value.replace("\r\n", "\n").split("\n", -1)) {
            builder.append("<p>").append(escapeText(line)).append("</p>");
        }
        return builder.toString();
    }

    @Override
    public String format(SlotRead read, AttributeBinding binding, Object value) {
        String plain;
        if (binding == null) {
            plain = NewText.plain(value, null);
        } else {
            String wire = NewText.wire(binding, value, read.wire());
            plain = wire != null ? wire : NewText.plain(value, binding);
        }
        Object node = read.node() instanceof OverrideNode over ? over.node() : read.node();
        if (node instanceof XmlTextNode textNode) {
            return textNode.html() ? htmlParagraphs(plain) : escapeText(plain);
        }
        return escapeAttribute(plain);
    }

    @Override
    public void write(Plan plan, ReadElement element, List<SlotChange> changes) {
        XmlElement owner = (XmlElement) element.entry();
        for (SlotChange change : changes) {
            SlotRead read = change.read();
            AttributeBinding binding = change.binding();
            Object node = read.node() instanceof OverrideNode over ? over.node() : read.node();
            if (node instanceof XmlAttribute attribute && read.present()) {
                if (change.isEmpty() && "remove".equals(binding.empty())) {
                    plan.add(SpliceOperation.REMOVE_KEY, whitespaceBefore(attribute.own().start()), attribute.own().end(), "");
                } else {
                    String written = format(read, binding, change.value());
                    if (!written.equals(text().text(attribute.value()))) {
                        plan.add(SpliceOperation.REPLACE_VALUE, attribute.value(), written);
                    }
                }
                continue;
            }
            if (node instanceof XmlTextNode textNode && read.present()) {
                boolean removesChild = textNode.element() != owner && (binding.create() != null || read.node() instanceof OverrideNode);
                if (change.isEmpty() && "remove".equals(binding.empty()) && removesChild) {
                    removeChild(plan, (XmlElement) textNode.element().parent(), textNode.element());
                } else if (textNode.span() != null) {
                    String written = format(read, binding, change.value());
                    if (!written.equals(text().text(textNode.span()))) {
                        plan.add(SpliceOperation.REPLACE_VALUE, textNode.span(), written);
                    }
                } else {
                    open(plan, textNode.element(), format(read, binding, change.value()), SpliceOperation.REPLACE_VALUE, true);
                }
                continue;
            }
            String name = binding.xmlAttribute();
            if (name != null) {
                if (!(node instanceof XmlElement target)) {
                    Plan.refuse("The " + binding().name() + " file has no " + (binding.child() == null ? "" : binding.child()) + " to write \"" + name
                            + "\" in.");
                    return;
                }
                List<XmlAttribute> attributes = target.attributes();
                int offset = !attributes.isEmpty() ? attributes.get(attributes.size() - 1).own().end() : target.nameEnd();
                plan.add(SpliceOperation.INSERT_KEY, offset, offset, " " + name + "=\"" + format(read, binding, change.value()) + "\"");
                continue;
            }
            CreateChild create = binding.create();
            if (binding.text() && create != null) {
                String content = format(read.withNode(new XmlTextNode(owner, null, binding.htmlParagraphs())), binding, change.value());
                createChild(plan, owner, create, NewText.render(create.emit(), placeholder -> placeholder.equals("value") ? content : null));
                continue;
            }
            Plan.refuse("The " + binding().name() + " file has no " + (binding.child() != null ? binding.child() : "element") + " to write the "
                    + change.attribute() + " in.");
        }
    }

    /** The start of the whitespace directly before {@code offset}. */
    private int whitespaceBefore(int offset) {
        byte[] bytes = text().bytes();
        while (offset > 0 && isSpace(bytes[offset - 1])) {
            offset--;
        }
        return offset;
    }

    /** The bytes from the start of an element's line to its first byte. */
    private String indentOf(Entry entry) {
        return text().text(text().lines().get(text().lineIndexAt(entry.own().start())).start(), entry.own().start());
    }

    private String childIndent(XmlElement parent) {
        return indentOf(parent) + indentation(step());
    }

    /**
     * Opens a self-closed element to hold {@code content} (FBL §6.1 {@code self-close}): its
     * {@code />} becomes {@code >}, the content follows on its own line, and a new end tag closes
     * it on the line after, at the element's indentation.
     *
     * @param inline whether the content and the end tag stay on the element's line
     */
    private void open(Plan plan, XmlElement element, String content, SpliceOperation operation, boolean inline) {
        int at = element.close().end();
        plan.add(SpliceOperation.SELF_CLOSE, element.close(), ">");
        if (inline || element.lineSpan() == null) {
            plan.add(operation, at, at, content);
            plan.add(SpliceOperation.SELF_CLOSE, at, at, "</" + element.name() + ">");
            return;
        }
        String newline = newlineAt(element.close().start());
        plan.add(operation, at, at, newline + childIndent(element) + content);
        plan.add(SpliceOperation.SELF_CLOSE, at, at, newline + indentOf(element) + "</" + element.name() + ">");
    }

    /** Appends {@code content} as the element's last child, on its own line when the last child is on one (FBL §6.3). */
    private void append(Plan plan, XmlElement element, String content, SpliceOperation operation) {
        if (element.selfClosed()) {
            open(plan, element, content, operation, false);
            return;
        }
        List<XmlElement> elements = element.elements();
        if (!elements.isEmpty()) {
            XmlElement last = elements.get(elements.size() - 1);
            Span line = last.lineSpan();
            if (line != null) {
                TextLine lineOfLast = text().lines().get(text().lineIndexAt(last.own().end() - 1));
                String ending = !lineOfLast.ending().isEmpty() ? lineOfLast.ending() : newlineAt(last.own().end());
                plan.add(operation, line.end(), line.end(), indentOf(last) + content + ending);
            } else {
                plan.add(operation, last.own().end(), last.own().end(), content);
            }
            return;
        }
        Span end = element.endTag();
        if (onlyWhitespaceBefore(end.start()) && text().lineIndexAt(end.start()) != text().lineIndexAt(element.startTag().end())) {
            int lineStart = text().lines().get(text().lineIndexAt(end.start())).start();
            plan.add(operation, lineStart, lineStart, childIndent(element) + content + newlineAt(lineStart));
            return;
        }
        plan.add(operation, end.start(), end.start(), content);
    }

    /** A child element holding a value (FBL §5.2 {@code create}), placed first, last or before the first child of a name. */
    private void createChild(Plan plan, XmlElement element, CreateChild create, String content) {
        if (element.selfClosed()) {
            open(plan, element, content, SpliceOperation.INSERT_KEY, false);
            return;
        }
        XmlElement before = null;
        if ("first".equals(create.place())) {
            List<XmlElement> elements = element.elements();
            before = elements.isEmpty() ? null : elements.get(0);
        } else if ("before".equals(create.place())) {
            for (XmlElement candidate : element.elements()) {
                if (candidate.name().equals(create.before())) {
                    before = candidate;
                    break;
                }
            }
        }
        if (before == null) {
            append(plan, element, content, SpliceOperation.INSERT_KEY);
            return;
        }
        if (before.lineSpan() != null) {
            int offset = text().lines().get(text().lineIndexAt(before.own().start()) - 1).contentEnd();
            plan.add(SpliceOperation.INSERT_KEY, offset, offset, newlineAt(offset) + indentOf(before) + content);
            return;
        }
        plan.add(SpliceOperation.INSERT_KEY, before.own().start(), before.own().start(), content);
    }

    /**
     * Removes a child element holding a value with the line break before it ({@code remove-key}), and
     * closes the parent again when only whitespace is left in it ({@code self-close}).
     */
    private void removeChild(Plan plan, XmlElement parent, XmlElement child) {
        int line = text().lineIndexAt(child.own().start());
        Span span = child.lineSpan() != null && line > 0 ? new Span(text().lines().get(line - 1).contentEnd(), child.own().end()) : child.own();
        if (span.start() < parent.contentStart()) {
            span = new Span(parent.contentStart(), span.end());
        }
        Span end = parent.endTag();
        boolean closes = end != null
                && isWhitespace(new Span(parent.contentStart(), span.start()))
                && isWhitespace(new Span(span.end(), end.start()));
        if (!closes) {
            plan.add(SpliceOperation.REMOVE_KEY, span, "");
            return;
        }
        plan.add(SpliceOperation.SELF_CLOSE, parent.close().start(), span.start(), "/>");
        plan.add(SpliceOperation.REMOVE_KEY, span, "");
        plan.add(SpliceOperation.SELF_CLOSE, span.end(), end.end(), "");
    }

    @Override
    public void insert(Plan plan, InsertRequest request) {
        InsertSettings insert = request.rule().insert();
        XmlElement parent = request.parent() != null && request.parent().entry() instanceof XmlElement requested ? requested : null;
        if (parent == null && insert.container() != null && root != null) {
            String container = insert.container();
            List<Selector.Match> matches = Selector.match(Selector.start(container, root, null), container, this::attributeEquals);
            parent = !matches.isEmpty() && matches.get(0).entry() instanceof XmlElement found ? found : null;
        }
        if (parent == null) {
            parent = document;
        }
        if (parent == null) {
            Plan.refuse("The " + binding().name() + " file has no element to add the " + request.rule().type() + " to.");
            return;
        }
        String text;
        if (insert.emit() != null) {
            text = NewText.render(insert.emit(), name -> switch (name) {
                case "id" -> request.id() == null ? null : escapeAttribute(request.id());
                case "source" -> request.source() == null ? null : escapeAttribute(request.source().key());
                case "target" -> request.target() == null ? null : escapeAttribute(request.target().key());
                default -> {
                    Object v = request.values().get(name);
                    yield v != null ? escapeAttribute(NewText.plain(v, request.rule().attribute(name))) : null;
                }
            });
        } else {
            text = newElement(request);
        }
        List<XmlElement> elements = parent.elements();
        if ("start".equals(insert.place()) && !elements.isEmpty()) {
            XmlElement first = elements.get(0);
            if (first.lineSpan() != null) {
                int lineStart = text().lines().get(text().lineIndexAt(first.own().start())).start();
                plan.add(SpliceOperation.INSERT_ENTRY, lineStart, lineStart, indentOf(first) + text + newlineAt(lineStart));
            } else {
                plan.add(SpliceOperation.INSERT_ENTRY, first.own().start(), first.own().start(), text);
            }
            return;
        }
        append(plan, parent, text, SpliceOperation.INSERT_ENTRY);
    }

    /** An attribute of a new element: its name and its value before escaping. */
    private record Written(String name, String value) {
    }

    /** A new element without an {@code emit}: the rule's element name with its attributes in {@code insert.keys} order, then binding order (FBL §6.3). */
    private static String newElement(InsertRequest request) {
        Rule rule = request.rule();
        String last = null;
        for (String segment : rule.at().split("/")) {
            if (!segment.isEmpty()) {
                last = segment;
            }
        }
        if (last == null) {
            throw new IndexOutOfBoundsException("The selector '" + rule.at() + "' names no element.");
        }
        int bracket = last.indexOf('[');
        String name = bracket > 0 ? last.substring(0, bracket) : last;
        List<Written> written = new ArrayList<>();
        if (rule.id() != null && rule.id().from() != null && rule.id().from().xmlAttribute() != null && request.id() != null) {
            written.add(new Written(rule.id().from().xmlAttribute(), request.id()));
        }
        for (Map.Entry<String, AttributeBinding> pair : rule.attributes().entrySet()) {
            AttributeBinding binding = pair.getValue();
            Object value = request.values().get(pair.getKey());
            if (binding.xmlAttribute() != null && binding.child() == null && value != null) {
                written.add(new Written(binding.xmlAttribute(), NewText.plain(value, binding)));
            }
        }
        if (rule.source() != null && rule.source().xmlAttribute() != null && request.source() != null) {
            written.add(new Written(rule.source().xmlAttribute(), request.source().key()));
        }
        if (rule.target() != null && rule.target().xmlAttribute() != null && request.target() != null) {
            written.add(new Written(rule.target().xmlAttribute(), request.target().key()));
        }
        List<String> keys = rule.insert().keys();
        // The sort is stable: attributes no key names keep binding order, after those the keys name.
        written.sort(Comparator.comparingInt((Written w) -> keys.contains(w.name()) ? keys.indexOf(w.name()) : keys.size()));
        StringBuilder builder = new StringBuilder("<").append(name);
        for (Written w : written) {
            builder.append(' ').append(w.name()).append("=\"").append(escapeAttribute(w.value())).append('"');
        }
        return builder.append("/>").toString();
    }

    @Override
    public void remove(Plan plan, ReadElement element, Set<ReadElement> removed) {
        XmlElement entry = (XmlElement) element.entry();
        if (entry == document) {
            Plan.refuse("The document element of the " + binding().name() + " file cannot be removed.");
            return;
        }
        plan.add(SpliceOperation.REMOVE_ENTRY, entry.removalSpan(), "");
    }
}
