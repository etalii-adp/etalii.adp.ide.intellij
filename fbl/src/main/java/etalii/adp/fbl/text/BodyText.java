package etalii.adp.fbl.text;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A body's bytes with FBL's view of them (FBL §2.6): UTF-8 with or without a byte-order mark, lines
 * and their endings (CRLF, LF or a lone CR), and conversion from a byte offset to a 1-based line and a
 * code-point column. Every offset is a UTF-8 byte offset into the whole file, the mark included.
 */
public final class BodyText {

    private final byte[] bytes;
    private final int bomLength;
    private final boolean validUtf8;
    private final int invalidOffset;
    private final List<TextLine> lines = new ArrayList<>();

    public BodyText(byte[] bytes) {
        this.bytes = bytes;
        bomLength = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        int invalid = firstInvalidOffset(bytes, bomLength);
        validUtf8 = invalid < 0;
        invalidOffset = Math.max(invalid, 0);
        buildLines();
    }

    public byte[] bytes() {
        return bytes;
    }

    public int length() {
        return bytes.length;
    }

    /** 3 when the body starts with a UTF-8 byte-order mark, else 0. The mark belongs to no node. */
    public int bomLength() {
        return bomLength;
    }

    public boolean isValidUtf8() {
        return validUtf8;
    }

    /** The byte offset of the first invalid UTF-8 sequence. Only meaningful when {@link #isValidUtf8()} is false. */
    public int invalidOffset() {
        return invalidOffset;
    }

    public List<TextLine> lines() {
        return Collections.unmodifiableList(lines);
    }

    /** The bytes as text. Only meaningful for valid UTF-8. */
    public String text(int start, int end) {
        return new String(bytes, start, end - start, UTF_8);
    }

    public String text(Span span) {
        return text(span.start(), span.end());
    }

    /**
     * The ending that ends the most lines, CRLF winning a tie with LF; a lone CR counts as neither
     * (FBL §6.3). Null when no line has an ending.
     */
    public String dominantEnding() {
        int crlf = 0;
        int lf = 0;
        int cr = 0;
        for (TextLine line : lines) {
            switch (line.ending()) {
                case "\r\n" -> crlf++;
                case "\n" -> lf++;
                case "\r" -> cr++;
                default -> {
                }
            }
        }
        if (crlf == 0 && lf == 0) {
            return cr > 0 ? "\r" : null;
        }
        return crlf >= lf ? "\r\n" : "\n";
    }

    /** The line ending a splice at {@code offset} writes (FBL §6.3). */
    public String newlineAt(int offset, String fallback) {
        TextLine line = lines.get(lineIndexAt(offset));
        if (!line.ending().isEmpty()) {
            return line.ending();
        }
        String dominant = dominantEnding();
        return dominant != null ? dominant : fallback;
    }

    /** The 0-based index of the line holding {@code offset}. */
    public int lineIndexAt(int offset) {
        int lo = 0;
        int hi = lines.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (lines.get(mid).start() <= offset) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /** The 1-based line and code-point column of {@code offset}. */
    public Position position(int offset) {
        int index = lineIndexAt(offset);
        int start = lines.get(index).start();
        if (index == 0) {
            start = Math.max(start, bomLength);
        }
        return new Position(index + 1, 1 + codePoints(start, offset));
    }

    /** The number of code points in a span, for a finding's length. */
    public int codePoints(int start, int end) {
        int count = 0;
        for (int i = start; i < end && i < bytes.length; i++) {
            if ((bytes[i] & 0xC0) != 0x80) {
                count++;
            }
        }
        return count;
    }

    /** A 1-based line and a 1-based column counted in code points. */
    public record Position(int line, int column) {
    }

    private static int firstInvalidOffset(byte[] bytes, int from) {
        CharsetDecoder decoder = UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer in = ByteBuffer.wrap(bytes, from, bytes.length - from);
        CharBuffer out = CharBuffer.allocate(4096);
        while (true) {
            CoderResult result = decoder.decode(in, out, true);
            if (result.isError()) {
                return in.position();
            }
            if (result.isUnderflow()) {
                return -1;
            }
            out.clear();
        }
    }

    private void buildLines() {
        int start = 0;
        int i = 0;
        while (i < bytes.length) {
            byte b = bytes[i];
            if (b == '\r') {
                if (i + 1 < bytes.length && bytes[i + 1] == '\n') {
                    lines.add(new TextLine(start, i, i + 2, "\r\n"));
                    i += 2;
                } else {
                    lines.add(new TextLine(start, i, i + 1, "\r"));
                    i += 1;
                }
                start = i;
            } else if (b == '\n') {
                lines.add(new TextLine(start, i, i + 1, "\n"));
                i += 1;
                start = i;
            } else {
                i++;
            }
        }
        if (start < bytes.length || lines.isEmpty()) {
            lines.add(new TextLine(start, bytes.length, bytes.length, ""));
        }
    }
}
