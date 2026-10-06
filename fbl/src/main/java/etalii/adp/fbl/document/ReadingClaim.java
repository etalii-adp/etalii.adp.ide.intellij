package etalii.adp.fbl.document;

import java.util.List;

/** One origin a shared binding reads a file as: whether a bare file opens as it, and what suggests it. */
public record ReadingClaim(boolean bare, List<String> suggest) {

    public ReadingClaim {
        suggest = List.copyOf(suggest);
    }
}
