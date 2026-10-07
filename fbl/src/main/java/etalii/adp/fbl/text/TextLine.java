package etalii.adp.fbl.text;

/** One line: {@code start} to {@code contentEnd} is the line, up to {@code end} its ending. */
public record TextLine(int start, int contentEnd, int end, String ending) {
}
