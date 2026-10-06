package etalii.adp.fbl.history;

import java.util.List;

import etalii.adp.fbl.Edit;
import etalii.adp.fbl.Splice;

/**
 * One recorded edit: the edit, the splices that undo it, and the SHA-256 digests of the bytes
 * before and after it.
 *
 * @param snapshot the whole bytes before a snapshot edit, else null
 */
record HistoryEntry(Edit edit, List<Splice> inverse, byte[] beforeDigest, byte[] afterDigest, byte[] snapshot) {
}
