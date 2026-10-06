package etalii.adp.fbl.registration;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import etalii.adp.fbl.Finding;
import etalii.adp.fbl.FindingCodes;
import etalii.adp.fbl.FindingSeverity;
import etalii.adp.fbl.SourceLocation;
import etalii.adp.fbl.text.BodyText;
import etalii.adp.fbl.text.Span;
import etalii.adp.fbl.text.TextLine;

/**
 * A registration ({@code .adp}) in the line form of FBL §8.1: the origin on line 1, headers, the
 * {@code layout:} and {@code identities:} blocks, and whatever follows them kept as unbound content.
 * Every part keeps its span, so the registration is written by splices like any body (FBL §8.4).
 */
public final class RegistrationDocument {

    /** The headers FBL itself defines (FBL §8.1); a binding declares others. */
    public static final List<String> FBL_HEADERS = List.of("body", "view", "resource");

    private final BodyText text;
    private String origin = "";
    private List<RegistrationHeader> headers = List.of();
    private RegistrationBlock layout;
    private RegistrationBlock identities;
    private int afterHeaders;
    private Span unbound;

    RegistrationDocument(BodyText text) {
        this.text = text;
    }

    BodyText text() {
        return text;
    }

    /** The origin of the tool type (FBL §8.1, line 1). */
    public String origin() {
        return origin;
    }

    public List<RegistrationHeader> headers() {
        return headers;
    }

    /** The {@code layout:} block, or null when the registration has none. */
    public RegistrationBlock layout() {
        return layout;
    }

    /** The {@code identities:} block, or null when the registration has none. */
    public RegistrationBlock identities() {
        return identities;
    }

    /** Where a new block goes: the end of the header region's last line. */
    public int afterHeaders() {
        return afterHeaders;
    }

    /** Content after the blocks that FBL does not read, kept byte for byte. */
    public Span unbound() {
        return unbound;
    }

    /** The value of the first header of that key, or null. */
    public String header(String key) {
        return headers.stream().filter(h -> h.key().equals(key)).map(RegistrationHeader::value).findFirst().orElse(null);
    }

    /** The {@code body} header, or null. */
    public String body() {
        return header("body");
    }

    /** The {@code view} header, or null. */
    public String view() {
        return header("view");
    }

    /** The {@code resource} header, or null. */
    public String resource() {
        return header("resource");
    }

    /** The positions of the layout block by id; an entry whose value is not two numbers is left out. */
    public Map<String, RegistrationEntry.Position> positions() {
        Map<String, RegistrationEntry.Position> positions = new LinkedHashMap<>();
        for (RegistrationEntry entry : entries(layout)) {
            RegistrationEntry.Position position = entry.position();
            if (position != null) {
                positions.put(entry.key(), position);
            }
        }
        return Collections.unmodifiableMap(positions);
    }

