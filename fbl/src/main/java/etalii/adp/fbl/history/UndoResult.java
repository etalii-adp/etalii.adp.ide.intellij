package etalii.adp.fbl.history;

import java.util.List;

import etalii.adp.fbl.Splice;

/** What an undo or a redo did: the splices it applied, or why it was refused. */
public sealed interface UndoResult {

    record Done(List<Splice> splices) implements UndoResult {

        public Done {
            splices = List.copyOf(splices);
        }
    }

    record Refused(String reason) implements UndoResult {
    }
}
