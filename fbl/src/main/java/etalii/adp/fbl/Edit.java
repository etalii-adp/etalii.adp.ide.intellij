package etalii.adp.fbl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * One edit: the splices one model change (one DISL transaction) was planned as, applied together,
 * recorded together and undone together (FBL §6.4). Offsets refer to the body before the edit.
 */
public record Edit(List<Splice> splices, boolean snapshot) {

    public static final Edit EMPTY = new Edit(List.of());

    public Edit {
        splices = List.copyOf(splices);
    }

    public Edit(List<Splice> splices) {
        this(splices, false);
    }

    /** Applies the splices to {@code bytes}, in order, offsets referring to the bytes before. */
    public static byte[] apply(byte[] bytes, List<Splice> splices) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(bytes.length + 64);
        int position = 0;
        for (Splice splice : splices) {
            if (splice.start() < position || splice.end() < splice.start() || splice.end() > bytes.length) {
                throw new IllegalStateException("The splices of an edit overlap or leave the body: " + splice + " after offset " + position + ".");
            }
            output.write(bytes, position, splice.start() - position);
            byte[] text = splice.text().getBytes(UTF_8);
            output.write(text, 0, text.length);
            position = splice.end();
        }
        output.write(bytes, position, bytes.length - position);
        return output.toByteArray();
    }

    /**
     * The splices that undo {@code splices} once applied to {@code before}: each puts its replaced
     * bytes back, at its offset in the body after the edit, under its own operation (FBL §15.3).
     */
    public static List<Splice> inverse(byte[] before, List<Splice> splices) {
        List<Splice> inverse = new ArrayList<>(splices.size());
        int shift = 0;
        for (Splice splice : splices) {
            int start = splice.start() + shift;
            int written = splice.text().getBytes(UTF_8).length;
            String replaced = new String(before, splice.start(), splice.end() - splice.start(), UTF_8);
            inverse.add(new Splice(splice.operation(), start, start + written, replaced));
            shift += written - (splice.end() - splice.start());
        }
        return inverse;
    }
}