    /** The identities block as natural key to id (FBL §8.6). */
    public Map<String, String> identityMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (RegistrationEntry entry : entries(identities)) {
            map.putIfAbsent(entry.key(), entry.value());
        }
        return Collections.unmodifiableMap(map);
    }

    /** The headers neither FBL nor the binding declares, reported as {@code fbl.unknown-header} (FBL §8.1). */
    public List<Finding> unknownHeaders(List<String> declared, String fileName) {
        List<Finding> findings = new ArrayList<>();
        for (RegistrationHeader header : headers) {
            if (FBL_HEADERS.contains(header.key()) || declared.contains(header.key())) {
                continue;
            }
            BodyText.Position position = text.position(header.line().start());
            findings.add(new Finding(FindingCodes.UNKNOWN_HEADER, FindingSeverity.INFO,
                    "The header '" + header.key() + "' is neither FBL's nor the binding's; it is kept as it is.",
                    new SourceLocation(fileName, position.line(), position.column(), text.codePoints(header.line().start(), header.line().end()))));
        }
        return findings;
    }

    /**
     * Layout entries for ids the model does not have (FBL §8.5): reported as {@code fbl.stale-view-data},
     * applied to nothing, and removed at the registration's next write.
     */
    public List<Finding> staleEntries(Set<String> ids, String fileName) {
        List<Finding> findings = new ArrayList<>();
        for (RegistrationEntry entry : entries(layout)) {
            if (ids.contains(entry.key())) {
                continue;
            }
            BodyText.Position position = text.position(entry.keySpan().start());
            findings.add(new Finding(FindingCodes.STALE_VIEW_DATA, FindingSeverity.INFO,
                    "The layout keeps a position for '" + entry.key() + "', which the file no longer has; it is removed at the next save of the registration.",
                    new SourceLocation(fileName, position.line(), position.column(), text.codePoints(entry.keySpan().start(), entry.keySpan().end()))));
        }
        return findings;
    }

    /** Reads a registration. The line form has no syntax error: anything not understood is unbound content. */
    public static RegistrationDocument read(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        BodyText text = new BodyText(bytes);
        RegistrationDocument document = new RegistrationDocument(text);
        List<TextLine> lines = text.lines();
        document.origin = trim(text.text(text.bomLength(), lines.get(0).contentEnd()));
        document.afterHeaders = lines.get(0).end();
        int index = 1;
        List<RegistrationHeader> headers = new ArrayList<>();
        for (; index < lines.size(); index++) {
            TextLine line = lines.get(index);
            String content = text.text(line.start(), line.contentEnd());
            if (trim(content).isEmpty()) {
                continue;
            }
            if (blockName(content) != null) {
                break;
            }
            int separator = content.indexOf(": ");
            if (separator <= 0 || isWhiteSpace(content.charAt(0))) {
                break;
            }
            headers.add(new RegistrationHeader(content.substring(0, separator), trim(content.substring(separator + 2)), new Span(line.start(), line.end())));
            document.afterHeaders = line.end();
        }
        document.headers = Collections.unmodifiableList(headers);
        while (index < lines.size()) {
            TextLine line = lines.get(index);
            String content = text.text(line.start(), line.contentEnd());
            if (trim(content).isEmpty()) {
                index++;
                continue;
            }
            String name = blockName(content);
            if (name == null || (name.equals("layout") && document.layout != null) || (name.equals("identities") && document.identities != null)) {
                break;
            }
            RegistrationBlock block = readBlock(text, name, index);
            // The block's entries are the lines after its name line, one each.
            index += 1 + block.entries().size();
            if (name.equals("layout")) {
                document.layout = block;
            } else {
                document.identities = block;
            }
        }
        int unboundStart = index < lines.size() ? lines.get(index).start() : text.length();
        document.unbound = new Span(unboundStart, text.length());
        return document;
    }

    private static List<RegistrationEntry> entries(RegistrationBlock block) {
        return block == null ? List.of() : block.entries();
    }

    private static String blockName(String content) {
        int end = content.length();
        while (end > 0 && (content.charAt(end - 1) == ' ' || content.charAt(end - 1) == '\t')) {
            end--;
        }
        return switch (content.substring(0, end)) {
            case "layout:" -> "layout";
            case "identities:" -> "identities";
            default -> null;
        };
    }

    /** Reads the block whose name line is the line at {@code index}. */
    private static RegistrationBlock readBlock(BodyText text, String name, int index) {
        List<TextLine> lines = text.lines();
        TextLine header = lines.get(index);
        List<RegistrationEntry> entries = new ArrayList<>();
        int end = header.end();
        index++;
        while (index < lines.size()) {
            TextLine line = lines.get(index);
            String content = text.text(line.start(), line.contentEnd());
            if (content.isEmpty() || !isWhiteSpace(content.charAt(0)) || trim(content).isEmpty()) {
                break;
            }
            int separator = content.lastIndexOf(": ");
            if (separator < 0) {
                break;
            }
            int keyStart = content.length() - trimStart(content).length();
            String key = content.substring(keyStart, separator);
            String valueText = content.substring(separator + 2);
            int keyOffset = line.start() + utf8Length(content.substring(0, keyStart));
            int valueOffset = line.start() + utf8Length(content.substring(0, separator + 2));
            Span keySpan = new Span(keyOffset, keyOffset + utf8Length(key));
            Span valueSpan = new Span(valueOffset, valueOffset + utf8Length(trimEnd(valueText)));
            entries.add(new RegistrationEntry(key, trim(valueText), keySpan, valueSpan, new Span(line.start(), line.end()), keyStart));
            end = line.end();
            index++;
        }
        return new RegistrationBlock(name, new Span(header.start(), header.end()), new Span(header.start(), end), entries);
    }

    private static int utf8Length(String value) {
        return value.getBytes(UTF_8).length;
    }

    private static String trim(String value) {
        return trimEnd(trimStart(value));
    }

    private static String trimStart(String value) {
        int start = 0;
        while (start < value.length() && isWhiteSpace(value.charAt(start))) {
            start++;
        }
        return value.substring(start);
    }

    private static String trimEnd(String value) {
        int end = value.length();
        while (end > 0 && isWhiteSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    /** White space as Unicode counts it: the separators and the white space controls. */
    private static boolean isWhiteSpace(char c) {
        return Character.isSpaceChar(c) || (c >= '\t' && c <= '\r') || c == 0x85;
    }
}
