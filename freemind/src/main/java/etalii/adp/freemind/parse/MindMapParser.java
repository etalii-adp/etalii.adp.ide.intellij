package etalii.adp.freemind.parse;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import etalii.adp.core.Rgb;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import etalii.adp.core.FormatProblem;
import etalii.adp.freemind.edit.FreeMindConventions;
import etalii.adp.freemind.model.ArrowLink;
import etalii.adp.freemind.model.AttributeRange;
import etalii.adp.freemind.model.FontSpec;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.model.NodeRanges;
import etalii.adp.freemind.model.Range;
import etalii.adp.freemind.model.Side;
import etalii.adp.freemind.parse.XmlScanner.Attribute;
import etalii.adp.freemind.parse.XmlScanner.Token;

/**
 * Reads a FreeMind map in two passes (research R4). A SAX pass with secure processing, no DOCTYPE
 * and no external entities gives the authoritative well-formedness verdict at this trust boundary.
 * Then {@link XmlScanner} supplies exact ranges, from which the immutable {@link MindMap} is built.
 * Only what FreeMind 1.0.1 displays becomes a field; everything else stays in the text.
 */
public final class MindMapParser {

    private static final String DOCTYPE_REFUSED = "A DOCTYPE declaration is not allowed in a mind map, because it can make "
            + "the file read other files or the network.";

    private final String text;
    private final Map<NodeKey, MapNode> nodesByKey = new LinkedHashMap<>();
    private final Set<String> ids = new HashSet<>();
    private final List<ArrowLink> arrowLinks = new ArrayList<>();
    private final Deque<Frame> stack = new ArrayDeque<>();
    private Token mapTag;
    private NodeBuilder rootBuilder;
    private MapNode root;

    private MindMapParser(String text) {
        this.text = text;
    }

    public static MindMap parse(String text) throws FormatProblem {
        validate(text);
        return new MindMapParser(text).build();
    }

