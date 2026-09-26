package etalii.adp.core.diagram;

import java.util.List;
import java.util.Set;

import etalii.adp.core.FormatProblem;
import etalii.adp.core.TextChanges;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.End;

/**
 * Between one file format and the diagram (FR-027 to FR-029, research R5): the designer's own code.
 * Each edit method returns range-exact changes against {@code text}, the text {@code diagram} was
 * read from, or an empty {@link TextChanges} when there is nothing to do. Changes must not
 * overlap. The framework runs them as one command, re-reads the text and diffs.
 */
public interface DiagramMapping {

    /** Read the whole text. Unknown types are returned with their own type id. Must not modify anything. */
    Diagram read(CharSequence text) throws FormatProblem;

    TextChanges add(CharSequence text, Diagram diagram, AddRequest request);

    /** @param keys elements and connections; the connections of removed elements are already included */
    TextChanges remove(CharSequence text, Diagram diagram, Set<Object> keys);

    /** Moves and resizes, sector changes included. */
    TextChanges setBounds(CharSequence text, Diagram diagram, List<BoundsChange> changes);

    TextChanges connect(CharSequence text, Diagram diagram, String connectionType, End source, End target);

    TextChanges reconnect(CharSequence text, Diagram diagram, Object connection, EndSide side, End end);

    /** An empty {@code value} means "not set". */
    TextChanges setProperty(CharSequence text, Diagram diagram, Set<Object> keys, String property, String value);

    /** Only for designers with a {@link DiagramLayout}. By default nothing is supported. */
    default TextChanges drop(CharSequence text, Diagram diagram, Set<Object> keys, Object target, Placement placement) {
        return TextChanges.of();
    }
}