package etalii.adp.fbl.text;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Bytes, lines and positions (FBL §2.6). The counterparts of standalone's {@code Bytes/BodyText.Tests.cs}. */
class BodyTextTest {

    @Test
    void aColumnCountsCodePointsNotBytesOrUtf16Units() {
        // An emoji is four bytes and two UTF-16 units, but one code point.
        BodyText text = new BodyText("a😀b: x\n".getBytes(UTF_8));

        BodyText.Position position = text.position("a😀b".getBytes(UTF_8).length);

        assertEquals(new BodyText.Position(1, 4), position);
    }

    @Test
    void theByteOrderMarkBelongsToNoColumn() {
        BodyText text = new BodyText(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a', 'b'});

        assertEquals(3, text.bomLength());
        assertEquals(new BodyText.Position(1, 2), text.position(4));
    }

    @Test
    void crlfLfAndALoneCrEachEndALine() {
        BodyText text = new BodyText("a\r\nb\nc\rd".getBytes(UTF_8));

        assertEquals(List.of("\r\n", "\n", "\r", ""), text.lines().stream().map(TextLine::ending).toList());
        assertEquals(new BodyText.Position(4, 1), text.position(7));
    }

    @Test
    void aBodyOfOnlyALoneCrIsOneEmptyLineEndedByCr() {
        BodyText text = new BodyText("\r".getBytes(UTF_8));

        assertEquals(List.of(new TextLine(0, 0, 1, "\r")), text.lines());
        assertEquals("\r", text.dominantEnding());
    }

    @ParameterizedTest
    @MethodSource("dominantEndings")
    void crlfWinsATieAndALoneCrCountsAsNeither(String body, String dominant) {
        assertEquals(dominant, new BodyText(body.getBytes(UTF_8)).dominantEnding());
    }

    static Stream<Arguments> dominantEndings() {
        return Stream.of(
                Arguments.of("a\r\nb\nc", "\r\n"),
                Arguments.of("a\r\nb\nc\n", "\n"),
                Arguments.of("a\rb\rc\r\n", "\r\n"),
                Arguments.of("abc", null));
    }

    @Test
    void anInvalidUtf8SequenceIsFoundWithItsOffset() {
        // A lone continuation byte after two valid bytes.
        BodyText text = new BodyText(new byte[] {'a', 'b', (byte) 0x80, 'c'});

        assertFalse(text.isValidUtf8());
        assertEquals(2, text.invalidOffset());
    }
}