    private static void validate(String text) throws FormatProblem {
        int bom = text.startsWith("\ufeff") ? 1 : 0;
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.newSAXParser().parse(new InputSource(new StringReader(text.substring(bom))), new DefaultHandler());
        } catch (SAXParseException e) {
            if (e.getMessage() != null && e.getMessage().contains("DOCTYPE")) {
                throw new FormatProblem(DOCTYPE_REFUSED, Math.max(0, text.indexOf("<!DOCTYPE")));
            }
            throw new FormatProblem(e.getMessage(), bom + offsetOf(text.substring(bom), e.getLineNumber(), e.getColumnNumber()));
        } catch (SAXException | IOException e) {
            throw new FormatProblem(String.valueOf(e.getMessage()), 0);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("The JDK's SAX parser must support secure processing", e);
        }
    }

    /** SAX lines and columns are one-based and count CRLF, CR and LF each as one line break. */
    private static int offsetOf(String text, int line, int column) {
        if (line < 1) {
            return 0;
        }
        int offset = 0;
        for (int current = 1; current < line && offset < text.length(); offset++) {
            char c = text.charAt(offset);
            if (c == '\n' || (c == '\r' && (offset + 1 >= text.length() || text.charAt(offset + 1) != '\n'))) {
                current++;
            }
        }
        return Math.min(text.length(), offset + Math.max(0, column - 1));
    }

    private MindMap build() throws FormatProblem {
        for (Token token : XmlScanner.scan(text)) {
            switch (token.kind()) {
            case DOCTYPE -> throw new FormatProblem(DOCTYPE_REFUSED, token.offset());
            case START_TAG -> startTag(token);
            case END_TAG -> endTag(token);
            default -> {
            }
            }
        }
        if (mapTag == null) {
            throw new FormatProblem("The file holds no XML element, so it is not a FreeMind mind map.", 0);
        }
        return new MindMap(mapTag.value(text, "version"), root, nodesByKey, arrowLinks, FreeMindConventions.detectIndentUnit(text),
                FreeMindConventions.detectLineSeparator(text), text);
    }

    private void startTag(Token tag) throws FormatProblem {
        if (mapTag == null) {
            if (!tag.name().equals("map")) {
                throw new FormatProblem("The root element is <" + tag.name() + ">, but a FreeMind mind map has <map>.", tag.offset());
            }
            mapTag = tag;
            if (tag.selfClosing()) {
                throw noRootNode();
            }
            stack.push(new Frame(FrameKind.MAP, tag, null));
            return;
        }
        Frame parent = stack.peek();
        if (parent == null) {
            return;
        }
        if (parent.kind == FrameKind.MAP) {
            if (tag.name().equals("node")) {
                if (rootBuilder != null) {
                    throw new FormatProblem("The map has more than one top-level node, but a FreeMind mind map has exactly one root node.",
                            tag.offset());
                }
                rootBuilder = new NodeBuilder(tag, null, "0");
                open(tag, rootBuilder);
            } else {
                ignore(tag);
            }
            return;
        }
        if (parent.kind != FrameKind.NODE) {
            ignore(tag);
            return;
        }
        NodeBuilder owner = parent.builder;
        switch (tag.name()) {
        case "node" -> open(tag, new NodeBuilder(tag, owner, owner.path + "/" + owner.childCount++));
        case "icon" -> {
            String builtin = tag.value(text, "BUILTIN");
            if (builtin != null) {
                owner.icons.add(builtin);
            }
            ignore(tag);
        }
        case "font" -> {
            owner.font = new FontSpec(tag.value(text, "NAME"), integer(tag.value(text, "SIZE")), "true".equals(tag.value(text, "BOLD")),
                    "true".equals(tag.value(text, "ITALIC")));
            ignore(tag);
        }
        case "arrowlink" -> {
            if (tag.selfClosing()) {
                addArrowLink(owner, tag, tag.end());
            } else {
                stack.push(new Frame(FrameKind.ARROW, tag, owner));
            }
        }
        case "richcontent" -> {
            if (!tag.selfClosing()) {
                stack.push(new Frame(FrameKind.RICH, tag, owner));
            }
        }
        default -> ignore(tag);
        }
    }

    private void endTag(Token tag) throws FormatProblem {
        Frame frame = stack.pop();
        switch (frame.kind) {
        case MAP -> {
            if (rootBuilder == null) {
                throw noRootNode();
            }
        }
        case NODE -> finish(frame.builder, tag);
        case ARROW -> addArrowLink(frame.builder, frame.start, tag.end());
        case RICH -> {
            String type = frame.start.value(text, "TYPE");
            String content = RichText.toText(text.substring(frame.start.end(), tag.offset()));
            if ("NODE".equals(type)) {
                frame.builder.richText = content;
                frame.builder.richNode = widen(frame.start.offset(), tag.end()).range;
            } else if ("NOTE".equals(type)) {
                frame.builder.note = content;
            }
        }
        case OTHER -> {
        }
        }
    }

    private FormatProblem noRootNode() {
        return new FormatProblem("The map has no node, but a FreeMind mind map has exactly one root node.", mapTag.offset());
    }

    private void open(Token tag, NodeBuilder builder) {
        if (tag.selfClosing()) {
            finish(builder, null);
        } else {
            stack.push(new Frame(FrameKind.NODE, tag, builder));
        }
    }

    private void ignore(Token tag) {
        if (!tag.selfClosing()) {
            stack.push(new Frame(FrameKind.OTHER, tag, null));
        }
    }

    private void addArrowLink(NodeBuilder owner, Token tag, int end) {
        arrowLinks.add(new ArrowLink(owner.key, tag.value(text, "DESTINATION"), widen(tag.offset(), end).range,
                tag.value(text, "STARTARROW"), tag.value(text, "ENDARROW")));
    }

    private void finish(NodeBuilder b, Token endTag) {
        Token start = b.start;
        int elementEnd = endTag == null ? start.end() : endTag.end();
        Widened element = widen(start.offset(), elementEnd);

        Map<String, AttributeRange> attributes = new LinkedHashMap<>();
        for (Attribute a : start.attributes()) {
            attributes.put(a.name(), new AttributeRange(a.start(), a.end(), a.value()));
        }
        int attributeInsertPoint = start.attributes().isEmpty() ? start.offset() + 1 + start.name().length()
                : start.attributes().get(start.attributes().size() - 1).end();

        int childInsertPoint;
        boolean childInsertOwnLine = false;
        if (endTag == null) {
            childInsertPoint = start.end() - 2;
        } else {
            int lineStart = endTag.offset();
            while (lineStart > 0 && isBlank(text.charAt(lineStart - 1))) {
                lineStart--;
            }
            childInsertOwnLine = lineStart > start.end() && isBreak(text.charAt(lineStart - 1));
            childInsertPoint = childInsertOwnLine ? lineStart : endTag.offset();
        }

        NodeRanges ranges = new NodeRanges(element.range, start.range(), attributes, attributeInsertPoint, endTag == null,
                childInsertPoint, childInsertOwnLine, b.richNode, element.indent, element.ownLine);

        String plain = start.value(text, "TEXT");
        MapNode node = new MapNode(b.key, b.id, plain != null ? plain : b.richText != null ? b.richText : "", b.richText != null,
                longValue(start.value(text, "CREATED")), longValue(start.value(text, "MODIFIED")), Side.of(start.value(text, "POSITION")),
                "true".equals(start.value(text, "FOLDED")), b.icons, color(start.value(text, "COLOR")),
                color(start.value(text, "BACKGROUND_COLOR")), b.font, start.value(text, "LINK"), b.note, intValue(start.value(text, "HGAP")),
                intValue(start.value(text, "VGAP")), intValue(start.value(text, "VSHIFT")), b.children, ranges);

        nodesByKey.put(node.key(), node);
        if (b.parent == null) {
            root = node;
        } else {
            b.parent.children.add(node);
        }
    }

    private record Widened(Range range, boolean ownLine, String indent) {
    }

    /**
     * Widens {@code [start, end)} to whole lines when nothing but whitespace shares its lines, taking
     * the leading whitespace and one trailing line separator.
     */
    private Widened widen(int start, int end) {
        int lineStart = start;
        while (lineStart > 0 && isBlank(text.charAt(lineStart - 1))) {
            lineStart--;
        }
        boolean beginsLine = lineStart == 0 || isBreak(text.charAt(lineStart - 1));
        int lineEnd = end;
        while (lineEnd < text.length() && isBlank(text.charAt(lineEnd))) {
            lineEnd++;
        }
        boolean endsLine = lineEnd == text.length() || isBreak(text.charAt(lineEnd));
        String indent = beginsLine ? text.substring(lineStart, start) : "";
        if (!beginsLine || !endsLine) {
            return new Widened(new Range(start, end - start), false, indent);
        }
        if (lineEnd < text.length()) {
            lineEnd += text.startsWith("\r\n", lineEnd) ? 2 : 1;
        }
        return new Widened(new Range(lineStart, lineEnd - lineStart), true, indent);
    }

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\t';
    }

    private static boolean isBreak(char c) {
        return c == '\n' || c == '\r';
    }

    private static Long longValue(String value) {
        try {
            return value == null ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integer(String value) {
        try {
            return value == null ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int intValue(String value) {
        Integer parsed = integer(value);
        return parsed == null ? 0 : parsed;
    }

    private static Rgb color(String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) {
            return null;
        }
        int rgb = Integer.parseInt(value.substring(1), 16);
        return new Rgb(rgb >> 16 & 0xff, rgb >> 8 & 0xff, rgb & 0xff);
    }

    private enum FrameKind {
        MAP, NODE, RICH, ARROW, OTHER
    }

    /** An open element; {@code builder} is the node it belongs to, where that matters. */
    private record Frame(FrameKind kind, Token start, NodeBuilder builder) {
    }

    private final class NodeBuilder {
        final Token start;
        final NodeBuilder parent;
        final String path;
        final String id;
        final NodeKey key;
        final List<String> icons = new ArrayList<>();
        final List<MapNode> children = new ArrayList<>();
        int childCount;
        FontSpec font;
        String richText;
        Range richNode;
        String note;

        NodeBuilder(Token start, NodeBuilder parent, String path) {
            this.start = start;
            this.parent = parent;
            this.path = path;
            this.id = start.value(text, "ID");
            this.key = id != null && ids.add(id) ? NodeKey.ofId(id) : NodeKey.ofPath(path);
        }
    }
}
