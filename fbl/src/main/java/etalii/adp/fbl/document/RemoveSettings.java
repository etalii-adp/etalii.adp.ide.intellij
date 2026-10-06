package etalii.adp.fbl.document;

import java.util.List;

/** What removing a rule's entry takes with it (FBL §6.2). */
public record RemoveSettings(List<String> cascade, boolean removeContainerWhenEmpty) {

    public RemoveSettings {
        cascade = List.copyOf(cascade);
    }
}
