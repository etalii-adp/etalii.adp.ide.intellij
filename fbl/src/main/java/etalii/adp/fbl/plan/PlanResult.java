package etalii.adp.fbl.plan;

import etalii.adp.fbl.Edit;

/** What planning a change gives: an edit to apply, or the reason it cannot be made (FBL §6.4). */
public sealed interface PlanResult {

    record Planned(Edit edit) implements PlanResult {
    }

    record Refused(String reason) implements PlanResult {
    }
}
