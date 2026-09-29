package etalii.adp.core.diagram;

import java.util.List;

import etalii.adp.core.diagram.edit.DiagramCommands;

/**
 * Told what changed after every re-read of the text: edits, undo, redo and external changes alike
 * (FR-004, research R8). The diagram is passed as the {@link DiagramCommands.Host} it implements.
 */
@FunctionalInterface
public interface DiagramListener {

    void changed(DiagramCommands.Host tool, List<DiagramChange> changes);
}