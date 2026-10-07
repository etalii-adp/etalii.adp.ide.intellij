package etalii.adp.fbl.family.lines;

import etalii.adp.fbl.rule.Entry;
import etalii.adp.fbl.text.Span;

/**
 * A statement of the {@code lines} or {@code blocks} family (FBL §4.6, §4.7): one line, or, when it
 * opens a block, the lines up to and including the one holding the matching <code>}</code>.
 */
final class Statement extends Entry {

    private final int firstLine;
    private int lastLine;
    private final String content;
    private final int[] offsets;
    private boolean opens;
    private boolean isHeader;
    private Span firstLineSpan;

    /**
     * @param content the statement's first line from its first non-whitespace byte
     * @param offsets the byte offset of every UTF-16 index of {@code content}, and one past its end
     * @param own the span of the first line's content, without trailing whitespace
     * @param indent the byte column of the statement's first byte on its line
     */
    Statement(int firstLine, String content, int[] offsets, Span own, int indent) {
        super(own, null, indent);
        this.firstLine = firstLine;
        this.lastLine = firstLine;
        this.content = content;
        this.offsets = offsets;
        this.firstLineSpan = own;
    }

    int firstLine() {
        return firstLine;
    }

    int lastLine() {
        return lastLine;
    }

    void setLastLine(int value) {
        lastLine = value;
    }

    /** The statement's first line from its first non-whitespace byte, as the rules' {@code line} expressions see it. */
    String content() {
        return content;
    }

    /** The byte offset of every UTF-16 index of {@link #content()}, and one past its end. */
    int[] offsets() {
        return offsets;
    }

    boolean opens() {
        return opens;
    }

    void setOpens(boolean value) {
        opens = value;
    }

    boolean isHeader() {
        return isHeader;
    }

    void setHeader(boolean value) {
        isHeader = value;
    }

    /** The span of the first line's content, without trailing whitespace: what {@code re-emit-line} replaces. */
    Span firstLineSpan() {
        return firstLineSpan;
    }

    void setFirstLineSpan(Span value) {
        firstLineSpan = value;
    }
}
