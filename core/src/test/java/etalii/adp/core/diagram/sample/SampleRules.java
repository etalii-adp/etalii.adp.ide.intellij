package etalii.adp.core.diagram.sample;

import java.util.Set;

import etalii.adp.core.diagram.DiagramRules;
import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;

/** The sample diagram's one rule: a diagram keeps at least one task. */
public final class SampleRules implements DiagramRules {

    @Override
    public Verdict canRemove(Diagram d, Set<Object> keys) {
        boolean removesTask = false;
        boolean keepsTask = false;
        for (Element element : d.elements().values()) {
            if (element.type().equals("task")) {
                removesTask |= keys.contains(element.key());
                keepsTask |= !keys.contains(element.key());
            }
        }
        return removesTask && !keepsTask ? Verdict.refuse("a diagram needs at least one task") : Verdict.allow();
    }
}